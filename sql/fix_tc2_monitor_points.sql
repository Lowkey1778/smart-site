-- =============================================
-- 修复脚本：二号塔吊(TC-002, device_id=2)补充监测点配置
-- 原因：init_data.sql 只给一号塔吊配了监测点，二号塔吊详情页无监测点数据
-- 阈值依据 t_tower_crane_param id=2：rated_load=6.00t, rated_moment=400 t·m
-- =============================================
USE `smart_site`;

INSERT INTO `t_device_monitor_point`
(`point_code`, `device_id`, `point_name`, `monitor_type`, `monitor_sub_type`, `unit`,
 `warn_max`, `alarm_max`, `spray_enabled`, `collect_interval`, `status`) VALUES
('TC-MOMENT-02', 2, '力矩监测点', 'device', '力矩', 't·m', 360.00, 400.00, 0, 5, 1),
('TC-LOAD-02',   2, '吊重监测点', 'device', '吊重', 't',   5.40,  6.00,   0, 5, 1),
('TC-WIND-02',   2, '风速监测点', 'device', '风速', 'm/s', 12.00, 18.00,  0, 5, 1);
