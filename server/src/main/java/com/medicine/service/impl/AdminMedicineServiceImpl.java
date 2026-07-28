package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.Inventory;
import com.medicine.entity.Medicine;
import com.medicine.mapper.InventoryMapper;
import com.medicine.mapper.MedicineMapper;
import com.medicine.service.AdminMedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminMedicineServiceImpl implements AdminMedicineService {

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Override
    public Map<String, Object> list(Map<String, Object> params) {
        int page = params.get("page") != null ? Integer.parseInt(params.get("page").toString()) : 1;
        int pageSize = params.get("page_size") != null ? Integer.parseInt(params.get("page_size").toString()) : 20;
        String keyword = (String) params.get("keyword");
        Integer categoryId = params.get("category_id") != null ? Integer.parseInt(params.get("category_id").toString()) : null;
        Integer status = params.get("status") != null ? Integer.parseInt(params.get("status").toString()) : null;

        QueryWrapper<Medicine> whereWrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            whereWrapper.and(w -> w.apply("m.name LIKE {0}", kw)
                    .or().apply("m.generic_name LIKE {0}", kw)
                    .or().apply("m.brand LIKE {0}", kw));
        }
        if (categoryId != null) {
            whereWrapper.apply("m.category_id = {0}", categoryId);
        }
        if (status != null) {
            whereWrapper.apply("m.status = {0}", status);
        }

        long total = medicineMapper.countWithInventory(whereWrapper);

        whereWrapper.orderByDesc("m.created_at");
        int offset = (page - 1) * pageSize;
        whereWrapper.last("LIMIT " + offset + "," + pageSize);
        List<Medicine> list = medicineMapper.selectWithInventory(whereWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> params) {
        Integer categoryId = (Integer) params.get("category_id");
        String name = (String) params.get("name");
        Integer drugType = (Integer) params.get("drug_type");
        Object priceObj = params.get("price");

        if (categoryId == null || name == null || drugType == null || priceObj == null) {
            throw new BusinessException("缺少必填参数：category_id, name, drug_type, price", 40001);
        }

        Medicine medicine = new Medicine();
        medicine.setCategoryId(categoryId);
        medicine.setName(name);
        medicine.setDrugType(drugType);
        medicine.setPrice(new BigDecimal(priceObj.toString()));
        medicine.setStatus(1);

        if (params.get("generic_name") != null) medicine.setGenericName((String) params.get("generic_name"));
        if (params.get("brand") != null) medicine.setBrand((String) params.get("brand"));
        if (params.get("specification") != null) medicine.setSpecification((String) params.get("specification"));
        if (params.get("approval_number") != null) medicine.setApprovalNumber((String) params.get("approval_number"));
        if (params.get("description") != null) medicine.setDescription((String) params.get("description"));
        if (params.get("image_url") != null) medicine.setImageUrl((String) params.get("image_url"));
        if (params.get("original_price") != null) medicine.setOriginalPrice(new BigDecimal(params.get("original_price").toString()));
        if (medicine.getOriginalPrice() == null) medicine.setOriginalPrice(medicine.getPrice());

        medicineMapper.insert(medicine);

        if (params.get("stock_quantity") != null) {
            int stockQuantity = Integer.parseInt(params.get("stock_quantity").toString());
            Integer alertThreshold = params.get("alert_threshold") != null ? Integer.parseInt(params.get("alert_threshold").toString()) : 10;
            Inventory inventory = new Inventory();
            inventory.setMedicineId(medicine.getMedicineId());
            inventory.setStockQuantity(stockQuantity);
            inventory.setAlertThreshold(alertThreshold);
            inventory.setLockedQuantity(0);
            inventoryMapper.insert(inventory);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("medicine_id", medicine.getMedicineId());
        return result;
    }

    @Override
    public void update(Long medicineId, Map<String, Object> params) {
        Medicine medicine = medicineMapper.selectById(medicineId);
        if (medicine == null) {
            throw new BusinessException("药品不存在", 40401, 404);
        }

        Medicine updateMedicine = new Medicine();
        updateMedicine.setMedicineId(medicineId);

        if (params.get("category_id") != null) updateMedicine.setCategoryId(Integer.parseInt(params.get("category_id").toString()));
        if (params.get("name") != null) updateMedicine.setName((String) params.get("name"));
        if (params.get("generic_name") != null) updateMedicine.setGenericName((String) params.get("generic_name"));
        if (params.get("brand") != null) updateMedicine.setBrand((String) params.get("brand"));
        if (params.get("specification") != null) updateMedicine.setSpecification((String) params.get("specification"));
        if (params.get("approval_number") != null) updateMedicine.setApprovalNumber((String) params.get("approval_number"));
        if (params.get("drug_type") != null) updateMedicine.setDrugType(Integer.parseInt(params.get("drug_type").toString()));
        if (params.get("description") != null) updateMedicine.setDescription((String) params.get("description"));
        if (params.get("image_url") != null) updateMedicine.setImageUrl((String) params.get("image_url"));
        if (params.get("price") != null) updateMedicine.setPrice(new BigDecimal(params.get("price").toString()));
        if (params.get("original_price") != null) updateMedicine.setOriginalPrice(new BigDecimal(params.get("original_price").toString()));

        medicineMapper.updateById(updateMedicine);
    }

    @Override
    public void toggleStatus(Long medicineId, int status) {
        Medicine medicine = medicineMapper.selectById(medicineId);
        if (medicine == null) {
            throw new BusinessException("药品不存在", 40401, 404);
        }

        Medicine updateMedicine = new Medicine();
        updateMedicine.setMedicineId(medicineId);
        updateMedicine.setStatus(status);
        medicineMapper.updateById(updateMedicine);
    }

    @Override
    public void updateInventory(Long medicineId, int stockQuantity, Integer alertThreshold) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("medicine_id", medicineId);
        Inventory inventory = inventoryMapper.selectOne(wrapper);

        if (inventory == null) {
            inventory = new Inventory();
            inventory.setMedicineId(medicineId);
            inventory.setStockQuantity(stockQuantity);
            inventory.setAlertThreshold(alertThreshold != null ? alertThreshold : 10);
            inventory.setLockedQuantity(0);
            inventoryMapper.insert(inventory);
        } else {
            inventory.setStockQuantity(stockQuantity);
            if (alertThreshold != null) {
                inventory.setAlertThreshold(alertThreshold);
            }
            inventoryMapper.updateById(inventory);
        }
    }
}
