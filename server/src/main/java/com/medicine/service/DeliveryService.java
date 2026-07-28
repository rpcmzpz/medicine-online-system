package com.medicine.service;

import com.medicine.entity.Delivery;
import java.util.List;
import java.util.Map;

public interface DeliveryService {
    List<Delivery> pendingList();
    void accept(Long deliveryId, Long userId);
    void updateStatus(Long deliveryId, Long userId, int deliveryStatus);
    Map<String, Object> getStatus(Long orderId, Long userId);
}
