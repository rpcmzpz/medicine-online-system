package com.medicine.service;

import com.medicine.cache.StockCache;
import com.medicine.common.BusinessException;
import com.medicine.entity.Address;
import com.medicine.entity.CartItem;
import com.medicine.mapper.*;
import com.medicine.service.impl.OrderServiceImpl;
import com.medicine.stock.InventoryStockManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 下单链路上「Redis 预减 + DB 乐观锁」的配合行为：
 * 预减判定不足要快速失败；一旦预减成功过，后续任何环节失败都必须把预减量补偿回去。
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
    private PaymentMapper paymentMapper;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private PrescriptionMapper prescriptionMapper;
    @Mock
    private StockCache stockCache;
    @Mock
    private InventoryStockManager inventoryStockManager;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Map<String, Object> params() {
        Map<String, Object> params = new HashMap<>();
        params.put("cart_item_ids", Collections.singletonList(1));
        params.put("address_id", 1L);
        return params;
    }

    private CartItem cartItem(long medicineId, int quantity) {
        CartItem item = new CartItem();
        item.setMedicineId(medicineId);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("18.50"));
        item.setMedicineName("布洛芬缓释胶囊");
        item.setDrugType(0);
        return item;
    }

    private void stubCommon(List<CartItem> items) {
        when(addressMapper.selectOne(any())).thenReturn(new Address());
        when(cartItemMapper.selectByIds(anyList(), eq(USER_ID))).thenReturn(items);
    }

    @Test
    @DisplayName("Redis 预减判定库存不足时快速失败：不查库、不落单、不产生补偿动作")
    void create_redisSaysNotEnough_shouldFailFast() {
        stubCommon(Collections.singletonList(cartItem(1L, 2)));
        when(stockCache.tryPreDeduct(1L, 2)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.create(params(), USER_ID));

        assertEquals(40002, exception.getCode());
        // 预减就失败了，连 DB 都不该碰
        verify(inventoryStockManager, never()).lock(anyLong(), anyInt());
        verify(orderMapper, never()).insert(any());
        verify(stockCache, never()).rollback(any(), anyInt());
    }

    @Test
    @DisplayName("Redis 预减通过但 DB 阶段异常时，必须把预减掉的库存归还")
    void create_dbCheckFails_shouldRollbackRedis() {
        stubCommon(Collections.singletonList(cartItem(1L, 2)));
        when(stockCache.tryPreDeduct(1L, 2)).thenReturn(true);
        // 库存记录缺失 → 乐观锁管理器抛异常
        when(inventoryStockManager.lock(1L, 2))
                .thenThrow(new BusinessException("药品库存信息不存在: 1", 40002));

        assertThrows(BusinessException.class, () -> orderService.create(params(), USER_ID));

        // 预减成功过，所以失败时必须显式补偿，否则可售库存会凭空减少
        verify(stockCache, times(1)).rollback(1L, 2);
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Redis 未预热（降级放行）时，可售不足由 DB 乐观锁拦下并归还预减量")
    void create_redisDegraded_shouldStillBeBlockedByDb() {
        stubCommon(Collections.singletonList(cartItem(1L, 2)));
        when(stockCache.tryPreDeduct(1L, 2)).thenReturn(true);
        // DB 侧可售量不足 → lock 返回 false
        when(inventoryStockManager.lock(1L, 2)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.create(params(), USER_ID));

        assertEquals(40002, exception.getCode());
        verify(stockCache, times(1)).rollback(1L, 2);
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("多商品下单中途失败：前面已预减的条目也要逐个归还")
    void create_secondItemInsufficient_shouldRollbackAllPreDeducted() {
        stubCommon(Arrays.asList(cartItem(1L, 2), cartItem(2L, 3)));
        when(stockCache.tryPreDeduct(anyLong(), anyInt())).thenReturn(true);
        when(inventoryStockManager.lock(1L, 2)).thenReturn(true);   // 第 1 件锁定成功
        when(inventoryStockManager.lock(2L, 3)).thenReturn(false);  // 第 2 件不足

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.create(params(), USER_ID));

        assertEquals(40002, exception.getCode());
        // 两件都要归还：第 1 件虽锁定成功，但整个事务会回滚，Redis 上的预减只能手动补回
        verify(stockCache, times(1)).rollback(1L, 2);
        verify(stockCache, times(1)).rollback(2L, 3);
        verify(orderMapper, never()).insert(any());
    }
}
