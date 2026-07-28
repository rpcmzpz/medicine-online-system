package com.medicine.service;

import com.medicine.common.Result;

import java.util.Map;

public interface OrderService {
    Result create(Map<String, Object> params, Long userId);
    Result list(Map<String, Object> params, Long userId);
    Result detail(Long orderId, Long userId);
    Result cancel(Long orderId, Long userId);
    Result pay(Long orderId, Long userId, int paymentMethod);
    Result confirm(Long orderId, Long userId);
}
