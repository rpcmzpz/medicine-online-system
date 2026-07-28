package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
    @Select("SELECT r.*, u.username FROM reviews r JOIN users u ON r.user_id=u.user_id WHERE r.medicine_id=#{medicineId} ORDER BY r.created_at DESC LIMIT 5")
    java.util.List<Review> selectByMedicineId(@Param("medicineId") Long medicineId);
}
