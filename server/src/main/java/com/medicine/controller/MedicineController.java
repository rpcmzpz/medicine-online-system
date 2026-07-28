package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.MedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/medicines")
public class MedicineController {

    @Autowired
    private MedicineService medicineService;

    @GetMapping
    public Result list(@RequestParam(required = false) String keyword,
                       @RequestParam(value = "category_id", required = false) Integer categoryId,
                       @RequestParam(value = "sort_by", required = false) String sortBy,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize,
                       @RequestParam(value = "drug_type", required = false) Integer drugType) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("category_id", categoryId);
        params.put("sort_by", sortBy);
        params.put("page", page);
        params.put("page_size", pageSize);
        params.put("drug_type", drugType);
        return medicineService.list(params);
    }

    @GetMapping("/categories")
    public Result categories() {
        return medicineService.categories();
    }

    @GetMapping("/{medicineId}")
    public Result detail(@PathVariable Long medicineId) {
        return medicineService.detail(medicineId);
    }
}
