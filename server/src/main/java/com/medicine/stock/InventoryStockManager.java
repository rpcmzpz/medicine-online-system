package com.medicine.stock;

import com.medicine.common.BusinessException;
import com.medicine.entity.Inventory;
import com.medicine.mapper.InventoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 库存变更的唯一入口：把「MySQL 乐观锁 version + CAS 有限重试」收敛在这里，
 * 让订单、处方等上层业务不再直接写 SQL，避免同一套并发规则被抄成多个版本。
 *
 * <h3>为什么是乐观锁而不是悲观锁</h3>
 * 药品库存的冲突集中在少数秒杀型 SKU 上，绝大多数请求互不相干。
 * 悲观锁（select for update）会让所有请求串行等同一把行锁；
 * 乐观锁则让不冲突的请求全程无锁通过，只在真正撞车时才付出一次重读的代价。
 *
 * <h3>重试为什么必须换「当前读」</h3>
 * MySQL 默认可重复读下，同一事务里的普通查询走的是事务开始时的快照。
 * 如果 CAS 失败后还用普通查询重读，拿到的仍是旧 version，会一直失败到耗尽重试次数，
 * 乐观锁就退化成「必然失败」。所以第 2 次起改用 {@code SELECT ... FOR UPDATE} 当前读，
 * 既能拿到别人已提交的最新 version，也在该行上加锁直到本事务结束，后续重试必然收敛。
 */
@Component
@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public class InventoryStockManager {

    private static final Logger log = LoggerFactory.getLogger(InventoryStockManager.class);

    /** CAS 冲突后的重试上限：超过即判定热点争用过重，向上抛冲突异常由用户重试。 */
    private static final int MAX_RETRY = 3;

    @Resource
    private InventoryMapper inventoryMapper;

    /**
     * 下单锁定库存（可售量 = 总库存 - 锁定库存）。
     *
     * @return {@code false} 表示可售量不足，属于正常业务失败，调用方应转成「库存不足」；
     *         {@code true} 表示锁定成功
     */
    public boolean lock(Long medicineId, int quantity) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            Inventory inventory = read(medicineId, attempt);
            if (inventory == null) {
                throw new BusinessException("药品库存信息不存在: " + medicineId, 40002);
            }
            if (available(inventory) < quantity) {
                return false;
            }
            if (inventoryMapper.lockStock(medicineId, quantity, versionOf(inventory)) > 0) {
                return true;
            }
            log.debug("[cas] lock 版本冲突，重读重试 medicineId={} attempt={}/{}",
                    medicineId, attempt, MAX_RETRY);
        }
        throw new BusinessException("库存并发冲突，请稍后重试: " + medicineId, 40003);
    }

    /**
     * 解锁归还（订单取消、处方驳回）。
     *
     * <p>与 lock 不同，这里失败必须抛异常而不是静默：解锁属于补偿路径，
     * 一旦漏掉就会让可售库存永久少一块，宁可让事务回滚也不能悄悄吞掉。
     */
    public void unlock(Long medicineId, int quantity) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            Inventory inventory = read(medicineId, attempt);
            if (inventory == null) {
                // 库存记录已不存在（如药品被删除），无需归还
                return;
            }
            if (inventoryMapper.unlockStock(medicineId, quantity, versionOf(inventory)) > 0) {
                return;
            }
            log.debug("[cas] unlock 版本冲突，重读重试 medicineId={} attempt={}/{}",
                    medicineId, attempt, MAX_RETRY);
        }
        throw new BusinessException("库存解锁并发冲突: " + medicineId, 40003);
    }

    /**
     * 支付扣减：总库存与锁定库存同减，可售量不变。
     */
    public void deduct(Long medicineId, int quantity) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            Inventory inventory = read(medicineId, attempt);
            if (inventory == null) {
                throw new BusinessException("药品库存信息不存在: " + medicineId, 40002);
            }
            // 正常流程下这两项必然够（下单时已锁定），不够说明库存数据被外部改动过
            if (inventory.getStockQuantity() < quantity
                    || inventory.getLockedQuantity() < quantity) {
                throw new BusinessException("库存数据异常，无法扣减: " + medicineId, 40002);
            }
            if (inventoryMapper.deductStock(medicineId, quantity, versionOf(inventory)) > 0) {
                return;
            }
            log.debug("[cas] deduct 版本冲突，重读重试 medicineId={} attempt={}/{}",
                    medicineId, attempt, MAX_RETRY);
        }
        throw new BusinessException("库存扣减并发冲突: " + medicineId, 40003);
    }

    /**
     * 首次乐观读（快照），冲突后改当前读（FOR UPDATE），否则读不到新版本号。
     */
    private Inventory read(Long medicineId, int attempt) {
        return attempt == 1
                ? inventoryMapper.selectVersion(medicineId)
                : inventoryMapper.selectVersionForUpdate(medicineId);
    }

    /** 可售量：老数据 version 为 null 时按 0 处理，避免拆箱 NPE。 */
    private int versionOf(Inventory inventory) {
        return inventory.getVersion() == null ? 0 : inventory.getVersion();
    }

    private int available(Inventory inventory) {
        int stock = inventory.getStockQuantity() == null ? 0 : inventory.getStockQuantity();
        int locked = inventory.getLockedQuantity() == null ? 0 : inventory.getLockedQuantity();
        return stock - locked;
    }
}
