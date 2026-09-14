package com.medicine.service;

import com.medicine.cache.MedicineCache;
import com.medicine.cache.StockCache;
import com.medicine.entity.Medicine;
import com.medicine.mapper.InventoryMapper;
import com.medicine.mapper.MedicineMapper;
import com.medicine.service.impl.AdminMedicineServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 管理员药品写路径的缓存一致性：改完库要删缓存，并触发延迟双删。
 */
@ExtendWith(MockitoExtension.class)
class AdminMedicineServiceImplTest {

    @Mock
    private MedicineMapper medicineMapper;
    @Mock
    private InventoryMapper inventoryMapper;
    @Mock
    private MedicineCache medicineCache;
    @Mock
    private StockCache stockCache;

    @InjectMocks
    private AdminMedicineServiceImpl adminMedicineService;

    @Test
    @DisplayName("更新药品：先更库，再删缓存并延迟双删")
    void update_shouldEvictCacheAfterDbUpdate() {
        Medicine existing = new Medicine();
        existing.setMedicineId(1L);
        existing.setName("布洛芬缓释胶囊");
        when(medicineMapper.selectById(1L)).thenReturn(existing);
        when(medicineMapper.updateById(any(Medicine.class))).thenReturn(1);

        Map<String, Object> params = new HashMap<>();
        params.put("price", "19.90");
        adminMedicineService.update(1L, params);

        InOrder inOrder = inOrder(medicineMapper, medicineCache);
        inOrder.verify(medicineMapper).updateById(any(Medicine.class));
        inOrder.verify(medicineCache).evict(1L);
        verify(medicineCache).evictDelayed(1L);
    }

    @Test
    @DisplayName("下架药品：同样要清详情缓存")
    void toggleStatus_shouldEvictCache() {
        Medicine existing = new Medicine();
        existing.setMedicineId(2L);
        when(medicineMapper.selectById(2L)).thenReturn(existing);
        when(medicineMapper.updateById(any(Medicine.class))).thenReturn(1);

        adminMedicineService.toggleStatus(2L, 0);

        verify(medicineCache).evict(2L);
        verify(medicineCache).evictDelayed(2L);
    }

    @Test
    @DisplayName("管理员改库存：按「总库存 - 已锁定」重新对齐 Redis 可售量")
    void updateInventory_shouldSyncAvailableStock() {
        when(inventoryMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.insert(any())).thenReturn(1);

        adminMedicineService.updateInventory(1L, 100, 10);

        verify(stockCache).sync(1L, 100);
        verify(stockCache, never()).rollback(eq(1L), anyInt());
    }
}
