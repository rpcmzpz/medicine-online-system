package com.medicine.service;

import com.medicine.common.Result;
import org.springframework.web.multipart.MultipartFile;

public interface PrescriptionService {
    Result upload(MultipartFile[] files, Long userId);
    Result status(Long prescriptionId, Long userId);
}
