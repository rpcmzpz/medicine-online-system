package com.medicine.cache;

/**
 * Redis key 统一收口，避免字符串散落在各处业务代码里。
 */
public final class CacheKeys {

    /** 药品详情缓存（Cache Aside） */
    private static final String MEDICINE_DETAIL = "med:detail:";

    /** 药品详情缓存重建的互斥锁（防击穿用） */
    private static final String MEDICINE_DETAIL_LOCK = "lock:med:detail:";

    /** 药品可售库存计数（Redis 预减用，MySQL 才是真值） */
    private static final String MEDICINE_STOCK = "stock:med:";

    /**
     * 空值标记：查库确认「药品不存在」时写入缓存的值。
     * 恶意请求大量不存在的 id 时，直接命中标记返回，不会打到数据库（防缓存穿透）。
     */
    public static final String NULL_MARK = "";

    private CacheKeys() {
    }

    public static String medicineDetail(Long medicineId) {
        return MEDICINE_DETAIL + medicineId;
    }

    public static String medicineDetailLock(Long medicineId) {
        return MEDICINE_DETAIL_LOCK + medicineId;
    }

    public static String medicineStock(Long medicineId) {
        return MEDICINE_STOCK + medicineId;
    }
}
