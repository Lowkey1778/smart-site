-- =============================================
-- 功能升级脚本（T-01 用户管理 / T-02 角色管理 / T-03 动态菜单 / T-19 告警统计分析）
-- 在 smart_site 库执行：
--   mysql -uroot -p123456 smart_site < upgrade_sys_menu.sql
-- =============================================
USE `smart_site`;

-- 1. 新增菜单：系统管理（目录）、用户管理、角色管理、告警统计分析
-- 先顺延原 sort>=8 的菜单，让“告警统计分析”紧跟“告警管理”
UPDATE `t_sys_menu` SET `sort` = `sort` + 1 WHERE `sort` >= 8;

INSERT INTO `t_sys_menu` (`id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `icon`, `sort`, `status`) VALUES
(13, 0,  '系统管理',      'system',     1, NULL,           'Setting',    13, 1),
(14, 13, '用户管理',      'sys-user',   2, '/system/user', 'User',        1, 1),
(15, 13, '角色管理',      'sys-role',   2, '/system/role', 'UserFilled',  2, 1),
(16, 0,  '告警统计分析',  'alarm-stats',2, '/alarm/stats', 'TrendCharts', 8, 1);

-- 2. 角色-菜单分配：
--    ADMIN(1) 全部菜单（含系统管理/用户管理/角色管理/告警统计分析）
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 13), (1, 14), (1, 15), (1, 16);
--    LEADER(2) 只读为主：追加 告警统计分析
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES (2, 16);
--    SAFETY(3) 日常操作：追加 告警统计分析
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES (3, 16);
