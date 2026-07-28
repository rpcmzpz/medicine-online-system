package com.medicine.service.impl;

import com.medicine.common.BusinessException;
import com.medicine.common.Result;
import com.medicine.entity.Category;
import com.medicine.entity.Medicine;
import com.medicine.entity.Review;
import com.medicine.mapper.CategoryMapper;
import com.medicine.mapper.MedicineMapper;
import com.medicine.mapper.ReviewMapper;
import com.medicine.service.MedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MedicineServiceImpl implements MedicineService {

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Override
    public Result list(Map<String, Object> params) {
        String keyword = (String) params.get("keyword");
        Integer categoryId = params.get("category_id") != null
                ? Integer.valueOf(params.get("category_id").toString()) : null;
        String sortBy = (String) params.get("sort_by");
        int page = params.get("page") != null
                ? Integer.parseInt(params.get("page").toString()) : 1;
        int pageSize = params.get("page_size") != null
                ? Integer.parseInt(params.get("page_size").toString()) : 20;
        Integer drugType = params.get("drug_type") != null
                ? Integer.valueOf(params.get("drug_type").toString()) : null;

        List<Integer> categoryIds = null;
        if (categoryId != null) {
            List<Category> children = categoryMapper.selectByParentId(categoryId);
            if (children != null && !children.isEmpty()) {
                categoryIds = new ArrayList<>();
                categoryIds.add(categoryId);
                for (Category child : children) {
                    categoryIds.add(child.getCategoryId());
                }
            } else {
                categoryIds = Collections.singletonList(categoryId);
            }
        }

        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> rows = medicineMapper.selectListWithStock(
                keyword, categoryIds, drugType, sortBy, offset, pageSize);
        int total = medicineMapper.countListWithStock(keyword, categoryIds, drugType);

        List<Map<String, Object>> list = rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("medicine_id", row.get("medicine_id"));
            item.put("name", row.get("name"));
            item.put("generic_name", row.get("generic_name"));
            item.put("brand", row.get("brand"));
            item.put("specification", row.get("specification"));
            item.put("image_url", row.get("image_url"));
            item.put("price", row.get("price"));
            item.put("original_price", row.get("original_price"));
            item.put("sales_count", row.get("sales_count"));
            item.put("drug_type", row.get("drug_type"));
            item.put("drug_type_label", getDrugTypeLabel(row.get("drug_type")));
            item.put("stock_available", row.get("available_stock"));
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return Result.success(result);
    }

    @Override
    public Result detail(Long medicineId) {
        Medicine medicine = medicineMapper.selectDetailById(medicineId);
        if (medicine == null) {
            throw new BusinessException("药品不存在", 40400, 404);
        }

        List<Review> reviews = reviewMapper.selectByMedicineId(medicineId);
        Map<String, Object> avgData = medicineMapper.selectAvgRating(medicineId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("medicine_id", medicine.getMedicineId());
        data.put("name", medicine.getName());
        data.put("generic_name", medicine.getGenericName());
        data.put("brand", medicine.getBrand());
        data.put("specification", medicine.getSpecification());
        data.put("approval_number", medicine.getApprovalNumber());
        data.put("drug_type", medicine.getDrugType());
        data.put("drug_type_label", getDrugTypeLabel(medicine.getDrugType()));
        data.put("description", medicine.getDescription());
        data.put("price", medicine.getPrice());
        data.put("original_price", medicine.getOriginalPrice());
        data.put("stock_quantity", medicine.getStockQuantity());

        Map<String, Object> categoryMap = new LinkedHashMap<>();
        categoryMap.put("category_id", medicine.getCategoryId());
        categoryMap.put("name", medicine.getCategoryName());
        data.put("category", categoryMap);

        List<Map<String, Object>> reviewList = reviews.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("user_name", maskUsername(r.getUsername()));
            map.put("rating", r.getRating());
            map.put("content", r.getContent());
            map.put("created_at", r.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        data.put("reviews", reviewList);
        data.put("reviews_count", reviews.size());

        if (avgData != null && avgData.get("avg_rating") != null) {
            data.put("avg_rating", avgData.get("avg_rating"));
        } else {
            data.put("avg_rating", null);
        }

        return Result.success(data);
    }

    @Override
    public Result categories() {
        List<Category> allCategories = categoryMapper.selectAllOrdered();

        Map<Integer, Category> categoryMap = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category c : allCategories) {
            categoryMap.put(c.getCategoryId(), c);
            if (c.getParentId() == null || c.getParentId() == 0) {
                roots.add(c);
            }
        }

        for (Category c : allCategories) {
            if (c.getParentId() != null && c.getParentId() != 0) {
                Category parent = categoryMap.get(c.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(c);
                }
            }
        }

        List<Map<String, Object>> treeRoots = roots.stream()
                .map(this::categoryToMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", treeRoots);
        return Result.success(result);
    }

    private Map<String, Object> categoryToMap(Category cat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("category_id", cat.getCategoryId());
        map.put("parent_id", cat.getParentId());
        map.put("name", cat.getName());
        map.put("sort_order", cat.getSortOrder());
        if (cat.getChildren() != null && !cat.getChildren().isEmpty()) {
            List<Map<String, Object>> childMaps = cat.getChildren().stream()
                    .map(this::categoryToMap)
                    .collect(Collectors.toList());
            map.put("children", childMaps);
        }
        return map;
    }

    private String getDrugTypeLabel(Object drugType) {
        if (drugType == null) {
            return "";
        }
        int type;
        if (drugType instanceof Integer) {
            type = (Integer) drugType;
        } else {
            type = Integer.parseInt(drugType.toString());
        }
        switch (type) {
            case 0:
                return "OTC";
            case 1:
                return "处方药";
            default:
                return "其他";
        }
    }

    private String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "***";
        }
        return username.charAt(0) + "**";
    }
}
