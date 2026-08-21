-- =============================================
-- 权限补齐脚本：系统管理菜单 + 全部按钮权限（menu_type=3）
-- 背景：upgrade_btn_perm.sql 未执行且不完整（缺 spray/device/env 权限码，父节点 14/15 不存在）
-- 幂等：INSERT IGNORE，可重复执行
-- 执行：mysql -uroot -p1234 --default-character-set=utf8mb4 smart_site < upgrade_btn_perm_full.sql
-- =============================================
USE `smart_site`;

-- 1. 系统管理目录与菜单（用户管理/角色管理/操作日志）
INSERT IGNORE INTO `t_sys_menu` (`id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `icon`, `sort`, `status`) VALUES
(14, 0,  '系统管理',   'sys',          1, '/system',     'Setting',        14, 1),
(15, 14, '用户管理',   'sys:user',     1, '/system/user', 'User',          1, 1),
(16, 14, '角色管理',   'sys:role',     1, '/system/role', 'UserFilled',    2, 1),
(30, 14, '操作日志',   'sys:log',      1, '/system/log',  'Document',      3, 1);

-- 2. 按钮权限节点（挂在对应功能菜单下）
INSERT IGNORE INTO `t_sys_menu` (`id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `icon`, `sort`, `status`) VALUES
-- 用户管理按钮
(17, 15, '新增用户',   'sys:user:add',     3, NULL, NULL, 1, 1),
(18, 15, '编辑用户',   'sys:user:edit',    3, NULL, NULL, 2, 1),
(19, 15, '删除用户',   'sys:user:delete',  3, NULL, NULL, 3, 1),
(20, 15, '重置密码',   'sys:user:reset',   3, NULL, NULL, 4, 1),
-- 角色管理按钮
(21, 16, '新增角色',   'sys:role:add',     3, NULL, NULL, 1, 1),
(22, 16, '编辑角色',   'sys:role:edit',    3, NULL, NULL, 2, 1),
(23, 16, '删除角色',   'sys:role:delete',  3, NULL, NULL, 3, 1),
-- 告警 / AI 处置按钮
(24, 7,  '告警处置',   'sys:alarm:handle', 3, NULL, NULL, 1, 1),
(25, 6,  'AI告警处置', 'sys:ai:handle',    3, NULL, NULL, 1, 1),
-- 喷淋按钮（前端 SprayMonitor 使用）
(26, 9,  '喷淋任务管理', 'sys:spray:task',  3, NULL, NULL, 1, 1),
(27, 9,  '喷淋手动控制', 'sys:spray:control', 3, NULL, NULL, 2, 1),
-- 设备管理按钮（前端 DeviceList/DeviceDetail 使用）
(31, 2,  '新增设备',   'sys:device:add',     3, NULL, NULL, 1, 1),
(32, 2,  '编辑设备',   'sys:device:edit',    3, NULL, NULL, 2, 1),
(33, 2,  '删除设备',   'sys:device:delete',  3, NULL, NULL, 3, 1),
(34, 2,  '类型管理',   'sys:device:type',    3, NULL, NULL, 4, 1),
(35, 2,  '位置管理',   'sys:device:location',3, NULL, NULL, 5, 1),
(36, 2,  '监测点管理', 'sys:device:point',   3, NULL, NULL, 6, 1),
-- 环境监测点管理按钮
(37, 8,  '监测点管理', 'sys:env:point',      3, NULL, NULL, 1, 1);

-- 3. 菜单分配：ADMIN 补系统管理菜单；LEADER 保持只读；SAFETY 补系统管理查看
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 14), (1, 15), (1, 16), (1, 30),
(3, 14), (3, 15), (3, 16), (3, 30);

-- 4. 按钮分配：ADMIN 全部；SAFETY 处置/运维类；LEADER 只读无按钮
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 17), (1, 18), (1, 19), (1, 20), (1, 21), (1, 22), (1, 23), (1, 24), (1, 25),
(1, 26), (1, 27), (1, 31), (1, 32), (1, 33), (1, 34), (1, 35), (1, 36), (1, 37),
(3, 24), (3, 25), (3, 26), (3, 27), (3, 37);
