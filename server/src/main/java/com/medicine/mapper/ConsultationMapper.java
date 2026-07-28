package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ConsultationMapper extends BaseMapper<Consultation> {
    @Select("SELECT c.*, u.username, u.real_name FROM consultations c JOIN users u ON c.user_id=u.user_id WHERE c.status=0 ORDER BY c.created_at ASC")
    java.util.List<Consultation> selectPendingList();

    @Select("SELECT c.*, u.real_name as pharmacist_name FROM consultations c LEFT JOIN users u ON c.pharmacist_id=u.user_id WHERE c.user_id=#{userId} ORDER BY c.created_at DESC")
    java.util.List<Consultation> selectByUserId(@Param("userId") Long userId);
}
