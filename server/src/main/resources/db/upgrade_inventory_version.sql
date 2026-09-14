-- ============================================================
-- 升级脚本：给 inventory 表补上乐观锁 version 列
--
-- 用途：早期版本的数据库中 inventory 没有 version 列，
--       直接跑新版代码会在 CAS 更新时报「Unknown column 'version'」。
--       全新初始化（执行 schema.sql）的库已包含该列，无需运行本脚本。
--
-- 本脚本可重复执行：列已存在时会先 DROP 再 ADD，结果一致。
-- ============================================================

-- MySQL 不直接支持 ADD COLUMN IF NOT EXISTS，用存储过程做条件判断
DROP PROCEDURE IF EXISTS upgrade_inventory_version;
DELIMITER $$
CREATE PROCEDURE upgrade_inventory_version()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'inventory'
          AND column_name = 'version'
    ) THEN
        ALTER TABLE inventory
            ADD COLUMN version INT NOT NULL DEFAULT 0
            COMMENT '乐观锁：库存 CAS 更新的版本号';
    END IF;
END$$
DELIMITER ;

CALL upgrade_inventory_version();
DROP PROCEDURE upgrade_inventory_version;

-- 校验：应能查到 version 列
-- SELECT medicine_id, stock_quantity, locked_quantity, version FROM inventory;
