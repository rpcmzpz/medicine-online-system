package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
    @Update("UPDATE inventory SET stock_quantity = stock_quantity - #{quantity}, locked_quantity = GREATEST(locked_quantity - #{quantity}, 0) WHERE medicine_id = #{medicineId}")
    int deductStock(@Param("medicineId") Long medicineId, @Param("quantity") int quantity);

    @Update("UPDATE inventory SET locked_quantity = locked_quantity + #{quantity} WHERE medicine_id = #{medicineId}")
    int lockStock(@Param("medicineId") Long medicineId, @Param("quantity") int quantity);

    @Update("UPDATE inventory SET locked_quantity = GREATEST(locked_quantity - #{quantity}, 0) WHERE medicine_id = #{medicineId}")
    int unlockStock(@Param("medicineId") Long medicineId, @Param("quantity") int quantity);
}
