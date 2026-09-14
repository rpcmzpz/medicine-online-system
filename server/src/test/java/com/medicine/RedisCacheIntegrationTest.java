package com.medicine;

import com.medicine.cache.MedicineCache;
import com.medicine.cache.StockCache;
import com.medicine.common.BusinessException;
import com.medicine.service.MedicineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存行为集成测试：需要本机可用的 MySQL(medicine_online) 与 Redis(6379)。
 *
 * <p>默认不随 {@code mvn test} 运行（打了 integration tag，被 surefire 排除），
 * 需要时执行：{@code mvn test -DexcludedGroups=}。
 *
 * <p>MyBatis 配置了 StdOutImpl，会把每条 SQL 打到标准输出，
 * 所以这里直接统计「Preparing:」的出现次数来证明缓存到底有没有挡住数据库。
 */
@SpringBootTest
@Tag("integration")
class RedisCacheIntegrationTest {

    private static final Long MEDICINE_ID = 1L;
    private static final Long GHOST_ID = 987654321L;

    @Autowired
    private MedicineService medicineService;
    @Autowired
    private MedicineCache medicineCache;
    @Autowired
    private StockCache stockCache;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 执行动作并统计其中 MyBatis 打印的 SQL 条数 */
    private int sqlCount(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        String log = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        int count = 0;
        int index = log.indexOf("Preparing:");
        while (index >= 0) {
            count++;
            index = log.indexOf("Preparing:", index + 1);
        }
        return count;
    }

    @Test
    @DisplayName("详情：首次回源 3 条 SQL，第二次命中缓存只剩实时库存 1 条")
    void detail_secondCallShouldHitCache() {
        String key = "med:detail:" + MEDICINE_ID;
        stringRedisTemplate.delete(key);

        int first = sqlCount(() -> medicineService.detail(MEDICINE_ID));
        assertNotNull(stringRedisTemplate.opsForValue().get(key), "首次回源后应写入缓存");

        int second = sqlCount(() -> medicineService.detail(MEDICINE_ID));

        System.out.println("[it] 首次 SQL 条数 = " + first + "，第二次 SQL 条数 = " + second);
        assertTrue(first >= 3, "首次应回源：详情 JOIN + 评价 + 平均分，实际 " + first);
        assertEquals(1, second, "命中缓存后只该剩一条实时库存查询，实际 " + second);

        Long ttl = stringRedisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 1800 && ttl <= 2100, "TTL 应为 1800~2100（基础 + 随机抖动），实际 " + ttl);
    }

    @Test
    @DisplayName("防穿透：不存在的 id 首次回源、写入空值标记，第二次完全不查库")
    void detail_ghostIdShouldBeBlockedByNullMark() {
        String key = "med:detail:" + GHOST_ID;
        stringRedisTemplate.delete(key);

        int first = sqlCount(() -> assertThrows(BusinessException.class,
                () -> medicineService.detail(GHOST_ID)));
        assertEquals("", stringRedisTemplate.opsForValue().get(key), "应写入空值标记");

        int second = sqlCount(() -> assertThrows(BusinessException.class,
                () -> medicineService.detail(GHOST_ID)));

        System.out.println("[it] 空值标记前 SQL = " + first + "，之后 SQL = " + second);
        assertTrue(first >= 1);
        assertEquals(0, second, "命中空值标记后不该再查库，实际 " + second);
    }

    @Test
    @DisplayName("写路径删缓存 + 延迟双删调度生效")
    void evict_delayedEvictShouldRemoveKeyLater() throws Exception {
        stringRedisTemplate.opsForValue().set("med:detail:" + MEDICINE_ID, "{\"stale\":true}");

        medicineCache.evict(MEDICINE_ID);
        assertNull(stringRedisTemplate.opsForValue().get("med:detail:" + MEDICINE_ID));

        // 模拟「删缓存后并发读又把旧值写回」的情况，验证延迟双删确实会再删一次
        stringRedisTemplate.opsForValue().set("med:detail:" + MEDICINE_ID, "{\"stale\":true}");
        medicineCache.evictDelayed(MEDICINE_ID);
        Thread.sleep(1200);
        assertNull(stringRedisTemplate.opsForValue().get("med:detail:" + MEDICINE_ID),
                "延迟双删应在 500ms 后把 key 再删一次");
    }

    @Test
    @DisplayName("库存预减：原子扣减、失败不扣、回滚归还，且与 DB 预热值一致")
    void stock_preDeductAndRollback() {
        Integer before = stockCache.available(MEDICINE_ID);
        assertNotNull(before, "启动预热应已写入 stock:med:" + MEDICINE_ID);

        assertTrue(stockCache.tryPreDeduct(MEDICINE_ID, 3));
        assertEquals(before - 3, stockCache.available(MEDICINE_ID));

        stockCache.rollback(MEDICINE_ID, 3);
        assertEquals(before, stockCache.available(MEDICINE_ID));

        assertFalse(stockCache.tryPreDeduct(MEDICINE_ID, 100000), "超出库存应快速失败");
        assertEquals(before, stockCache.available(MEDICINE_ID), "失败时不能扣减");
        System.out.println("[it] 药品 1 预热可售库存 = " + before);
    }
}
