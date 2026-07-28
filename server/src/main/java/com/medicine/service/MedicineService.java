package com.medicine.service;

import com.medicine.common.Result;

import java.util.Map;

public interface MedicineService {
    Result list(Map<String, Object> params);
    Result detail(Long medicineId);
    Result categories();
}
