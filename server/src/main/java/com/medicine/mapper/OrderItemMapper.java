package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
    @Select("SELECT oi.medicine_id, oi.medicine_name, SUM(oi.quantity) as total_quantity, SUM(oi.subtotal) as total_sales FROM order_items oi JOIN orders o ON oi.order_id=o.order_id WHERE o.order_status=5 GROUP BY oi.medicine_id, oi.medicine_name ORDER BY total_quantity DESC LIMIT #{limit}")
    List<Map<String, Object>> selectMedicineRanking(@Param("limit") int limit);

    @Select("SELECT * FROM order_items WHERE order_id = #{orderId}")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT oi.medicine_id, oi.medicine_name, SUM(oi.quantity) as total_quantity, SUM(oi.subtotal) as total_sales FROM order_items oi JOIN orders o ON oi.order_id=o.order_id WHERE o.order_status=5 AND o.created_at >= #{startDate} AND o.created_at <= #{endDate} GROUP BY oi.medicine_id, oi.medicine_name ORDER BY total_quantity DESC LIMIT #{limit}")
    List<Map<String, Object>> selectMedicineRankingWithDate(@Param("limit") int limit, @Param("startDate") String startDate, @Param("endDate") String endDate);
}
