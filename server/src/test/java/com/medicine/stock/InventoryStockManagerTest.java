package com.medicine.stock;

import com.medicine.common.BusinessException;
import com.medicine.entity.Inventory;
import com.medicine.mapper.InventoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 库存乐观锁 CAS 的行为验证：
 * 版本冲突要重读重试、可售不足要如实返回失败、补偿路径（解锁）失败必须抛异常。
 */
@ExtendWith(MockitoExtension.class)
class InventoryStockManagerTest {

    private static final Long MEDICINE_ID = 1L;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryStockManager manager;

    private Inventory inventory(int stock, int locked, int version) {
        Inventory inv = new Inventory();
        inv.setMedicineId(MEDICINE_ID);
        inv.setStockQuantity(stock);
        inv.setLockedQuantity(locked);
        inv.setVersion(version);
        return inv;
    }

    // ---------------- lock ----------------

    @Test
    @DisplayName("锁定成功：一次 CAS 命中，version 作为条件传入")
    void lock_shouldSucceedOnFirstCas() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 100, 7));
        when(inventoryMapper.lockStock(MEDICINE_ID, 20, 7)).thenReturn(1);

        assertTrue(manager.lock(MEDICINE_ID, 20));

        verify(inventoryMapper, times(1)).lockStock(MEDICINE_ID, 20, 7);
        // 没有冲突就不该走当前读
        verify(inventoryMapper, never()).selectVersionForUpdate(anyLong());
    }

    @Test
    @DisplayName("可售量不足：直接返回 false，连 CAS 都不发")
    void lock_notEnoughAvailable_shouldReturnFalse() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 495, 3));

        assertFalse(manager.lock(MEDICINE_ID, 10), "可售 5 < 需求 10，应返回 false");

        verify(inventoryMapper, never()).lockStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("版本冲突后重试：第 2 次改用 FOR UPDATE 当前读取最新 version 并成功")
    void lock_versionConflict_shouldRetryWithCurrentRead() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 100, 0));
        when(inventoryMapper.lockStock(MEDICINE_ID, 20, 0)).thenReturn(0);      // 旧版本，被别人改过
        when(inventoryMapper.selectVersionForUpdate(MEDICINE_ID)).thenReturn(inventory(500, 100, 1));
        when(inventoryMapper.lockStock(MEDICINE_ID, 20, 1)).thenReturn(1);      // 新版本，成功

        assertTrue(manager.lock(MEDICINE_ID, 20));

        verify(inventoryMapper, times(1)).selectVersion(MEDICINE_ID);
        verify(inventoryMapper, times(1)).selectVersionForUpdate(MEDICINE_ID);
        verify(inventoryMapper, times(1)).lockStock(MEDICINE_ID, 20, 1);
    }

    @Test
    @DisplayName("持续冲突到重试上限：抛并发冲突异常，不静默丢掉锁定")
    void lock_alwaysConflicting_shouldThrowAfterMaxRetry() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 0, 0));
        when(inventoryMapper.selectVersionForUpdate(MEDICINE_ID)).thenReturn(inventory(500, 0, 0));
        when(inventoryMapper.lockStock(eq(MEDICINE_ID), eq(20), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.lock(MEDICINE_ID, 20));

        assertEquals(40003, exception.getCode());
        // 第 1 次乐观读 + 第 2、3 次当前读
        verify(inventoryMapper, times(2)).selectVersionForUpdate(MEDICINE_ID);
        verify(inventoryMapper, times(3)).lockStock(eq(MEDICINE_ID), eq(20), anyInt());
    }

    @Test
    @DisplayName("库存记录不存在：抛业务异常（下单不该在缺失库存上继续）")
    void lock_inventoryMissing_shouldThrow() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.lock(MEDICINE_ID, 1));

        assertEquals(40002, exception.getCode());
    }

    // ---------------- unlock ----------------

    @Test
    @DisplayName("解锁成功：CAS 命中即返回")
    void unlock_shouldSucceedOnFirstCas() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 100, 4));
        when(inventoryMapper.unlockStock(MEDICINE_ID, 20, 4)).thenReturn(1);

        assertDoesNotThrow(() -> manager.unlock(MEDICINE_ID, 20));

        verify(inventoryMapper, times(1)).unlockStock(MEDICINE_ID, 20, 4);
    }

    @Test
    @DisplayName("库存记录已不存在：解锁静默返回，不抛异常")
    void unlock_inventoryMissing_shouldReturnQuietly() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(null);

        assertDoesNotThrow(() -> manager.unlock(MEDICINE_ID, 20));
        verify(inventoryMapper, never()).unlockStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("解锁持续冲突：必须抛异常，防止可售库存被永久少算")
    void unlock_alwaysConflicting_shouldThrow() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 100, 0));
        when(inventoryMapper.selectVersionForUpdate(MEDICINE_ID)).thenReturn(inventory(500, 100, 0));
        when(inventoryMapper.unlockStock(eq(MEDICINE_ID), eq(20), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.unlock(MEDICINE_ID, 20));

        assertEquals(40003, exception.getCode());
    }

    // ---------------- deduct ----------------

    @Test
    @DisplayName("支付扣减成功：总库存与锁定库存同减")
    void deduct_shouldSucceedOnFirstCas() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 100, 9));
        when(inventoryMapper.deductStock(MEDICINE_ID, 20, 9)).thenReturn(1);

        assertDoesNotThrow(() -> manager.deduct(MEDICINE_ID, 20));

        verify(inventoryMapper, times(1)).deductStock(MEDICINE_ID, 20, 9);
    }

    @Test
    @DisplayName("锁定数小于扣减量：判定库存数据异常，抛业务异常而非硬扣")
    void deduct_lockedLessThanQuantity_shouldThrow() {
        when(inventoryMapper.selectVersion(MEDICINE_ID)).thenReturn(inventory(500, 5, 2));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.deduct(MEDICINE_ID, 20));

        assertEquals(40002, exception.getCode());
        verify(inventoryMapper, never()).deductStock(anyLong(), anyInt(), anyInt());
    }
}
