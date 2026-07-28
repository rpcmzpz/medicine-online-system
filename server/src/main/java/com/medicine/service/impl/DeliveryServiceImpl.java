package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.Delivery;
import com.medicine.entity.Order;
import com.medicine.mapper.DeliveryMapper;
import com.medicine.mapper.OrderMapper;
import com.medicine.service.DeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeliveryServiceImpl implements DeliveryService {

    @Autowired
    private DeliveryMapper deliveryMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public List<Delivery> pendingList() {
        List<Delivery> list = deliveryMapper.selectPendingList();
        for (Delivery delivery : list) {
            if (delivery.getPhone() != null && delivery.getPhone().length() >= 7) {
                String phone = delivery.getPhone();
                String masked = phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
                delivery.setPhone(masked);
            }
        }
        return list;
    }

    @Override
    public void accept(Long deliveryId, Long userId) {
        int affectedRows = deliveryMapper.acceptDelivery(deliveryId, userId);
        if (affectedRows == 0) {
            throw new BusinessException("已被其他配送员接单", 40900, 409);
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long deliveryId, Long userId, int deliveryStatus) {
        if (deliveryStatus != 2 && deliveryStatus != 3) {
            throw new BusinessException("无效的配送状态", 40000);
        }

        Delivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null || delivery.getDeliveryPersonId() == null || !delivery.getDeliveryPersonId().equals(userId)) {
            throw new BusinessException("配送单不存在或无权操作", 40001);
        }

        Order order = orderMapper.selectById(delivery.getOrderId());
        if (order == null) {
            throw new BusinessException("关联订单不存在", 40001);
        }

        delivery.setDeliveryStatus(deliveryStatus);
        if (deliveryStatus == 3) {
            delivery.setDeliveredTime(LocalDateTime.now());
        }
        deliveryMapper.updateById(delivery);

        if (deliveryStatus == 2) {
            order.setOrderStatus(4);
        } else if (deliveryStatus == 3) {
            order.setOrderStatus(5);
        }
        orderMapper.updateById(order);
    }

    @Override
    public Map<String, Object> getStatus(Long orderId, Long userId) {
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq("order_id", orderId).eq("user_id", userId);
        Order order = orderMapper.selectOne(orderWrapper);
        if (order == null) {
            throw new BusinessException("订单不存在", 40001);
        }

        QueryWrapper<Delivery> deliveryWrapper = new QueryWrapper<>();
        deliveryWrapper.eq("order_id", orderId);
        Delivery delivery = deliveryMapper.selectOne(deliveryWrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        String[] statusLabels = {"待接单", "已接单", "配送中", "已送达"};

        if (delivery != null) {
            result.put("deliveryId", delivery.getDeliveryId());
            result.put("deliveryStatus", delivery.getDeliveryStatus());
            result.put("deliveryStatusLabel", delivery.getDeliveryStatus() >= 0 && delivery.getDeliveryStatus() < statusLabels.length
                    ? statusLabels[delivery.getDeliveryStatus()] : "未知状态");
            result.put("deliveryPersonId", delivery.getDeliveryPersonId());
            result.put("pickupTime", delivery.getPickupTime());
            result.put("deliveredTime", delivery.getDeliveredTime());
        } else {
            result.put("deliveryStatus", -1);
            result.put("deliveryStatusLabel", "未分配配送");
        }

        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("orderStatus", order.getOrderStatus());

        return result;
    }
}
