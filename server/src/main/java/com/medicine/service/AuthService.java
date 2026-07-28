package com.medicine.service;

import java.util.Map;

public interface AuthService {
    Map<String, Object> register(Map<String, Object> params);
    Map<String, Object> login(String username, String password);
    Map<String, Object> refreshToken(String refreshToken);
}
