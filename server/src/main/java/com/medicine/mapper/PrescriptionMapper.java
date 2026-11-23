package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PrescriptionMapper extends BaseMapper<Prescription> {
    /**
     * 药师待审列表。
     *
     * 必须 JOIN orders 且只取「已支付 + 待审核(1)」的处方：
     * 处方在结算页上传时就写库了，此时用户可能还没付款。
     * 原 SQL 只按 review_status=0 过滤，药师会把「没付钱的单子」审过，
     * 审核通过后订单状态直接跳到 2(待配药)，等于白送一单。
     */
    @Select("SELECT p.*, u.username as user_name FROM prescriptions p " +
            "JOIN users u ON p.user_id=u.user_id " +
            "JOIN orders o ON p.order_id=o.order_id " +
            "WHERE p.review_status=0 AND o.order_status=1 AND o.has_prescription=1 " +
            "ORDER BY p.created_at ASC")
    java.util.List<Prescription> selectPendingList();
}
