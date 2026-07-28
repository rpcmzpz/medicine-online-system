package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.Delivery;
import com.medicine.entity.Order;
import com.medicine.mapper.DeliveryMapper;
import com.medicine.mapper.OrderMapper;
import com.medicine.service.AdminOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private DeliveryMapper deliveryMapper;

    @Override
    public Map<String, Object> list(Map<String, Object> params) {
        int page = params.get("page") != null ? Integer.parseInt(params.get("page").toString()) : 1;
        int pageSize = params.get("page_size") != null ? Integer.parseInt(params.get("page_size").toString()) : 20;
        Integer status = params.get("status") != null ? Integer.parseInt(params.get("status").toString()) : null;
        String keyword = (String) params.get("keyword");
        String startDate = (String) params.get("start_date");
        String endDate = (String) params.get("end_date");

        QueryWrapper<Order> whereWrapper = new QueryWrapper<>();
        if (status != null) {
            whereWrapper.eq("o.order_status", status);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            whereWrapper.and(w -> w.like("o.order_no", keyword).or().like("u.username", keyword));
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            whereWrapper.ge("o.created_at", startDate);
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            whereWrapper.le("o.created_at", endDate + " 23:59:59");
        }

        long total = orderMapper.selectCountWithUser(whereWrapper);

        whereWrapper.orderByDesc("o.created_at");
        int offset = (page - 1) * pageSize;
        whereWrapper.last("LIMIT " + offset + "," + pageSize);
        List<Order> list = orderMapper.selectWithUser(whereWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    @Override
    @Transactional
    public void dispense(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在", 40401, 404);
        }
        if (order.getOrderStatus() != 2) {
            throw new BusinessException("当前订单状态不允许配药", 40001);
        }

        Order updateOrder = new Order();
        updateOrder.setOrderId(orderId);
        updateOrder.setOrderStatus(3);
        orderMapper.updateById(updateOrder);

        Delivery delivery = new Delivery();
        delivery.setOrderId(orderId);
        delivery.setDeliveryStatus(0);
        deliveryMapper.insert(delivery);
    }

    @Override
    @Transactional
    public void ship(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在", 40401, 404);
        }
        if (order.getOrderStatus() != 3) {
            throw new BusinessException("当前订单状态不允许配送", 40001);
        }

        // 订单进入配送中，配送员通过 /api/v1/delivery 接单
        Order updateOrder = new Order();
        updateOrder.setOrderId(orderId);
        updateOrder.setOrderStatus(4);
        orderMapper.updateById(updateOrder);
    }
}
