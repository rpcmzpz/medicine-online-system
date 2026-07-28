package com.medicine.service;

import java.util.Map;

public interface AdminStatsService {
    Map<String, Object> sales(Map<String, Object> params);

    Map<String, Object> medicineRanking(Map<String, Object> params);

    Map<String, Object> orderStatus();
}
