package com.medicine.service;

import java.util.Map;

public interface PharmacistConsultationService {
    Map<String, Object> pendingList();

    Map<String, Object> reply(Long consultationId, Long pharmacistId, String answer);
}
