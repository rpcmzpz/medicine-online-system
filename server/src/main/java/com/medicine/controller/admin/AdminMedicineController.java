package com.medicine.controller.admin;

import com.medicine.common.Result;
import com.medicine.service.AdminMedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/medicines")
public class AdminMedicineController {

    @Autowired
    private AdminMedicineService adminMedicineService;

    @GetMapping
    public Result list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer category_id,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int page_size) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("category_id", category_id);
        params.put("status", status);
        params.put("page", page);
        params.put("page_size", page_size);
        return Result.success(adminMedicineService.list(params));
    }

    @PostMapping
    public Result create(@RequestBody Map<String, Object> body) {
        return Result.created(adminMedicineService.create(body), "创建成功");
    }

    @PutMapping("/{medicineId}")
    public Result update(@PathVariable Long medicineId, @RequestBody Map<String, Object> body) {
        adminMedicineService.update(medicineId, body);
        return Result.success(null, "更新成功");
    }

    @PatchMapping("/{medicineId}/status")
    public Result toggleStatus(@PathVariable Long medicineId, @RequestBody Map<String, Object> body) {
        int status = Integer.parseInt(body.get("status").toString());
        adminMedicineService.toggleStatus(medicineId, status);
        return Result.success(null, "状态更新成功");
    }

    @PatchMapping("/{medicineId}/inventory")
    public Result updateInventory(@PathVariable Long medicineId, @RequestBody Map<String, Object> body) {
        int stockQuantity = Integer.parseInt(body.get("stock_quantity").toString());
        Integer alertThreshold = body.get("alert_threshold") != null ? Integer.parseInt(body.get("alert_threshold").toString()) : null;
        adminMedicineService.updateInventory(medicineId, stockQuantity, alertThreshold);
        return Result.success(null, "库存更新成功");
    }
}
