package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public Result getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = userService.getProfile(userId);
        return Result.success(data);
    }

    @PutMapping("/profile")
    public Result updateProfile(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = userService.updateProfile(userId, params);
        return Result.success(data, "更新成功");
    }

    @PostMapping("/addresses")
    public Result addAddress(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = userService.addAddress(userId, params);
        return Result.created(data, "添加成功");
    }

    @GetMapping("/addresses")
    public Result getAddresses(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = userService.getAddresses(userId);
        return Result.success(data);
    }

    @PatchMapping("/addresses/{addressId}/default")
    public Result setDefaultAddress(HttpServletRequest request, @PathVariable Long addressId) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = userService.setDefaultAddress(userId, addressId);
        return Result.success(data);
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result deleteAddress(HttpServletRequest request, @PathVariable Long addressId) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = userService.deleteAddress(userId, addressId);
        return Result.success(data);
    }
}
