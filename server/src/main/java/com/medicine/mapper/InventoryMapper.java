package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

/**
 * 库存表的数据访问。
 *
 * <p>三条写操作（锁定 / 解锁 / 扣减）全部是 <b>乐观锁 CAS</b>：
 * {@code WHERE version = #{version}} 且 {@code version = version + 1}，
 * 影响行数为 0 即代表版本已被其他事务改过（或条件不满足），由上层重读后重试。
 *
 * <p>锁定与扣减还把「可售量是否够」写进 WHERE，让 MySQL 的原子更新本身成为
 * 最后一道防超卖屏障——即使应用层的校验被并发穿透，行上的判断也不会放过。
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 支付时扣减：总库存与锁定库存同减（可售量不变，故无需动 Redis 预减值）。
     */
    @Update("UPDATE inventory SET version = version + 1, "
            + "stock_quantity = stock_quantity - #{quantity}, "
            + "locked_quantity = GREATEST(locked_quantity - #{quantity}, 0) "
            + "WHERE medicine_id = #{medicineId} AND version = #{version} "
            + "AND stock_quantity >= #{quantity} AND locked_quantity >= #{quantity}")
    int deductStock(@Param("medicineId") Long medicineId,
                    @Param("quantity") int quantity,
                    @Param("version") int version);

    /**
     * 下单锁定：锁定库存增加，条件里带「可售量仍然足够」，用于兜住并发超卖。
     */
    @Update("UPDATE inventory SET version = version + 1, "
            + "locked_quantity = locked_quantity + #{quantity} "
            + "WHERE medicine_id = #{medicineId} AND version = #{version} "
            + "AND stock_quantity - locked_quantity >= #{quantity}")
    int lockStock(@Param("medicineId") Long medicineId,
                  @Param("quantity") int quantity,
                  @Param("version") int version);

    /**
     * 解锁归还（订单取消、处方驳回）。GREATEST 保证重复解锁不会把锁定数打成负数。
     */
    @Update("UPDATE inventory SET version = version + 1, "
            + "locked_quantity = GREATEST(locked_quantity - #{quantity}, 0) "
            + "WHERE medicine_id = #{medicineId} AND version = #{version}")
    int unlockStock(@Param("medicineId") Long medicineId,
                    @Param("quantity") int quantity,
                    @Param("version") int version);

    /**
     * 当前读（{@code SELECT ... FOR UPDATE}），用于 CAS 冲突后重读最新版本号。
     *
     * <p>必须用它而不是普通 select：MySQL 默认可重复读下，同一事务内的普通查询走快照，
     * 重试时读到的还是旧 version，会一直 CAS 失败。
     */
    @Select("SELECT * FROM inventory WHERE medicine_id = #{medicineId} FOR UPDATE")
    Inventory selectVersionForUpdate(@Param("medicineId") Long medicineId);

    /**
     * 普通读，取 version 供第一次 CAS 使用。
     */
    @Select("SELECT * FROM inventory WHERE medicine_id = #{medicineId}")
    Inventory selectVersion(@Param("medicineId") Long medicineId);
}
