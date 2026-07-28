package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    @PostMapping
    public Result create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        reminderService.create(userId, body);
        return Result.created(null, "创建成功");
    }

    @GetMapping
    public Result list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> list = reminderService.list(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return Result.success(result);
    }

    @PutMapping("/{reminderId}")
    public Result update(@PathVariable Long reminderId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        reminderService.update(userId, reminderId, body);
        return Result.success(null, "更新成功");
    }

    @DeleteMapping("/{reminderId}")
    public Result remove(@PathVariable Long reminderId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        reminderService.remove(userId, reminderId);
        return Result.success(null, "删除成功");
    }
}
