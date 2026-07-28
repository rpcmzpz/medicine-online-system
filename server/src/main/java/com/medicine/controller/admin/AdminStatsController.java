package com.medicine.controller.admin;

import com.medicine.common.Result;
import com.medicine.service.AdminStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatsController {

    @Autowired
    private AdminStatsService adminStatsService;

    @GetMapping("/sales")
    public Result sales(@RequestParam(defaultValue = "day") String period,
                        @RequestParam(required = false) String start_date,
                        @RequestParam(required = false) String end_date) {
        Map<String, Object> params = new HashMap<>();
        params.put("period", period);
        params.put("start_date", start_date);
        params.put("end_date", end_date);
        return Result.success(adminStatsService.sales(params));
    }

    @GetMapping("/medicine-ranking")
    public Result medicineRanking(@RequestParam(defaultValue = "20") int limit,
                                  @RequestParam(required = false) String start_date,
                                  @RequestParam(required = false) String end_date) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("start_date", start_date);
        params.put("end_date", end_date);
        return Result.success(adminStatsService.medicineRanking(params));
    }

    @GetMapping("/order-status")
    public Result orderStatus() {
        return Result.success(adminStatsService.orderStatus());
    }
}
