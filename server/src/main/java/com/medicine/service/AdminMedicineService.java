package com.medicine.service;

import java.util.Map;

public interface AdminMedicineService {
    Map<String, Object> list(Map<String, Object> params);

    Map<String, Object> create(Map<String, Object> params);

    void update(Long medicineId, Map<String, Object> params);

    void toggleStatus(Long medicineId, int status);

    void updateInventory(Long medicineId, int stockQuantity, Integer alertThreshold);
}
