package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.cache.StockCache;
import com.medicine.common.BusinessException;
import com.medicine.entity.Order;
import com.medicine.entity.OrderItem;
import com.medicine.entity.Prescription;
import com.medicine.mapper.OrderItemMapper;
import com.medicine.mapper.OrderMapper;
import com.medicine.mapper.PrescriptionMapper;
import com.medicine.service.PharmacistPrescriptionService;
import com.medicine.stock.InventoryStockManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PharmacistPrescriptionServiceImpl implements PharmacistPrescriptionService {

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private InventoryStockManager inventoryStockManager;

    @Autowired
    private StockCache stockCache;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> pendingList() {
        List<Prescription> list = prescriptionMapper.selectPendingList();

        if (list != null) {
            for (Prescription prescription : list) {
                parseImageUrls(prescription);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> review(Long prescriptionId, Long pharmacistId, int reviewStatus, String reviewRemark) {
        if (reviewStatus != 1 && reviewStatus != 2) {
            throw new BusinessException("审核状态必须为1(通过)或2(驳回)", 40001);
        }

        Prescription prescription = prescriptionMapper.selectById(prescriptionId);
        if (prescription == null) {
            throw new BusinessException("处方不存在", 40401, 404);
        }
        if (prescription.getReviewStatus() != null && prescription.getReviewStatus() != 0) {
            throw new BusinessException("该处方已被审核", 40001);
        }

        Prescription updatePrescription = new Prescription();
        updatePrescription.setPrescriptionId(prescriptionId);
        updatePrescription.setReviewStatus(reviewStatus);
        updatePrescription.setPharmacistId(pharmacistId);
        updatePrescription.setReviewRemark(reviewRemark);
        updatePrescription.setReviewedAt(LocalDateTime.now());
        prescriptionMapper.updateById(updatePrescription);

        if (prescription.getOrderId() != null) {
            if (reviewStatus == 1) {
                Order updateOrder = new Order();
                updateOrder.setOrderId(prescription.getOrderId());
                updateOrder.setOrderStatus(2);
                orderMapper.updateById(updateOrder);
            } else if (reviewStatus == 2) {
                QueryWrapper<OrderItem> itemWrapper = new QueryWrapper<>();
                itemWrapper.eq("order_id", prescription.getOrderId());
                List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

                if (items != null) {
                    for (OrderItem item : items) {
                        inventoryStockManager.unlock(item.getMedicineId(), item.getQuantity());
                        // 处方被驳回等同于订单作废：DB 解锁的同时归还 Redis 预减量
                        stockCache.rollback(item.getMedicineId(), item.getQuantity());
                    }
                }

                Order updateOrder = new Order();
                updateOrder.setOrderId(prescription.getOrderId());
                updateOrder.setOrderStatus(6);
                orderMapper.updateById(updateOrder);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("prescription_id", prescriptionId);
        result.put("review_status", reviewStatus);
        return result;
    }

    private void parseImageUrls(Prescription prescription) {
        String imageUrls = prescription.getImageUrls();
        if (imageUrls != null && !imageUrls.trim().isEmpty()) {
            try {
                List<String> urls = objectMapper.readValue(imageUrls, new TypeReference<List<String>>() {});
                prescription.setImageUrls(String.join(",", urls));
            } catch (Exception ignored) {}
        }
    }
}
