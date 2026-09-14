package com.medicine.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 库存的 Redis 原子预减。
 *
 * <p><b>定位</b>：MySQL 始终是库存真值，三段式 lock/deduct/unlock + 乐观锁 version 保证最终不超卖；
 * Redis 计数只是挡在 DB 前面的第一道闸门，把「明知库存不足」的并发请求快速失败掉，
 * 不让它们全部挤到数据库上。两者是「拦截 + 兜底」的组合，不是二选一。
 *
 * <p>计数含义是「可售库存」= stock_quantity - locked_quantity，因此各环节的增减必须对齐：
 * 下单预减、取消/驳回归还、支付不动（支付时总库存和锁定库存同减，可售量不变）、
 * 管理员改库存则按 DB 重置。
 */
@Component
public class StockCache {

    private static final Logger log = LoggerFactory.getLogger(StockCache.class);

    /** 管理员改库存 / 启动预热时，以 DB 为准重置计数 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "deductStockScript")
    private DefaultRedisScript<Long> deductStockScript;

    @Resource(name = "rollbackStockScript")
    private DefaultRedisScript<Long> rollbackStockScript;

    @Value("${medicine.cache.enabled:true}")
    private boolean enabled;

    /**
     * 原子预减库存。
     *
     * @return true 允许继续下单（含 Redis 未启用、未预热的降级）；
     *         false 明确库存不足，可直接快速失败，不必再查库
     */
    public boolean tryPreDeduct(Long medicineId, int quantity) {
        if (!enabled || quantity <= 0) {
            return true;
        }
        try {
            Long remain = stringRedisTemplate.execute(deductStockScript,
                    Collections.singletonList(CacheKeys.medicineStock(medicineId)),
                    String.valueOf(quantity));
            if (remain == null || remain == -1L) {
                // 未预热：交给 DB 的库存校验 + 乐观锁兜底
                return true;
            }
            return remain != -2L;
        } catch (Exception e) {
            log.warn("Redis 库存预减失败，降级由 DB 兜底, medicineId={}", medicineId, e);
            return true;
        }
    }

    /** 归还预减掉的库存（下单失败补偿 / 订单取消 / 处方驳回） */
    public void rollback(Long medicineId, int quantity) {
        if (!enabled || quantity <= 0) {
            return;
        }
        try {
            stringRedisTemplate.execute(rollbackStockScript,
                    Collections.singletonList(CacheKeys.medicineStock(medicineId)),
                    String.valueOf(quantity));
        } catch (Exception e) {
            log.warn("回滚 Redis 库存失败, medicineId={}", medicineId, e);
        }
    }

    /** 以 DB 为准重置可售库存（启动预热、管理员调整库存） */
    public void sync(Long medicineId, int availableStock) {
        if (!enabled) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(CacheKeys.medicineStock(medicineId),
                    String.valueOf(Math.max(availableStock, 0)));
        } catch (Exception e) {
            log.warn("同步 Redis 库存失败, medicineId={}", medicineId, e);
        }
    }

    /** 读取可售库存；null 表示尚未预热 */
    public Integer available(Long medicineId) {
        if (!enabled) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(CacheKeys.medicineStock(medicineId));
            return value == null ? null : Integer.valueOf(value);
        } catch (Exception e) {
            log.warn("读取 Redis 库存失败, medicineId={}", medicineId, e);
            return null;
        }
    }
}
