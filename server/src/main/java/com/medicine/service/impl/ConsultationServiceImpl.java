package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.Consultation;
import com.medicine.mapper.ConsultationMapper;
import com.medicine.service.ConsultationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConsultationServiceImpl implements ConsultationService {

    @Autowired
    private ConsultationMapper consultationMapper;

    @Override
    public Consultation create(Long userId, String question) {
        if (question == null || question.trim().length() < 5) {
            throw new BusinessException("问题描述不能少于5个字符", 40000);
        }
        Consultation consultation = new Consultation();
        consultation.setUserId(userId);
        consultation.setQuestion(question.trim());
        consultation.setStatus(0);
        consultation.setCreatedAt(LocalDateTime.now());
        consultationMapper.insert(consultation);
        return consultation;
    }

    @Override
    public List<Consultation> list(Long userId) {
        return consultationMapper.selectByUserId(userId);
    }
}
