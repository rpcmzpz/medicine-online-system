package com.medicine.service;

import java.util.Map;

public interface PharmacistPrescriptionService {
    Map<String, Object> pendingList();

    Map<String, Object> review(Long prescriptionId, Long pharmacistId, int reviewStatus, String reviewRemark);
}
