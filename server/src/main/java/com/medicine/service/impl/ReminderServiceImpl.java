package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.MedicationReminder;
import com.medicine.mapper.MedicationReminderMapper;
import com.medicine.service.ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReminderServiceImpl implements ReminderService {

    @Autowired
    private MedicationReminderMapper reminderMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MedicationReminder create(Long userId, Map<String, Object> params) {
        String medicineName = (String) params.get("medicine_name");
        String dosage = (String) params.get("dosage");
        String frequency = (String) params.get("frequency");
        Object remindTimesObj = params.get("remind_times");
        String startDateStr = (String) params.get("start_date");
        String endDateStr = (String) params.get("end_date");

        if (medicineName == null || medicineName.trim().isEmpty()) {
            throw new BusinessException("药品名称不能为空", 40000);
        }
        if (dosage == null || dosage.trim().isEmpty()) {
            throw new BusinessException("用量不能为空", 40000);
        }
        if (frequency == null || frequency.trim().isEmpty()) {
            throw new BusinessException("用药频率不能为空", 40000);
        }
        if (remindTimesObj == null) {
            throw new BusinessException("提醒时间不能为空", 40000);
        }
        if (startDateStr == null || startDateStr.trim().isEmpty()) {
            throw new BusinessException("开始日期不能为空", 40000);
        }

        MedicationReminder reminder = new MedicationReminder();
        reminder.setUserId(userId);
        reminder.setMedicineName(medicineName.trim());
        reminder.setDosage(dosage.trim());
        reminder.setFrequency(frequency.trim());

        try {
            reminder.setRemindTimes(objectMapper.writeValueAsString(remindTimesObj));
        } catch (JsonProcessingException e) {
            throw new BusinessException("提醒时间格式错误", 40000);
        }

        reminder.setStartDate(LocalDate.parse(startDateStr));
        // end_date is optional
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            reminder.setEndDate(LocalDate.parse(endDateStr));
        }
        reminder.setIsActive(1);
        reminder.setCreatedAt(LocalDateTime.now());

        reminderMapper.insert(reminder);
        return reminder;
    }

    @Override
    public List<Map<String, Object>> list(Long userId) {
        QueryWrapper<MedicationReminder> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        List<MedicationReminder> list = reminderMapper.selectList(wrapper);
        return list.stream().map(reminder -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("reminder_id", reminder.getReminderId());
            map.put("medicine_name", reminder.getMedicineName());
            map.put("dosage", reminder.getDosage());
            map.put("frequency", reminder.getFrequency());
            map.put("remind_times", reminder.getRemindTimes());
            map.put("start_date", reminder.getStartDate());
            map.put("end_date", reminder.getEndDate());
            map.put("is_active", reminder.getIsActive());
            map.put("created_at", reminder.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public MedicationReminder update(Long userId, Long reminderId, Map<String, Object> params) {
        MedicationReminder reminder = reminderMapper.selectById(reminderId);
        if (reminder == null || !reminder.getUserId().equals(userId)) {
            throw new BusinessException("提醒记录不存在", 40001);
        }

        if (params.containsKey("medicine_name")) {
            String val = (String) params.get("medicine_name");
            if (val == null || val.trim().isEmpty()) {
                throw new BusinessException("药品名称不能为空", 40000);
            }
            reminder.setMedicineName(val.trim());
        }
        if (params.containsKey("dosage")) {
            String val = (String) params.get("dosage");
            if (val == null || val.trim().isEmpty()) {
                throw new BusinessException("用量不能为空", 40000);
            }
            reminder.setDosage(val.trim());
        }
        if (params.containsKey("frequency")) {
            String val = (String) params.get("frequency");
            if (val == null || val.trim().isEmpty()) {
                throw new BusinessException("用药频率不能为空", 40000);
            }
            reminder.setFrequency(val.trim());
        }
        if (params.containsKey("remind_times")) {
            try {
                reminder.setRemindTimes(objectMapper.writeValueAsString(params.get("remind_times")));
            } catch (JsonProcessingException e) {
                throw new BusinessException("提醒时间格式错误", 40000);
            }
        }
        if (params.containsKey("start_date")) {
            String val = (String) params.get("start_date");
            if (val != null && !val.trim().isEmpty()) {
                reminder.setStartDate(LocalDate.parse(val));
            }
        }
        if (params.containsKey("end_date")) {
            String val = (String) params.get("end_date");
            if (val != null && !val.trim().isEmpty()) {
                reminder.setEndDate(LocalDate.parse(val));
            }
        }
        if (params.containsKey("is_active")) {
            reminder.setIsActive((Integer) params.get("is_active"));
        }

        reminderMapper.updateById(reminder);
        return reminder;
    }

    @Override
    public void remove(Long userId, Long reminderId) {
        QueryWrapper<MedicationReminder> wrapper = new QueryWrapper<>();
        wrapper.eq("reminder_id", reminderId).eq("user_id", userId);
        int rows = reminderMapper.delete(wrapper);
        if (rows == 0) {
            throw new BusinessException("提醒记录不存在", 40001);
        }
    }
}
