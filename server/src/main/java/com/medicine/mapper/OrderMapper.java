package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    @Select("<script>SELECT o.*, u.username FROM orders o JOIN users u ON o.user_id = u.user_id WHERE 1=1 ${ew.customSqlSegment}</script>")
    java.util.List<Order> selectWithUser(@Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<Order> wrapper);

    @Select("<script>SELECT COUNT(*) FROM orders o <where> ${ew.sqlSegment} </where></script>")
    long selectCountWithUser(@Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<Order> wrapper);

    @Update("UPDATE orders SET order_status = #{status}, version = version + 1 WHERE order_id = #{orderId} AND version = #{version}")
    int updateStatusOptimistic(@Param("orderId") Long orderId, @Param("status") int status, @Param("version") int version);

    @Select("SELECT SUM(actual_amount) as total_sales, COUNT(*) as total_orders, AVG(actual_amount) as avg_order_amount FROM orders WHERE order_status = 5")
    java.util.Map<String, Object> selectSalesSummary();

    @Select("SELECT DATE_FORMAT(created_at, #{format}) as date, SUM(actual_amount) as sales, COUNT(*) as orders FROM orders WHERE order_status = 5 GROUP BY date ORDER BY date DESC LIMIT 30")
    java.util.List<java.util.Map<String, Object>> selectSalesTrend(@Param("format") String format);

    @Select("SELECT order_status, COUNT(*) as count FROM orders GROUP BY order_status")
    java.util.List<java.util.Map<String, Object>> selectOrderStatusGroup();

    @Select("SELECT SUM(actual_amount) as total_sales, COUNT(*) as total_orders, AVG(actual_amount) as avg_order_amount FROM orders WHERE order_status = 5 AND created_at >= #{startDate} AND created_at <= #{endDate}")
    java.util.Map<String, Object> selectSalesSummaryWithDate(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Select("SELECT DATE_FORMAT(created_at, #{format}) as date, SUM(actual_amount) as sales, COUNT(*) as orders FROM orders WHERE order_status = 5 AND created_at >= #{startDate} AND created_at <= #{endDate} GROUP BY date ORDER BY date ASC")
    java.util.List<java.util.Map<String, Object>> selectSalesTrendWithDate(@Param("format") String format, @Param("startDate") String startDate, @Param("endDate") String endDate);

    @Select("SELECT COUNT(*) FROM users WHERE created_at >= #{startDate}")
    long selectNewUserCount(@Param("startDate") String startDate);
}
