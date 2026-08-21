-- =============================================
-- 功能升级脚本 v2（SRS 2.3.3.3 按钮级操作权限）
-- 新增按钮权限节点（menu_type=3），挂在对应功能菜单下
--   mysql -uroot -p123456 --default-character-set=utf8mb4 smart_site < upgrade_btn_perm.sql
-- =============================================
USE `smart_site`;

-- 1. 按钮权限节点
INSERT INTO `t_sys_menu` (`id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `icon`, `sort`, `status`) VALUES
(17, 14, '新增用户',   'sys:user:add',    3, NULL, NULL, 1, 1),
(18, 14, '编辑用户',   'sys:user:edit',   3, NULL, NULL, 2, 1),
(19, 14, '删除用户',   'sys:user:delete', 3, NULL, NULL, 3, 1),
(20, 14, '重置密码',   'sys:user:reset',  3, NULL, NULL, 4, 1),
(21, 15, '新增角色',   'sys:role:add',    3, NULL, NULL, 1, 1),
(22, 15, '编辑角色',   'sys:role:edit',   3, NULL, NULL, 2, 1),
(23, 15, '删除角色',   'sys:role:delete', 3, NULL, NULL, 3, 1),
(24, 7,  '告警处置',   'sys:alarm:handle', 3, NULL, NULL, 1, 1),
(25, 6,  'AI告警处置', 'sys:ai:handle',    3, NULL, NULL, 1, 1);

-- 2. 按钮权限分配：ADMIN 全部；SAFETY 处置类；LEADER 只读无按钮
INSERT IGNORE INTO `t_sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 17), (1, 18), (1, 19), (1, 20), (1, 21), (1, 22), (1, 23), (1, 24), (1, 25),
(3, 24), (3, 25);
