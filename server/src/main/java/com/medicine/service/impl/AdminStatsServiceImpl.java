package com.medicine.service.impl;

import com.medicine.mapper.OrderItemMapper;
import com.medicine.mapper.OrderMapper;
import com.medicine.service.AdminStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Override
    public Map<String, Object> sales(Map<String, Object> params) {
        String period = (String) params.getOrDefault("period", "day");
        String startDate = (String) params.get("start_date");
        String endDate = (String) params.get("end_date");

        if (startDate == null || startDate.trim().isEmpty()) {
            startDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (endDate == null || endDate.trim().isEmpty()) {
            endDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        String endDateFull = endDate + " 23:59:59";

        String format;
        switch (period) {
            case "week":
                format = "%Y-%u";
                break;
            case "month":
                format = "%Y-%m";
                break;
            default:
                format = "%Y-%m-%d";
                break;
        }

        Map<String, Object> summary = orderMapper.selectSalesSummaryWithDate(startDate, endDateFull);
        if (summary == null) {
            summary = new HashMap<>();
            summary.put("total_sales", 0);
            summary.put("total_orders", 0);
            summary.put("avg_order_amount", 0);
        }

        List<Map<String, Object>> trend = orderMapper.selectSalesTrendWithDate(format, startDate, endDateFull);

        String thirtyDaysAgo = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long newUsers = orderMapper.selectNewUserCount(thirtyDaysAgo);

        Map<String, Object> result = new HashMap<>();
        result.put("total_sales", summary.getOrDefault("total_sales", 0));
        result.put("total_orders", summary.getOrDefault("total_orders", 0));
        result.put("avg_order_amount", summary.getOrDefault("avg_order_amount", 0));
        result.put("sales_trend", trend);
        result.put("new_users", newUsers);
        result.put("period", period);
        result.put("start_date", startDate);
        result.put("end_date", endDate);
        return result;
    }

    @Override
    public Map<String, Object> medicineRanking(Map<String, Object> params) {
        int limit = params.get("limit") != null ? Integer.parseInt(params.get("limit").toString()) : 20;
        LocalDate today = LocalDate.now();
        String startDate = params.get("start_date") != null ? (String) params.get("start_date") : today.minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String endDate = params.get("end_date") != null ? (String) params.get("end_date") : today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String endDateFull = endDate + " 23:59:59";

        List<Map<String, Object>> ranking = orderItemMapper.selectMedicineRankingWithDate(limit, startDate, endDateFull);

        Map<String, Object> result = new HashMap<>();
        result.put("list", ranking);
        result.put("limit", limit);
        return result;
    }

    @Override
    public Map<String, Object> orderStatus() {
        List<Map<String, Object>> statusList = orderMapper.selectOrderStatusGroup();

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("pending_payment", 0);
        result.put("pending_review", 0);
        result.put("pending_dispense", 0);
        result.put("pending_delivery", 0);
        result.put("delivering", 0);
        result.put("completed", 0);
        result.put("cancelled", 0);
        result.put("refunded", 0);

        if (statusList != null) {
            for (Map<String, Object> row : statusList) {
                Object statusObj = row.get("order_status");
                Object countObj = row.get("count");
                if (statusObj != null && countObj != null) {
                    int status = Integer.parseInt(statusObj.toString());
                    long count = Long.parseLong(countObj.toString());
                    String key = getStatusKey(status);
                    if (key != null) {
                        result.put(key, count);
                    }
                }
            }
        }

        return result;
    }

    private String getStatusKey(int status) {
        switch (status) {
            case 0: return "pending_payment";
            case 1: return "pending_review";
            case 2: return "pending_dispense";
            case 3: return "pending_delivery";
            case 4: return "delivering";
            case 5: return "completed";
            case 6: return "cancelled";
            case 7: return "refunded";
            default: return null;
        }
    }
}
