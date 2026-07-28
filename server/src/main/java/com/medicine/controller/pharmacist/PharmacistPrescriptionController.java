package com.medicine.controller.pharmacist;

import com.medicine.common.Result;
import com.medicine.service.PharmacistPrescriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pharmacist/prescriptions")
public class PharmacistPrescriptionController {

    @Autowired
    private PharmacistPrescriptionService pharmacistPrescriptionService;

    @GetMapping("/pending")
    public Result pendingList() {
        return Result.success(pharmacistPrescriptionService.pendingList());
    }

    @PatchMapping("/{prescriptionId}/review")
    public Result review(@PathVariable Long prescriptionId,
                         @RequestBody Map<String, Object> body,
                         HttpServletRequest request) {
        Long pharmacistId = (Long) request.getAttribute("userId");
        int reviewStatus = Integer.parseInt(body.get("review_status").toString());
        String reviewRemark = (String) body.get("review_remark");
        return Result.success(pharmacistPrescriptionService.review(prescriptionId, pharmacistId, reviewStatus, reviewRemark), "审核完成");
    }
}
