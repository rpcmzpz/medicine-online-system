package com.medicine.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 药品详情缓存（Cache Aside 模式）。
 *
 * <p>一次性堵住缓存的三个经典问题：
 * <ul>
 *   <li><b>穿透</b>：查库不存在（null）的 id 也写一个空值标记（短 TTL），
 *       恶意刷不存在的 id 时直接命中标记返回，不会每次都打到 DB；</li>
 *   <li><b>击穿</b>：热点 key 失效的瞬间，只放一个线程回源重建，其余线程等锁后重读缓存
 *       （互斥锁用 setIfAbsent 加 TTL，释放用 Lua 保证「只删自己的锁」）；</li>
 *   <li><b>雪崩</b>：TTL 加随机抖动，避免大批 key 在同一时刻集体失效。</li>
 * </ul>
 *
 * <p>所有 Redis 调用都做了降级：缓存故障只记日志、业务走 DB，
 * 不让「缓存不可用」升级成「接口不可用」。
 */
@Component
public class MedicineCache {

    private static final Logger log = LoggerFactory.getLogger(MedicineCache.class);

    /** 重建锁最长持有时间（秒），防止持锁线程崩溃后锁一直不释放 */
    private static final long LOCK_TTL_SECONDS = 10L;

    /** 没抢到锁时的等待间隔（毫秒）与最大重试次数 */
    private static final long LOCK_WAIT_MILLIS = 50L;
    private static final int MAX_ATTEMPT = 3;

    /** 延迟双删的延迟时间：要大于一次「读 DB + 回写缓存」的耗时，经验值 500ms */
    private static final long DELAYED_EVICT_MILLIS = 500L;

    /** 延迟双删用的单线程调度器（守护线程，不阻塞应用退出） */
    private static final ScheduledExecutorService DELAYED_EVICTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "medicine-cache-evict");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 独立的 ObjectMapper：不复用 Spring MVC 那个，避免影响接口出参的序列化配置。
     * USE_BIG_DECIMAL_FOR_FLOATS 保证价格、评分这类小数经缓存往返后仍是 BigDecimal，不丢精度和小数位。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "unlockScript")
    private DefaultRedisScript<Long> unlockScript;

    /** 缓存总开关，压测「无缓存 vs 有缓存」对比时可直接关掉 */
    @Value("${medicine.cache.enabled:true}")
    private boolean enabled;

    /** 详情缓存基础 TTL（秒） */
    @Value("${medicine.cache.detail-ttl-seconds:1800}")
    private long detailTtlSeconds;

    /** TTL 随机抖动上限（秒），防雪崩 */
    @Value("${medicine.cache.jitter-seconds:300}")
    private int jitterSeconds;

    /** 空值标记 TTL（秒），防穿透 */
    @Value("${medicine.cache.null-ttl-seconds:120}")
    private long nullTtlSeconds;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Cache Aside 读取：命中直接返回，未命中只让一个线程回源并写缓存。
     *
     * @param loader 回源逻辑；返回 null 表示该药品确实不存在（会被写成空值标记）
     * @return 业务数据；null 表示不存在
     */
    public Map<String, Object> getOrLoad(Long medicineId, Supplier<Map<String, Object>> loader) {
        if (!enabled) {
            return loader.get();
        }
        String key = CacheKeys.medicineDetail(medicineId);
        for (int attempt = 0; attempt < MAX_ATTEMPT; attempt++) {
            String cached;
            try {
                cached = stringRedisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("读取药品详情缓存失败，降级回源 DB, medicineId={}", medicineId, e);
                return loader.get();
            }

            if (cached != null) {
                if (cached.isEmpty()) {
                    // 命中空值标记：之前已确认该 id 不存在，直接返回，不再查库
                    return null;
                }
                try {
                    return readJson(cached);
                } catch (Exception e) {
                    log.warn("药品详情缓存反序列化失败，清掉脏数据后重试, medicineId={}", medicineId, e);
                    evict(medicineId);
                    continue;
                }
            }

            // 未命中：抢重建锁，只让一个线程回源（防击穿）
            String lockKey = CacheKeys.medicineDetailLock(medicineId);
            String token = UUID.randomUUID().toString();
            Boolean locked;
            try {
                locked = stringRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("获取缓存重建锁失败，降级回源 DB, medicineId={}", medicineId, e);
                return loader.get();
            }

            if (!Boolean.TRUE.equals(locked)) {
                // 其他线程正在重建：等一下再重读缓存，避免一起去压 DB
                sleepQuietly();
                continue;
            }

            try {
                Map<String, Object> data = loader.get();
                try {
                    if (data == null) {
                        stringRedisTemplate.opsForValue()
                                .set(key, CacheKeys.NULL_MARK, nullTtlSeconds, TimeUnit.SECONDS);
                        return null;
                    }
                    stringRedisTemplate.opsForValue()
                            .set(key, writeJson(data), ttlWithJitter(), TimeUnit.SECONDS);
                } catch (Exception e) {
                    // 写缓存失败不影响本次返回
                    log.warn("写入药品详情缓存失败, medicineId={}", medicineId, e);
                }
                return data;
            } finally {
                unlockQuietly(lockKey, token);
            }
        }
        // 多次重试仍未拿到数据：兜底直查 DB
        return loader.get();
    }

    /** 删除详情缓存（写路径统一「先更库、再删缓存」） */
    public void evict(Long medicineId) {
        if (!enabled) {
            return;
        }
        try {
            stringRedisTemplate.delete(CacheKeys.medicineDetail(medicineId));
        } catch (Exception e) {
            log.warn("删除药品详情缓存失败, medicineId={}", medicineId, e);
        }
    }

    /**
     * 延迟双删。
     *
     * <p>删完缓存之后，仍可能有并发读线程把「它刚读到的旧值」重新写回缓存，
     * 所以等一小段时间（大于一次「读 DB + 回写缓存」的耗时）再删一次。
     * 更彻底的方案是 MQ 重试删除或 Canal 订阅 binlog，这里用 500ms 延迟足够覆盖单机场景。
     */
    public void evictDelayed(Long medicineId) {
        if (!enabled) {
            return;
        }
        DELAYED_EVICTOR.schedule(() -> evict(medicineId), DELAYED_EVICT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** 基础 TTL + 随机抖动，避免同一时刻批量失效造成雪崩 */
    private long ttlWithJitter() {
        return detailTtlSeconds + ThreadLocalRandom.current().nextInt(Math.max(jitterSeconds, 0) + 1);
    }

    private void unlockQuietly(String lockKey, String token) {
        try {
            stringRedisTemplate.execute(unlockScript, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("释放缓存重建锁失败, lockKey={}", lockKey, e);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(LOCK_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> readJson(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private String writeJson(Map<String, Object> data) throws Exception {
        return MAPPER.writeValueAsString(data);
    }
}
