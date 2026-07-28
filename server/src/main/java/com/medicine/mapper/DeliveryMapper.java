package com.medicine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicine.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DeliveryMapper extends BaseMapper<Delivery> {
    @Select("SELECT d.*, o.order_no, a.receiver_name, a.phone, CONCAT(a.province,a.city,a.district,a.detail) as address FROM deliveries d JOIN orders o ON d.order_id=o.order_id JOIN addresses a ON o.address_id=a.address_id WHERE d.delivery_status=0 ORDER BY d.created_at ASC")
    java.util.List<Delivery> selectPendingList();

    @Update("UPDATE deliveries SET delivery_status=1, delivery_person_id=#{personId}, pickup_time=NOW() WHERE delivery_id=#{deliveryId} AND delivery_status=0")
    int acceptDelivery(@Param("deliveryId") Long deliveryId, @Param("personId") Long personId);
}
