package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.User;
import com.medicine.mapper.UserMapper;
import com.medicine.security.JwtUtil;
import com.medicine.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private long expiration;

    @Override
    public Map<String, Object> register(Map<String, Object> params) {
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        String phone = (String) params.get("phone");
        String email = (String) params.get("email");
        Integer userType = params.get("user_type") != null
                ? Integer.valueOf(params.get("user_type").toString()) : 1;
        String realName = (String) params.get("real_name");
        Integer age = params.get("age") != null
                ? Integer.valueOf(params.get("age").toString()) : null;

        if (username == null || username.length() < 4 || username.length() > 50) {
            throw new BusinessException("用户名长度必须在4-50个字符之间", 40001);
        }
        if (password == null || password.length() < 8 || password.length() > 32) {
            throw new BusinessException("密码长度必须在8-32个字符之间", 40001);
        }
        if (!Pattern.compile("[a-zA-Z]").matcher(password).find()
                || !Pattern.compile("[0-9]").matcher(password).find()) {
            throw new BusinessException("密码必须包含字母和数字", 40001);
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        if (userMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException("用户名已存在", 40002);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUserType(userType);
        user.setRealName(realName);
        user.setAge(age);
        user.setPhone(phone);
        user.setEmail(email);
        user.setMembershipLevel(0);
        user.setCreatedAt(LocalDateTime.now());

        userMapper.insert(user);

        Map<String, Object> result = new HashMap<>();
        result.put("user_id", user.getUserId());
        result.put("username", user.getUsername());
        return result;
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误", 40102);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误", 40102);
        }

        String accessToken = jwtUtil.generateToken(user.getUserId(), user.getUsername(), user.getUserType());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("user_id", user.getUserId());
        userInfo.put("username", user.getUsername());
        userInfo.put("user_type", user.getUserType());
        userInfo.put("real_name", user.getRealName());

        Map<String, Object> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        result.put("expires_in", expiration);
        result.put("user_info", userInfo);

        return result;
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            Long userId = Long.valueOf(jwtUtil.parseToken(refreshToken).get("userId").toString());

            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getUserId, userId);
            User user = userMapper.selectOne(queryWrapper);

            if (user == null) {
                throw new BusinessException("用户不存在", 40102);
            }

            String newAccessToken = jwtUtil.generateToken(user.getUserId(), user.getUsername(), user.getUserType());

            Map<String, Object> result = new HashMap<>();
            result.put("access_token", newAccessToken);
            result.put("expires_in", expiration);

            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("刷新令牌无效或已过期", 40101);
        }
    }
}
