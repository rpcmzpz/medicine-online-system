package com.medicine.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("cart_items")
public class CartItem {
    @TableId(type = IdType.AUTO)
    private Long cartId;
    private Long userId;
    private Long medicineId;
    private Integer quantity;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    // 关联字段
    @TableField(exist = false)
    private String medicineName;
    @TableField(exist = false)
    private java.math.BigDecimal price;
    @TableField(exist = false)
    private Integer drugType;
    @TableField(exist = false)
    private Integer availableStock;
    @TableField(exist = false)
    private String imageUrl;

    public Long getCartId() {
        return cartId;
    }

    public void setCartId(Long cartId) {
        this.cartId = cartId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(Long medicineId) {
        this.medicineId = medicineId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public void setPrice(java.math.BigDecimal price) {
        this.price = price;
    }

    public Integer getDrugType() {
        return drugType;
    }

    public void setDrugType(Integer drugType) {
        this.drugType = drugType;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
