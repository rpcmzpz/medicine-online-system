package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public Result create(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return orderService.create(params, userId);
    }

    @GetMapping
    public Result list(@RequestParam(required = false) Integer status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> params = new HashMap<>();
        params.put("status", status);
        params.put("page", page);
        params.put("page_size", pageSize);
        return orderService.list(params, userId);
    }

    @GetMapping("/{orderId}")
    public Result detail(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return orderService.detail(orderId, userId);
    }

    @PatchMapping("/{orderId}/cancel")
    public Result cancel(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return orderService.cancel(orderId, userId);
    }

    @PostMapping("/{orderId}/pay")
    public Result pay(@PathVariable Long orderId,
                      @RequestBody Map<String, Object> params,
                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int paymentMethod = Integer.parseInt(params.get("payment_method").toString());
        return orderService.pay(orderId, userId, paymentMethod);
    }

    @PatchMapping("/{orderId}/confirm")
    public Result confirm(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return orderService.confirm(orderId, userId);
    }
}
