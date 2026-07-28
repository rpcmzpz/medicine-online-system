package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.entity.Consultation;
import com.medicine.service.ConsultationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/consultations")
public class ConsultationController {

    @Autowired
    private ConsultationService consultationService;

    @PostMapping
    public Result create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String question = body.get("question");
        Consultation consultation = consultationService.create(userId, question);
        return Result.created(consultation, "咨询提交成功");
    }

    @GetMapping
    public Result list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Consultation> list = consultationService.list(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return Result.success(result);
    }
}
