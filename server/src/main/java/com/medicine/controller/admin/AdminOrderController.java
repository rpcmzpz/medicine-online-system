package com.medicine.controller.admin;

import com.medicine.common.Result;
import com.medicine.service.AdminOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    @Autowired
    private AdminOrderService adminOrderService;

    @GetMapping
    public Result list(@RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String start_date,
                       @RequestParam(required = false) String end_date,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int page_size) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", status);
        params.put("keyword", keyword);
        params.put("start_date", start_date);
        params.put("end_date", end_date);
        params.put("page", page);
        params.put("page_size", page_size);
        return Result.success(adminOrderService.list(params));
    }

    @PatchMapping("/{orderId}/dispense")
    public Result dispense(@PathVariable Long orderId) {
        adminOrderService.dispense(orderId);
        return Result.success(null, "配药成功");
    }

    @PatchMapping("/{orderId}/ship")
    public Result ship(@PathVariable Long orderId) {
        adminOrderService.ship(orderId);
        return Result.success(null, "确认配送");
    }
}
