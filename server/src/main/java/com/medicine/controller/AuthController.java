package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Result register(@RequestBody Map<String, Object> params) {
        Map<String, Object> data = authService.register(params);
        return Result.created(data, "注册成功");
    }

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, Object> params) {
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        Map<String, Object> data = authService.login(username, password);
        return Result.success(data, "登录成功");
    }

    @PostMapping("/refresh")
    public Result refresh(@RequestBody Map<String, Object> params) {
        String refreshToken = (String) params.get("refresh_token");
        Map<String, Object> data = authService.refreshToken(refreshToken);
        return Result.success(data);
    }
}
