package com.medicine.service;

import com.medicine.cache.StockCache;
import com.medicine.common.BusinessException;
import com.medicine.entity.Address;
import com.medicine.entity.CartItem;
import com.medicine.mapper.*;
import com.medicine.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 下单链路上「Redis 预减 + DB 兜底」的配合行为：
 * 预减判定不足要快速失败，DB 阶段失败要把预减量补偿回去。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private CartItemMapper cartItemMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private InventoryMapper inventoryMapper;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private PrescriptionMapper prescriptionMapper;
    @Mock
    private StockCache stockCache;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Map<String, Object> params() {
        Map<String, Object> params = new HashMap<>();
        params.put("cart_item_ids", Collections.singletonList(1));
        params.put("address_id", 1L);
        return params;
    }

    private List<CartItem> cartItems() {
        CartItem item = new CartItem();
        item.setMedicineId(1L);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("18.50"));
        item.setMedicineName("布洛芬缓释胶囊");
        item.setDrugType(0);
        return Collections.singletonList(item);
    }

    private void stubCommon() {
        when(addressMapper.selectOne(any())).thenReturn(new Address());
        when(cartItemMapper.selectByIds(anyList(), eq(USER_ID))).thenReturn(cartItems());
    }

    @Test
    @DisplayName("Redis 预减判定库存不足时快速失败：不落订单，也不产生补偿动作")
    void create_redisSaysNotEnough_shouldFailFast() {
        stubCommon();
        when(stockCache.tryPreDeduct(1L, 2)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.create(params(), USER_ID));

        assertEquals(40002, exception.getCode());
        // 连 DB 都不该查，更不该落单
        verify(inventoryMapper, never()).selectOne(any());
        verify(orderMapper, never()).insert(any());
        verify(stockCache, never()).rollback(any(), anyInt());
    }

    @Test
    @DisplayName("Redis 预减通过但 DB 校验失败时，必须把预减掉的库存归还")
    void create_dbCheckFails_shouldRollbackRedis() {
        stubCommon();
        when(stockCache.tryPreDeduct(1L, 2)).thenReturn(true);
        // 库存记录缺失 → DB 阶段抛异常
        when(inventoryMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> orderService.create(params(), USER_ID));

        // 预减成功过，所以失败时必须显式补偿，否则可售库存会凭空减少
        verify(stockCache, times(1)).rollback(1L, 2);
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Redis 未预热（降级放行）时，库存不足由 DB 校验拦下")
    void create_redisDegraded_shouldStillBeBlockedByDb() {
        stubCommon();
        when(stockCache.tryPreDeduct(1L, 2)).thenReturn(true);

        com.medicine.entity.Inventory inventory = new com.medicine.entity.Inventory();
        inventory.setMedicineId(1L);
        inventory.setStockQuantity(10);
        inventory.setLockedQuantity(9);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.create(params(), USER_ID));

        assertEquals(40002, exception.getCode());
        verify(inventoryMapper, never()).lockStock(any(), anyInt());
        verify(stockCache, times(1)).rollback(1L, 2);
    }
}
