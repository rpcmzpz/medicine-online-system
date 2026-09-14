package com.medicine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.cache.StockCache;
import com.medicine.common.BusinessException;
import com.medicine.common.Result;
import com.medicine.entity.*;
import com.medicine.mapper.*;
import com.medicine.service.OrderService;
import com.medicine.stock.InventoryStockManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private AddressMapper addressMapper;
    @Autowired
    private InventoryStockManager inventoryStockManager;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private DeliveryMapper deliveryMapper;
    @Autowired
    private PrescriptionMapper prescriptionMapper;
    @Autowired
    private StockCache stockCache;

    private static final String[] ORDER_STATUS_LABELS = {"待支付", "待审核", "待配药", "待配送", "配送中", "已完成", "已取消", "已退款"};

    @Override
    @Transactional
    public Result create(Map<String, Object> params, Long userId) {
        @SuppressWarnings("unchecked")
        List<Integer> cartItemIdList = (List<Integer>) params.get("cart_item_ids");
        if (cartItemIdList == null || cartItemIdList.isEmpty()) {
            throw new BusinessException("购物车不能为空", 40001);
        }
        Long addressId = params.get("address_id") != null
                ? Long.valueOf(params.get("address_id").toString()) : null;
        if (addressId == null) {
            throw new BusinessException("收货地址不能为空", 40001);
        }
        Address address = addressMapper.selectOne(
                new QueryWrapper<Address>().eq("address_id", addressId).eq("user_id", userId));
        if (address == null) {
            throw new BusinessException("收货地址不存在", 40001);
        }
        String remark = params.get("remark") != null ? params.get("remark").toString() : null;

        List<CartItem> cartItems = cartItemMapper.selectByIds(cartItemIdList, userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BusinessException("购物车商品不存在或已下架", 40001);
        }

        // 库存校验 + 锁定：Redis 预减挡并发，MySQL 乐观锁兜底
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean hasPrescription = false;
        // 记录已预减成功的条目：后续任何一步失败都要把 Redis 上扣掉的量补回去
        List<CartItem> preDeducted = new ArrayList<>();
        try {
            for (CartItem item : cartItems) {
                // ① Redis 原子预减（Lua 一次执行「查-判-减」）。
                //    返回 false 说明 Redis 上可售库存已不足，直接快速失败，不再压数据库；
                //    未预热或 Redis 故障时返回 true，自动降级为纯 DB 校验，不影响下单可用性。
                if (!stockCache.tryPreDeduct(item.getMedicineId(), item.getQuantity())) {
                    throw new BusinessException("药品库存不足: " + item.getMedicineId(), 40002);
                }
                preDeducted.add(item);

                // ② DB 复核并锁定：真值仍在 MySQL，用 version 乐观锁做 CAS 更新，
                //    SQL 的 WHERE 里同时带「可售量足够」，即使上面的预减被并发穿透也不会超卖。
                if (!inventoryStockManager.lock(item.getMedicineId(), item.getQuantity())) {
                    throw new BusinessException("药品库存不足: " + item.getMedicineId(), 40002);
                }
                totalAmount = totalAmount.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                if (item.getDrugType() != null && item.getDrugType() == 1) {
                    hasPrescription = true;
                }
            }

            // 生成订单编号
            String orderNo = "MED" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                    + randomHex(4).toUpperCase();

            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setAddressId(addressId);
            order.setTotalAmount(totalAmount);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setActualAmount(totalAmount);
            order.setOrderStatus(hasPrescription ? 1 : 0);
            order.setHasPrescription(hasPrescription ? 1 : 0);
            order.setRemark(remark);
            order.setVersion(0);
            orderMapper.insert(order);

            // 写入订单明细
            for (CartItem item : cartItems) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(order.getOrderId());
                orderItem.setMedicineId(item.getMedicineId());
                orderItem.setMedicineName(item.getMedicineName());
                orderItem.setPrice(item.getPrice());
                orderItem.setQuantity(item.getQuantity());
                orderItem.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                orderItemMapper.insert(orderItem);
            }

            // 清空购物车已下单商品
            cartItemMapper.delete(
                    new QueryWrapper<CartItem>().in("cart_id", cartItemIdList).eq("user_id", userId));

            // 处方药订单关联处方
            @SuppressWarnings("unchecked")
            List<Integer> prescriptionIdList = (List<Integer>) params.get("prescription_ids");
            if (hasPrescription && prescriptionIdList != null && !prescriptionIdList.isEmpty()) {
                for (Integer pid : prescriptionIdList) {
                    Prescription prescription = new Prescription();
                    prescription.setPrescriptionId(Long.valueOf(pid));
                    prescription.setOrderId(order.getOrderId());
                    prescriptionMapper.updateById(prescription);
                }
            }

            // 组装响应
            Map<String, Object> result = new HashMap<>();
            result.put("order_id", order.getOrderId());
            result.put("order_no", order.getOrderNo());
            result.put("total_amount", order.getTotalAmount());
            result.put("actual_amount", order.getActualAmount());
            result.put("order_status", order.getOrderStatus());
            result.put("has_prescription", order.getHasPrescription());
            result.put("items", cartItems.size());
            return Result.created(result, "订单创建成功");
        } catch (RuntimeException e) {
            // @Transactional 只回滚数据库；Redis 不是事务资源，预减必须在这里显式补偿，
            // 否则一次失败的下单会让可售库存凭空减少（少卖）。
            for (CartItem item : preDeducted) {
                stockCache.rollback(item.getMedicineId(), item.getQuantity());
            }
            throw e;
        }
    }

    @Override
    public Result list(Map<String, Object> params, Long userId) {
        Integer status = params.get("status") != null
                ? Integer.valueOf(params.get("status").toString()) : null;
        int page = params.get("page") != null
                ? Integer.parseInt(params.get("page").toString()) : 1;
        int pageSize = params.get("page_size") != null
                ? Integer.parseInt(params.get("page_size").toString()) : 20;

        // 分页条件查询
        QueryWrapper<Order> whereWrapper = new QueryWrapper<>();
        whereWrapper.eq("o.user_id", userId);
        if (status != null) {
            whereWrapper.eq("o.order_status", status);
        }

        long total = orderMapper.selectCountWithUser(whereWrapper);

        // 分页查询 + 排序
        whereWrapper.orderByDesc("o.created_at");
        whereWrapper.last("LIMIT " + ((page - 1) * pageSize) + ", " + pageSize);
        List<Order> orders = orderMapper.selectWithUser(whereWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", orders);
        result.put("total", total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return Result.success(result);
    }

    @Override
    public Result detail(Long orderId, Long userId) {
        Order order = orderMapper.selectOne(
                new QueryWrapper<Order>().eq("order_id", orderId).eq("user_id", userId));
        if (order == null) {
            throw new BusinessException("订单不存在", 40400, 404);
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        Address address = addressMapper.selectById(order.getAddressId());
        Payment payment = paymentMapper.selectOne(
                new QueryWrapper<Payment>().eq("order_id", orderId));
        Delivery delivery = deliveryMapper.selectOne(
                new QueryWrapper<Delivery>().eq("order_id", orderId));

        // 组装订单详情（含明细/地址/支付/配送）
        Map<String, Object> result = new HashMap<>();
        result.put("order_id", order.getOrderId());
        result.put("order_no", order.getOrderNo());
        result.put("order_status", order.getOrderStatus());
        Integer status = order.getOrderStatus();
        result.put("order_status_label", status != null && status >= 0 && status < ORDER_STATUS_LABELS.length
                ? ORDER_STATUS_LABELS[status] : "未知");
        result.put("total_amount", order.getTotalAmount());
        result.put("discount_amount", order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO);
        result.put("actual_amount", order.getActualAmount());
        result.put("has_prescription", order.getHasPrescription());
        result.put("remark", order.getRemark());
        result.put("created_at", order.getCreatedAt());
        result.put("paid_at", order.getPaidAt());
        result.put("completed_at", order.getCompletedAt());

        // 商品明细
        result.put("items", items != null ? items.stream().map(item -> {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("medicine_name", item.getMedicineName());
            itemMap.put("price", item.getPrice());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("subtotal", item.getSubtotal());
            return itemMap;
        }).collect(Collectors.toList()) : Collections.emptyList());

        // 收货地址
        if (address != null) {
            Map<String, Object> addrMap = new HashMap<>();
            addrMap.put("receiver_name", address.getReceiverName());
            addrMap.put("phone", address.getPhone());
            addrMap.put("detail", address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail());
            result.put("address", addrMap);
        } else {
            result.put("address", null);
        }

        // 支付信息
        if (payment != null) {
            Map<String, Object> payMap = new HashMap<>();
            payMap.put("payment_method_label", new String[]{"微信", "支付宝", "银联"}[payment.getPaymentMethod()]);
            payMap.put("payment_status_label", new String[]{"处理中", "成功", "失败", "已退款"}[payment.getPaymentStatus()]);
            result.put("payment", payMap);
        } else {
            result.put("payment", null);
        }

        // 配送信息
        if (delivery != null) {
            Map<String, Object> delMap = new HashMap<>();
            delMap.put("delivery_status_label", new String[]{"待接单", "已接单", "配送中", "已送达"}[delivery.getDeliveryStatus()]);
            result.put("delivery", delMap);
        } else {
            result.put("delivery", null);
        }

        return Result.success(result);
    }

    @Override
    @Transactional
    public Result cancel(Long orderId, Long userId) {
        Order order = orderMapper.selectOne(
                new QueryWrapper<Order>().eq("order_id", orderId).eq("user_id", userId));
        if (order == null) {
            throw new BusinessException("订单不存在", 40001);
        }
        if (order.getOrderStatus() != 0) {
            throw new BusinessException("仅待支付订单可取消", 40002);
        }
        order.setOrderStatus(6);
        int affected = orderMapper.updateStatusOptimistic(orderId, 6, order.getVersion());
        if (affected == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试", 40003);
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : items) {
            inventoryStockManager.unlock(item.getMedicineId(), item.getQuantity());
            // DB 解锁的同时归还 Redis 预减量，保证两边「可售库存」始终一致
            stockCache.rollback(item.getMedicineId(), item.getQuantity());
        }
        return Result.success(null, "订单已取消");
    }

    @Override
    @Transactional
    public Result pay(Long orderId, Long userId, int paymentMethod) {
        Order order = orderMapper.selectOne(
                new QueryWrapper<Order>().eq("order_id", orderId).eq("user_id", userId));
        if (order == null) {
            throw new BusinessException("订单不存在", 40001);
        }
        if (order.getOrderStatus() != 0 && order.getOrderStatus() != 1) {
            throw new BusinessException("该订单状态不可支付", 40002);
        }

        String transactionNo = "TXN" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                + randomHex(6).toUpperCase();
        int newStatus = (order.getHasPrescription() != null && order.getHasPrescription() == 1) ? 1 : 2;

        // 更新订单状态
        order.setOrderStatus(newStatus);
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 写入支付记录
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTransactionNo(transactionNo);
        payment.setAmount(order.getActualAmount() != null ? order.getActualAmount() : order.getTotalAmount());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(1);
        paymentMapper.insert(payment);

        // 扣减库存：总库存和锁定库存同减，可售量不变，所以 Redis 预减计数这里不需要再动
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : items) {
            inventoryStockManager.deduct(item.getMedicineId(), item.getQuantity());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("payment_id", payment.getPaymentId());
        result.put("transaction_no", transactionNo);
        result.put("amount", payment.getAmount());
        result.put("order_status", newStatus);
        return Result.success(result, "支付成功");
    }

    @Override
    public Result confirm(Long orderId, Long userId) {
        Order order = orderMapper.selectOne(
                new QueryWrapper<Order>().eq("order_id", orderId).eq("user_id", userId));
        if (order == null) {
            throw new BusinessException("订单不存在", 40001);
        }
        if (order.getOrderStatus() != 3 && order.getOrderStatus() != 4) {
            throw new BusinessException("该订单状态不可确认收货", 40002);
        }
        order.setOrderStatus(5);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateStatusOptimistic(orderId, 5, order.getVersion());
        return Result.success(null, "已确认收货");
    }

    private String randomHex(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
