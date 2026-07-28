package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.PrescriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionController {

    @Autowired
    private PrescriptionService prescriptionService;

    @PostMapping("/upload")
    public Result upload(@RequestParam("files") MultipartFile[] files, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return prescriptionService.upload(files, userId);
    }

    @GetMapping("/{prescriptionId}")
    public Result getStatus(@PathVariable Long prescriptionId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return prescriptionService.status(prescriptionId, userId);
    }
}
