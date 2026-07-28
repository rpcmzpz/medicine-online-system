package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.entity.Address;
import com.medicine.entity.User;
import com.medicine.mapper.AddressMapper;
import com.medicine.mapper.UserMapper;
import com.medicine.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Override
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在", 40401, 404);
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("user_id", user.getUserId());
        profile.put("username", user.getUsername());
        profile.put("user_type", user.getUserType());
        profile.put("real_name", user.getRealName());
        profile.put("age", user.getAge());
        profile.put("phone", maskPhone(user.getPhone()));
        profile.put("email", maskEmail(user.getEmail()));
        profile.put("membership_level", user.getMembershipLevel());
        profile.put("created_at", user.getCreatedAt());

        return profile;
    }

    @Override
    @Transactional
    public Map<String, Object> updateProfile(Long userId, Map<String, Object> params) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在", 40401, 404);
        }

        if (params.containsKey("real_name")) {
            user.setRealName((String) params.get("real_name"));
        }
        if (params.containsKey("age")) {
            user.setAge(params.get("age") != null
                    ? Integer.valueOf(params.get("age").toString()) : null);
        }
        if (params.containsKey("phone")) {
            user.setPhone((String) params.get("phone"));
        }
        if (params.containsKey("email")) {
            user.setEmail((String) params.get("email"));
        }

        userMapper.updateById(user);

        Map<String, Object> profile = new HashMap<>();
        profile.put("user_id", user.getUserId());
        profile.put("real_name", user.getRealName());
        profile.put("age", user.getAge());
        profile.put("phone", user.getPhone());
        profile.put("email", user.getEmail());

        return profile;
    }

    @Override
    @Transactional
    public Map<String, Object> addAddress(Long userId, Map<String, Object> params) {
        LambdaQueryWrapper<Address> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Address::getUserId, userId);
        if (addressMapper.selectCount(countWrapper) >= 20) {
            throw new BusinessException("地址数量已达上限（20个）", 40001);
        }

        Boolean isDefault = params.get("is_default") != null
                && (Boolean) params.get("is_default");

        if (isDefault) {
            addressMapper.clearDefault(userId);
        }

        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName((String) params.get("receiver_name"));
        address.setPhone((String) params.get("phone"));
        address.setProvince((String) params.get("province"));
        address.setCity((String) params.get("city"));
        address.setDistrict((String) params.get("district"));
        address.setDetail((String) params.get("detail"));
        address.setIsDefault(isDefault ? 1 : 0);
        address.setCreatedAt(LocalDateTime.now());

        addressMapper.insert(address);

        Map<String, Object> result = new HashMap<>();
        result.put("address_id", address.getAddressId());
        result.put("receiver_name", address.getReceiverName());
        result.put("phone", address.getPhone());
        result.put("province", address.getProvince());
        result.put("city", address.getCity());
        result.put("district", address.getDistrict());
        result.put("detail", address.getDetail());
        result.put("is_default", address.getIsDefault());

        return result;
    }

    @Override
    public Map<String, Object> getAddresses(Long userId) {
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreatedAt);
        List<Address> addresses = addressMapper.selectList(queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", addresses);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> setDefaultAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在", 40402, 404);
        }

        addressMapper.clearDefault(userId);
        addressMapper.setDefault(addressId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("address_id", addressId);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> deleteAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在", 40402, 404);
        }

        addressMapper.deleteById(addressId);

        Map<String, Object> result = new HashMap<>();
        result.put("address_id", addressId);
        return result;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }
}
