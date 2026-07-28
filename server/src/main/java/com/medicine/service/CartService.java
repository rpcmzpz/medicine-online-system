package com.medicine.service;

import com.medicine.common.Result;

public interface CartService {
    Result add(Long userId, Long medicineId, int quantity);
    Result list(Long userId);
    Result updateQuantity(Long userId, Long cartId, int quantity);
    Result remove(Long userId, Long cartId);
}
