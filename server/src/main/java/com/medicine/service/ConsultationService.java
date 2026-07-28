package com.medicine.service;

import com.medicine.entity.Consultation;
import java.util.List;

public interface ConsultationService {
    Consultation create(Long userId, String question);
    List<Consultation> list(Long userId);
}
