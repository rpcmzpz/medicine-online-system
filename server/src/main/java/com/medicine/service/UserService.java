package com.medicine.service;

import java.util.Map;

public interface UserService {
    Map<String, Object> getProfile(Long userId);
    Map<String, Object> updateProfile(Long userId, Map<String, Object> params);
    Map<String, Object> addAddress(Long userId, Map<String, Object> params);
    Map<String, Object> getAddresses(Long userId);
    Map<String, Object> setDefaultAddress(Long userId, Long addressId);
    Map<String, Object> deleteAddress(Long userId, Long addressId);
}
