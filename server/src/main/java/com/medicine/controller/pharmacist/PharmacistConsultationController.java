package com.medicine.controller.pharmacist;

import com.medicine.common.Result;
import com.medicine.service.PharmacistConsultationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pharmacist/consultations")
public class PharmacistConsultationController {

    @Autowired
    private PharmacistConsultationService pharmacistConsultationService;

    @GetMapping("/pending")
    public Result pendingList() {
        return Result.success(pharmacistConsultationService.pendingList());
    }

    @PatchMapping("/{consultationId}/reply")
    public Result reply(@PathVariable Long consultationId,
                        @RequestBody Map<String, Object> body,
                        HttpServletRequest request) {
        Long pharmacistId = (Long) request.getAttribute("userId");
        String answer = (String) body.get("answer");
        return Result.success(pharmacistConsultationService.reply(consultationId, pharmacistId, answer), "回复成功");
    }
}
