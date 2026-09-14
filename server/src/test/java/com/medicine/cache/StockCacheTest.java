package com.medicine.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 库存 Redis 预减的三种返回分支：通过、库存不足快速失败、未预热/故障降级。
 */
@ExtendWith(MockitoExtension.class)
class StockCacheTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private StockCache stockCache;

    private void prepare(StockCache cache) {
        ReflectionTestUtils.setField(cache, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(cache, "deductStockScript", new DefaultRedisScript<Long>());
        ReflectionTestUtils.setField(cache, "enabled", true);
        stockCache = cache;
    }

    @Test
    @DisplayName("Lua 返回剩余库存 >= 0 时允许继续下单")
    void tryPreDeduct_enoughStock_shouldReturnTrue() {
        prepare(new StockCache());
        when(stringRedisTemplate.execute(any(), anyList(), any())).thenReturn(8L);

        assertTrue(stockCache.tryPreDeduct(1L, 2));
    }

    @Test
    @DisplayName("Lua 返回 -2（库存不足）时返回 false，可快速失败不再查库")
    void tryPreDeduct_notEnough_shouldReturnFalse() {
        prepare(new StockCache());
        when(stringRedisTemplate.execute(any(), anyList(), any())).thenReturn(-2L);

        assertFalse(stockCache.tryPreDeduct(1L, 2));
    }

    @Test
    @DisplayName("Lua 返回 -1（key 未预热）时降级返回 true，交给 DB 乐观锁兜底")
    void tryPreDeduct_notPreheated_shouldDegradeToDb() {
        prepare(new StockCache());
        when(stringRedisTemplate.execute(any(), anyList(), any())).thenReturn(-1L);

        assertTrue(stockCache.tryPreDeduct(1L, 2));
    }

    @Test
    @DisplayName("Redis 抛异常时降级返回 true，不能让缓存故障阻断下单")
    void tryPreDeduct_redisDown_shouldDegradeToDb() {
        prepare(new StockCache());
        when(stringRedisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertTrue(stockCache.tryPreDeduct(1L, 2));
    }

    @Test
    @DisplayName("回滚时按 key 归还库存，不抛异常")
    void rollback_shouldNotThrow() {
        prepare(new StockCache());
        when(stringRedisTemplate.execute(any(), anyList(), any())).thenReturn(10L);

        stockCache.rollback(1L, 2);
        // 到达这里即视为通过：回滚是补偿动作，任何情况下都不应该把业务异常抛出去
        assertTrue(true);
    }

    @Test
    @DisplayName("缓存开关关闭时直接放行，完全不碰 Redis")
    void tryPreDeduct_disabled_shouldBypassRedis() {
        StockCache disabled = new StockCache();
        ReflectionTestUtils.setField(disabled, "enabled", false);

        assertTrue(disabled.tryPreDeduct(1L, 2));
    }
}
