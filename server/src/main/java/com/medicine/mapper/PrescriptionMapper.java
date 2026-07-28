package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PrescriptionMapper extends BaseMapper<Prescription> {
    @Select("SELECT p.*, u.username as user_name FROM prescriptions p JOIN users u ON p.user_id=u.user_id WHERE p.review_status=0 ORDER BY p.created_at ASC")
    java.util.List<Prescription> selectPendingList();
}
