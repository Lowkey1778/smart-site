-- 修复喷淋降尘按钮权限缺失：sys:spray:task / sys:spray:control 挂喷淋菜单(id=9)下并授权
USE `smart_site`;
INSERT INTO `t_sys_menu` (`id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `sort`, `status`) VALUES
(33, 9, '定时任务管理', 'sys:spray:task', 3, NULL, 1, 1),
(34, 9, '手动喷淋控制', 'sys:spray:control', 3, NULL, 2, 1);
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 33), (3, 33), (1, 34), (3, 34);
