package com.medicine.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medicine.entity.Inventory;
import com.medicine.mapper.InventoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把数据库的可售库存预热到 Redis。
 *
 * <p>没有预热，Lua 脚本会一直返回「未预热」，库存预减就形同虚设；
 * 这里以 DB 为准覆盖写（而不是 setIfAbsent），是为了避免 Redis 里残留上一次运行的旧计数与 DB 漂移。
 * Bean 的初始化数据由 {@code DataInitializer} 落库，所以本 Runner 必须排在它之后执行。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StockCacheWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockCacheWarmer.class);

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private StockCache stockCache;

    @Override
    public void run(ApplicationArguments args) {
        List<Inventory> inventoryList = inventoryMapper.selectList(new QueryWrapper<Inventory>());
        for (Inventory inventory : inventoryList) {
            stockCache.sync(inventory.getMedicineId(), availableOf(inventory));
        }
        log.info("Redis 库存预热完成，共 {} 条药品", inventoryList.size());
    }

    private int availableOf(Inventory inventory) {
        int stock = inventory.getStockQuantity() == null ? 0 : inventory.getStockQuantity();
        int locked = inventory.getLockedQuantity() == null ? 0 : inventory.getLockedQuantity();
        return Math.max(stock - locked, 0);
    }
}
