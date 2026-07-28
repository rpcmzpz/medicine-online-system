package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.common.Result;
import com.medicine.entity.Prescription;
import com.medicine.mapper.PrescriptionMapper;
import com.medicine.service.PrescriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Value("${upload.path}")
    private String uploadPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Result upload(MultipartFile[] files, Long userId) {
        if (files == null || files.length == 0) {
            throw new BusinessException("请上传处方图片", 40001);
        }
        if (files.length > 10) {
            throw new BusinessException("最多上传10张图片", 40001);
        }

        List<String> imageUrls = new ArrayList<>();
        try {
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                String originalName = file.getOriginalFilename();
                String extension = "";
                if (originalName != null && originalName.contains(".")) {
                    extension = originalName.substring(originalName.lastIndexOf("."));
                }
                String fileName = System.currentTimeMillis() + "_" + randomHex(8) + extension;
                Path filePath = uploadDir.resolve(fileName);
                file.transferTo(filePath.toFile());
                imageUrls.add("/uploads/" + fileName);
            }
        } catch (IOException e) {
            throw new BusinessException("文件上传失败: " + e.getMessage(), 50001);
        }

        if (imageUrls.isEmpty()) {
            throw new BusinessException("没有有效的上传文件", 40001);
        }

        String imageUrlsJson;
        try {
            imageUrlsJson = objectMapper.writeValueAsString(imageUrls);
        } catch (IOException e) {
            throw new BusinessException("文件上传失败", 50001);
        }

        Prescription prescription = new Prescription();
        prescription.setUserId(userId);
        prescription.setImageUrls(imageUrlsJson);
        prescription.setReviewStatus(0);
        prescriptionMapper.insert(prescription);

        Map<String, Object> result = new HashMap<>();
        result.put("prescription_id", prescription.getPrescriptionId());
        result.put("image_urls", imageUrls);
        result.put("review_status", 0);
        return Result.created(result, "处方上传成功");
    }

    @Override
    public Result status(Long prescriptionId, Long userId) {
        Prescription prescription = prescriptionMapper.selectOne(
                new QueryWrapper<Prescription>().eq("prescription_id", prescriptionId));
        if (prescription == null) {
            throw new BusinessException("处方不存在", 40001);
        }

        List<String> imageUrls = new ArrayList<>();
        String imageUrlsStr = prescription.getImageUrls();
        if (imageUrlsStr != null && !imageUrlsStr.isEmpty()) {
            try {
                imageUrls = objectMapper.readValue(imageUrlsStr,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception ignored) {}
        }

        Map<String, Object> result = new HashMap<>();
        result.put("prescription_id", prescription.getPrescriptionId());
        result.put("order_id", prescription.getOrderId());
        result.put("user_id", prescription.getUserId());
        result.put("review_status", prescription.getReviewStatus());
        result.put("review_remark", prescription.getReviewRemark());
        result.put("reviewed_at", prescription.getReviewedAt());
        result.put("created_at", prescription.getCreatedAt());
        result.put("image_urls", imageUrls);
        return Result.success(result);
    }

    private String randomHex(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
