-- =============================================
-- 功能升级脚本 v3（设备资产管理 T-06~T-13 按钮级操作权限）
-- 新增设备管理相关按钮节点（menu_type=3），挂在“设备资产管理(2)”下
-- =============================================
USE `smart_site`;

INSERT INTO `t_sys_menu` (`id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `icon`, `sort`, `status`) VALUES
(26, 2, '新增设备',     'sys:device:add',      3, NULL, NULL, 1, 1),
(27, 2, '编辑设备',     'sys:device:edit',     3, NULL, NULL, 2, 1),
(28, 2, '删除设备',     'sys:device:delete',   3, NULL, NULL, 3, 1),
(29, 2, '设备类型管理', 'sys:device:type',     3, NULL, NULL, 4, 1),
(30, 2, '设备位置管理', 'sys:device:location', 3, NULL, NULL, 5, 1),
(31, 2, '监测点配置',   'sys:device:point',    3, NULL, NULL, 6, 1);

-- ADMIN 全部；SAFETY 类型/位置/监测点配置（日常维护），不含设备本体增删改
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 26), (1, 27), (1, 28), (1, 29), (1, 30), (1, 31),
(3, 29), (3, 30), (3, 31);
