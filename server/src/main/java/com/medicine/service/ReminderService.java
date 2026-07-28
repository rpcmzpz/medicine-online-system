package com.medicine.service;

import com.medicine.entity.MedicationReminder;
import java.util.List;
import java.util.Map;

public interface ReminderService {
    MedicationReminder create(Long userId, Map<String, Object> params);
    List<Map<String, Object>> list(Long userId);
    MedicationReminder update(Long userId, Long reminderId, Map<String, Object> params);
    void remove(Long userId, Long reminderId);
}
