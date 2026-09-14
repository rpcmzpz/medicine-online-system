package com.medicine.service;

import com.medicine.cache.MedicineCache;
import com.medicine.common.BusinessException;
import com.medicine.common.Result;
import com.medicine.entity.Inventory;
import com.medicine.mapper.CategoryMapper;
import com.medicine.mapper.InventoryMapper;
import com.medicine.mapper.MedicineMapper;
import com.medicine.mapper.ReviewMapper;
import com.medicine.service.impl.MedicineServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 药品详情接口的缓存行为测试：命中缓存不查库、库存实时回源、不存在的药品返回 404。
 */
@ExtendWith(MockitoExtension.class)
class MedicineServiceImplTest {

    @Mock
    private MedicineMapper medicineMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private InventoryMapper inventoryMapper;
    @Mock
    private MedicineCache medicineCache;

    @InjectMocks
    private MedicineServiceImpl medicineService;

    @Test
    @DisplayName("详情命中缓存时直接返回缓存数据，不再查药品/评价表，并补上实时库存")
    void detail_hitCache_shouldNotQueryDbExceptStock() {
        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("medicine_id", 1L);
        cached.put("name", "布洛芬缓释胶囊");
        when(medicineCache.getOrLoad(eq(1L), any())).thenReturn(cached);

        Inventory inventory = new Inventory();
        inventory.setMedicineId(1L);
        inventory.setStockQuantity(500);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);

        Result result = medicineService.detail(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("布洛芬缓释胶囊", data.get("name"));
        // 库存不进缓存，每次读都回源，保证不会展示过期库存
        assertEquals(500, data.get("stock_quantity"));
        // 命中缓存说明没回源，药品/分类/评价三个 Mapper 都不该被调用
        verifyNoInteractions(medicineMapper, categoryMapper, reviewMapper);
    }

    @Test
    @DisplayName("详情缓存里是空值标记（药品不存在）时抛 404 业务异常")
    void detail_nullMarked_shouldThrow404() {
        when(medicineCache.getOrLoad(eq(999999L), any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> medicineService.detail(999999L));

        assertEquals(404, exception.getHttpStatus());
        assertEquals(40400, exception.getCode());
    }
}
