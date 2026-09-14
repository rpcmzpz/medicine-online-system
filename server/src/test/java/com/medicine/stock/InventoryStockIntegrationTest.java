package com.medicine.stock;

import com.medicine.entity.Inventory;
import com.medicine.mapper.InventoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 库存并发正确性集成测试：需要本机可用的 MySQL(medicine_online)。
 *
 * <p>默认不随 {@code mvn test} 运行（打了 integration tag，被 surefire 排除），
 * 需要时执行：{@code mvn test -DexcludedGroups=}。
 *
 * <p>这里验证的是简历里那句「MySQL 乐观锁 version 兜底防超卖」到底成不成立：
 * 先用过期版本号直接证明 CAS 会失败，再用真并发抢库存证明不会超卖。
 */
@SpringBootTest
@Tag("integration")
class InventoryStockIntegrationTest {

    private static final Long MEDICINE_ID = 1L;

    @Autowired
    private InventoryStockManager stockManager;
    @Autowired
    private InventoryMapper inventoryMapper;

    private Inventory current() {
        Inventory inventory = inventoryMapper.selectVersion(MEDICINE_ID);
        assertNotNull(inventory, "药品 " + MEDICINE_ID + " 的库存记录应存在");
        return inventory;
    }

    @Test
    @Transactional
    @DisplayName("乐观锁的直接证据：同一个旧 version 第二次 CAS 必然失败")
    void staleVersionCasMustFail() {
        int version = current().getVersion();

        assertTrue(inventoryMapper.lockStock(MEDICINE_ID, 1, version) > 0,
                "用最新 version 做 CAS 应成功");
        // 旧 version 再来一次 —— 这正是并发场景下第二个请求会长到的样子
        assertEquals(0, inventoryMapper.lockStock(MEDICINE_ID, 1, version),
                "旧 version 的 CAS 必须返回 0，否则乐观锁形同虚设");

        System.out.println("[it] 乐观锁验证：version=" + version
                + " 首次 CAS 影响行数=1，旧 version 再次 CAS 影响行数=0");

        // 当前 version 已自增，CAS 条件随之变化
        assertEquals(version + 1, current().getVersion(), "每成功一次 version 应 +1");
    }

    @Test
    @DisplayName("并发抢库存不超卖：40 线程各锁 20 件，成功次数必须恰好等于可售整除单量")
    void concurrentLockShouldNotOversell() throws Exception {
        Inventory snapshot = current();
        final int available = snapshot.getStockQuantity() - snapshot.getLockedQuantity();
        final int perOrder = 20;
        final int threads = 40;
        final int expectedSuccess = available / perOrder;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        if (stockManager.lock(MEDICINE_ID, perOrder)) {
                            success.incrementAndGet();
                        } else {
                            insufficient.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    } finally {
                        finishGate.countDown();
                    }
                });
            }
            startGate.countDown();   // 同时放行，制造真实争抢
            assertTrue(finishGate.await(60, TimeUnit.SECONDS), "并发任务应在 60s 内跑完");
        } finally {
            pool.shutdownNow();
        }

        Inventory after = current();
        int lockedDelta = after.getLockedQuantity() - snapshot.getLockedQuantity();
        int availableAfter = after.getStockQuantity() - after.getLockedQuantity();

        System.out.println("[it] 并发抢库存：线程=" + threads + " 单量=" + perOrder
                + " 可售=" + available + " → 成功=" + success.get()
                + " 判定不足=" + insufficient.get() + " 异常=" + errors.size());
        System.out.println("[it] 锁定增量=" + lockedDelta + "，剩余可售=" + availableAfter);

        assertEquals(0, errors.size(), "CAS 冲突应在内部重试掉，不该有异常冒到调用方: " + errors);
        assertEquals(expectedSuccess, success.get(), "成功次数应为 可售/单量 的整除结果");
        assertEquals(expectedSuccess * perOrder, lockedDelta, "锁定增量必须等于成功单量之和");
        assertTrue(availableAfter >= 0, "可售量绝不允许为负——为负即超卖");
        assertTrue(after.getLockedQuantity() <= after.getStockQuantity(), "锁定数不得超过总库存");

        // 还原现场，避免影响其它用例与 Redis 预热基线
        stockManager.unlock(MEDICINE_ID, lockedDelta);
        assertEquals(snapshot.getLockedQuantity(), current().getLockedQuantity(),
                "测试结束后锁定数应恢复到测试前");
    }
}
