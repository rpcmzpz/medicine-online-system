package com.medicine.service;

import java.util.Map;

public interface AdminOrderService {
    Map<String, Object> list(Map<String, Object> params);

    void dispense(Long orderId);

    void ship(Long orderId);
}
