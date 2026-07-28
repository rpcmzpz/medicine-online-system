package com.medicine.service.impl;

import com.medicine.common.BusinessException;
import com.medicine.entity.Consultation;
import com.medicine.mapper.ConsultationMapper;
import com.medicine.service.PharmacistConsultationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PharmacistConsultationServiceImpl implements PharmacistConsultationService {

    @Autowired
    private ConsultationMapper consultationMapper;

    @Override
    public Map<String, Object> pendingList() {
        List<Consultation> list = consultationMapper.selectPendingList();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        return result;
    }

    @Override
    public Map<String, Object> reply(Long consultationId, Long pharmacistId, String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new BusinessException("回复内容不能为空", 40001);
        }

        Consultation consultation = consultationMapper.selectById(consultationId);
        if (consultation == null) {
            throw new BusinessException("咨询不存在", 40401, 404);
        }
        if (consultation.getStatus() != null && consultation.getStatus() != 0) {
            throw new BusinessException("该咨询已被回复", 40001);
        }

        Consultation updateConsultation = new Consultation();
        updateConsultation.setConsultationId(consultationId);
        updateConsultation.setAnswer(answer);
        updateConsultation.setPharmacistId(pharmacistId);
        updateConsultation.setStatus(1);
        updateConsultation.setAnsweredAt(LocalDateTime.now());
        consultationMapper.updateById(updateConsultation);

        Map<String, Object> result = new HashMap<>();
        result.put("consultation_id", consultationId);
        return result;
    }
}
