package com.medicine.controller;

import com.medicine.common.Result;
import com.medicine.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @PostMapping("/items")
    public Result add(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long medicineId = Long.valueOf(body.get("medicine_id").toString());
        int quantity = body.containsKey("quantity") ? Integer.parseInt(body.get("quantity").toString()) : 1;
        return cartService.add(userId, medicineId, quantity);
    }

    @GetMapping("/items")
    public Result list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return cartService.list(userId);
    }

    @PatchMapping("/items/{cartId}")
    public Result updateQuantity(HttpServletRequest request,
                                  @PathVariable Long cartId,
                                  @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        int quantity = Integer.parseInt(body.get("quantity").toString());
        return cartService.updateQuantity(userId, cartId, quantity);
    }

    @DeleteMapping("/items/{cartId}")
    public Result remove(HttpServletRequest request, @PathVariable Long cartId) {
        Long userId = (Long) request.getAttribute("userId");
        return cartService.remove(userId, cartId);
    }
}
