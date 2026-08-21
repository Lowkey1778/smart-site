-- 修复喷淋自动联动：ENV-PM25-SPR 监测点启用联动但阈值缺失导致永不触发
-- 按 PM2.5 超标标准：>=75μg/m³ 自动开喷，恢复正常 <50 自动关喷，关联 1号楼喷淋装置(SPR-001)
USE `smart_site`;
UPDATE `t_device_monitor_point`
SET `spray_on_threshold` = 75.00,
    `spray_off_threshold` = 50.00,
    `spray_device_id` = 6
WHERE `id` = 5;
