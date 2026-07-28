package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.entity.Delivery;
import com.medicine.service.DeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/delivery")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @GetMapping("/pending")
    public Result pendingList() {
        List<Delivery> list = deliveryService.pendingList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return Result.success(result);
    }

    @GetMapping("/{orderId}/status")
    public Result getStatus(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> statusInfo = deliveryService.getStatus(orderId, userId);
        return Result.success(statusInfo);
    }

    @PatchMapping("/{deliveryId}/accept")
    public Result accept(@PathVariable Long deliveryId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        deliveryService.accept(deliveryId, userId);
        return Result.success(null, "接单成功");
    }

    @PatchMapping("/{deliveryId}/status")
    public Result updateStatus(@PathVariable Long deliveryId, @RequestBody Map<String, Integer> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer deliveryStatus = body.get("delivery_status");
        deliveryService.updateStatus(deliveryId, userId, deliveryStatus);
        return Result.success(null, "状态更新成功");
    }
}
