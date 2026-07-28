package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicine.common.BusinessException;
import com.medicine.common.Result;
import com.medicine.entity.CartItem;
import com.medicine.entity.Medicine;
import com.medicine.mapper.CartItemMapper;
import com.medicine.mapper.MedicineMapper;
import com.medicine.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @Override
    public Result add(Long userId, Long medicineId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("数量必须大于0", 40001);
        }

        Medicine medicine = medicineMapper.selectById(medicineId);
        if (medicine == null || medicine.getStatus() != 1) {
            throw new BusinessException("药品不存在或已下架", 40401);
        }

        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId)
                .eq(CartItem::getMedicineId, medicineId);
        CartItem existingItem = cartItemMapper.selectOne(wrapper);

        Medicine detail = medicineMapper.selectDetailById(medicineId);
        int availableStock = detail != null && detail.getAvailableStock() != null
                ? detail.getAvailableStock() : 0;

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + quantity;
            if (newQuantity > availableStock) {
                throw new BusinessException("库存不足，最多可添加" + availableStock + "件", 40002);
            }
            existingItem.setQuantity(newQuantity);
            cartItemMapper.updateById(existingItem);
            return Result.created(null, "已更新购物车数量");
        }

        if (quantity > availableStock) {
            throw new BusinessException("库存不足，最多可添加" + availableStock + "件", 40002);
        }

        CartItem cartItem = new CartItem();
        cartItem.setUserId(userId);
        cartItem.setMedicineId(medicineId);
        cartItem.setQuantity(quantity);
        cartItemMapper.insert(cartItem);

        return Result.created(null, "已添加到购物车");
    }

    @Override
    public Result list(Long userId) {
        List<CartItem> items = cartItemMapper.selectByUserId(userId);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> list = new ArrayList<>();

        for (CartItem item : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            BigDecimal price = item.getPrice();
            int quantity = item.getQuantity();
            BigDecimal subtotal = price != null
                    ? price.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;

            map.put("cart_id", item.getCartId());
            map.put("medicine_id", item.getMedicineId());
            map.put("medicine_name", item.getMedicineName());
            map.put("image_url", item.getImageUrl());
            map.put("price", price);
            map.put("quantity", quantity);
            map.put("subtotal", subtotal);
            map.put("drug_type", item.getDrugType());
            map.put("stock_available", item.getAvailableStock() != null
                    ? item.getAvailableStock() : 0);
            map.put("max_quantity", item.getAvailableStock() != null
                    ? item.getAvailableStock() : 0);

            list.add(map);
            totalAmount = totalAmount.add(subtotal);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total_amount", totalAmount);
        result.put("total_count", items.size());
        return Result.success(result);
    }

    @Override
    public Result updateQuantity(Long userId, Long cartId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("数量必须大于0", 40001);
        }

        CartItem cartItem = cartItemMapper.selectById(cartId);
        if (cartItem == null || !cartItem.getUserId().equals(userId)) {
            throw new BusinessException("购物车项不存在", 40402);
        }

        Medicine medicine = medicineMapper.selectDetailById(cartItem.getMedicineId());
        int availableStock = medicine != null && medicine.getAvailableStock() != null
                ? medicine.getAvailableStock() : 0;

        if (quantity > availableStock) {
            throw new BusinessException("库存不足，最多可购买" + availableStock + "件", 40002);
        }

        cartItem.setQuantity(quantity);
        cartItemMapper.updateById(cartItem);

        return Result.success(null, "更新成功");
    }

    @Override
    public Result remove(Long userId, Long cartId) {
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getCartId, cartId)
                .eq(CartItem::getUserId, userId);
        int deleted = cartItemMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException("购物车项不存在", 40402);
        }
        return Result.success(null, "已移除");
    }
}
