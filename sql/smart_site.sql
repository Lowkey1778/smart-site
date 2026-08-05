-- =============================================
-- 数据库名称: smart_site
-- 字符集: utf8mb4
-- 描述: 建筑安全智能监控平台 数据库建表脚本
-- 版本: V1.0
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `smart_site` 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_general_ci;

USE `smart_site`;

-- 开启外键约束检查（确保数据完整性）
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 权限管理模块
-- =============================================

-- 1.1 系统用户表
DROP TABLE IF EXISTS `t_sys_user`;
CREATE TABLE `t_sys_user` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
  `password` VARCHAR(200) NOT NULL COMMENT '登录密码(BCrypt加密)',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `emp_no` VARCHAR(50) DEFAULT NULL COMMENT '员工编号',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `gender` TINYINT(4) DEFAULT '0' COMMENT '性别(0-未选择,1-男,2-女)',
  `position` VARCHAR(50) DEFAULT NULL COMMENT '岗位',
  `dept` VARCHAR(50) DEFAULT NULL COMMENT '部门',
  `leader` VARCHAR(50) DEFAULT NULL COMMENT '直属上级',
  `status` TINYINT(4) NOT NULL DEFAULT '2' COMMENT '账号状态(1-正常,0-禁用,2-未激活)',
  `locked` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '锁定标记(1-锁定,0-正常)',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 1.2 系统角色表
DROP TABLE IF EXISTS `t_sys_role`;
CREATE TABLE `t_sys_role` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码(ADMIN/LEADER/SAFETY)',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '角色状态(1-启用,0-禁用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 1.3 用户角色关联表
DROP TABLE IF EXISTS `t_sys_user_role`;
CREATE TABLE `t_sys_user_role` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID(关联t_sys_user.id)',
  `role_id` BIGINT(20) NOT NULL COMMENT '角色ID(关联t_sys_role.id)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 1.4 菜单权限表
DROP TABLE IF EXISTS `t_sys_menu`;
CREATE TABLE `t_sys_menu` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` BIGINT(20) NOT NULL DEFAULT '0' COMMENT '上级菜单ID(0表示根节点)',
  `menu_name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
  `menu_code` VARCHAR(50) NOT NULL COMMENT '菜单编码',
  `menu_type` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '菜单类型(1-目录,2-菜单,3-按钮)',
  `path` VARCHAR(100) DEFAULT NULL COMMENT '路由路径',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标',
  `sort` INT(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '状态(1-启用,0-禁用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_menu_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- 1.5 角色菜单权限关联表
DROP TABLE IF EXISTS `t_sys_role_menu`;
CREATE TABLE `t_sys_role_menu` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT(20) NOT NULL COMMENT '角色ID(关联t_sys_role.id)',
  `menu_id` BIGINT(20) NOT NULL COMMENT '菜单ID(关联t_sys_menu.id)',
  `actions` VARCHAR(100) DEFAULT NULL COMMENT '操作权限(按钮编码集合,逗号分隔)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单权限关联表';

-- =============================================
-- 2. 设备资产管理模块
-- =============================================

-- 2.1 设备类型表(树形)
DROP TABLE IF EXISTS `t_device_type`;
CREATE TABLE `t_device_type` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` BIGINT(20) NOT NULL DEFAULT '0' COMMENT '上级类型ID(0表示根节点)',
  `type_name` VARCHAR(50) NOT NULL COMMENT '类型名称',
  `sort` INT(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_type_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型表';

-- 2.2 设备位置表(树形)
DROP TABLE IF EXISTS `t_device_location`;
CREATE TABLE `t_device_location` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` BIGINT(20) NOT NULL DEFAULT '0' COMMENT '上级位置ID(0表示根节点)',
  `location_name` VARCHAR(50) NOT NULL COMMENT '位置名称',
  `sort` INT(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_location_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备位置表';

-- 2.3 设备台账表(核心)
DROP TABLE IF EXISTS `t_device`;
CREATE TABLE `t_device` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_code` VARCHAR(50) NOT NULL COMMENT '设备编码(唯一)',
  `device_name` VARCHAR(50) NOT NULL COMMENT '设备名称',
  `type_id` BIGINT(20) NOT NULL COMMENT '设备类型ID(关联t_device_type.id)',
  `location_id` BIGINT(20) NOT NULL COMMENT '安装位置ID(关联t_device_location.id)',
  `brand` VARCHAR(50) DEFAULT NULL COMMENT '品牌/厂家',
  `model` VARCHAR(50) DEFAULT NULL COMMENT '型号',
  `supplier` VARCHAR(100) DEFAULT NULL COMMENT '供应商',
  `qr_code` VARCHAR(255) DEFAULT NULL COMMENT '二维码编号',
  `produce_date` DATE DEFAULT NULL COMMENT '生产日期',
  `supply_date` DATE DEFAULT NULL COMMENT '供货日期',
  `accept_date` DATE DEFAULT NULL COMMENT '验收日期',
  `install_date` DATE DEFAULT NULL COMMENT '安装日期',
  `enable_date` DATE DEFAULT NULL COMMENT '启用日期',
  `design_service_life` INT(11) DEFAULT NULL COMMENT '设计使用年限(年)',
  `expect_scrap_date` DATE DEFAULT NULL COMMENT '预计报废日期',
  `actual_scrap_date` DATE DEFAULT NULL COMMENT '实际报废日期',
  `last_maintain_date` DATE DEFAULT NULL COMMENT '最近维修日期',
  `original_value` DECIMAL(14,2) DEFAULT NULL COMMENT '设备原值(元)',
  `device_image` VARCHAR(255) DEFAULT NULL COMMENT '设备图片URL',
  `coordinate` VARCHAR(50) DEFAULT NULL COMMENT '平面坐标',
  `status` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '运行状态(1-在线,0-离线)',
  `enable_status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '启用状态(1-启用,0-禁用)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_code` (`device_code`),
  KEY `idx_device_type` (`type_id`),
  KEY `idx_device_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账表';

-- 2.4 塔吊基础参数表
DROP TABLE IF EXISTS `t_tower_crane_param`;
CREATE TABLE `t_tower_crane_param` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` BIGINT(20) NOT NULL COMMENT '塔吊设备ID(关联t_device.id)',
  `front_arm_len` DECIMAL(10,2) NOT NULL COMMENT '前臂长(m)',
  `rear_arm_len` DECIMAL(10,2) NOT NULL COMMENT '后臂长(m)',
  `max_height` DECIMAL(10,2) NOT NULL COMMENT '最大高度(m)',
  `rated_load` DECIMAL(10,2) NOT NULL COMMENT '额定载荷(t)',
  `max_load` DECIMAL(10,2) NOT NULL COMMENT '最大载荷(t)',
  `rated_moment` DECIMAL(12,2) NOT NULL COMMENT '额定力矩(t·m)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tc_param_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='塔吊基础参数表';

-- 2.5 升降机基础参数表
DROP TABLE IF EXISTS `t_lift_param`;
CREATE TABLE `t_lift_param` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` BIGINT(20) NOT NULL COMMENT '升降机设备ID(关联t_device.id)',
  `rated_weight` DECIMAL(10,2) NOT NULL COMMENT '额定重量(t)',
  `base_height` DECIMAL(10,2) NOT NULL COMMENT '基础高度(m)',
  `lift_speed` DECIMAL(8,2) NOT NULL COMMENT '提升速度(m/s)',
  `rated_load` DECIMAL(10,2) NOT NULL COMMENT '额定载荷(kg) [注意单位]',
  `cage_size` DECIMAL(8,2) NOT NULL COMMENT '吊笼尺寸(m²)',
  `max_lift_height` DECIMAL(10,2) NOT NULL COMMENT '最大提升高度(m)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lift_param_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='升降机基础参数表';

-- 2.6 设备监测点表
DROP TABLE IF EXISTS `t_device_monitor_point`;
CREATE TABLE `t_device_monitor_point` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `point_code` VARCHAR(50) NOT NULL COMMENT '监测点编码(唯一)',
  `device_id` BIGINT(20) NOT NULL COMMENT '所属设备ID(关联t_device.id)',
  `point_name` VARCHAR(50) NOT NULL COMMENT '监测点名称',
  `monitor_type` VARCHAR(20) NOT NULL COMMENT '监测类型(固定为device)',
  `monitor_sub_type` VARCHAR(30) NOT NULL COMMENT '监测子类型(风速/力矩/吊重等)',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位',
  `warn_min` DECIMAL(12,2) DEFAULT NULL COMMENT '预警下限',
  `warn_max` DECIMAL(12,2) DEFAULT NULL COMMENT '预警上限',
  `alarm_min` DECIMAL(12,2) DEFAULT NULL COMMENT '报警下限',
  `alarm_max` DECIMAL(12,2) DEFAULT NULL COMMENT '报警上限',
  `spray_enabled` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '是否关联喷淋(1-是,0-否)',
  `spray_on_threshold` DECIMAL(12,2) DEFAULT NULL COMMENT '启动喷淋阈值',
  `spray_off_threshold` DECIMAL(12,2) DEFAULT NULL COMMENT '关闭喷淋阈值',
  `spray_device_id` BIGINT(20) DEFAULT NULL COMMENT '关联喷淋设备ID(关联t_device.id)',
  `collect_interval` INT(11) NOT NULL DEFAULT '30' COMMENT '采集间隔(秒)',
  `install_location` VARCHAR(100) DEFAULT NULL COMMENT '安装位置',
  `status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '状态(1-启用,0-禁用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_point_code` (`point_code`),
  KEY `idx_monitor_point_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备监测点表';

-- 2.7 设备离线记录表
DROP TABLE IF EXISTS `t_device_offline_record`;
CREATE TABLE `t_device_offline_record` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` BIGINT(20) NOT NULL COMMENT '设备ID(关联t_device.id)',
  `status` TINYINT(4) NOT NULL COMMENT '状态变更(1-离线,2-上线)',
  `record_time` DATETIME NOT NULL COMMENT '状态变更时刻',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_offline_device_time` (`device_id`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备离线记录表';

-- 2.8 设备实时数据表
DROP TABLE IF EXISTS `t_realtime_data`;
CREATE TABLE `t_realtime_data` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` BIGINT(20) NOT NULL COMMENT '设备ID(关联t_device.id)',
  `point_id` BIGINT(20) DEFAULT NULL COMMENT '监测点ID(关联t_device_monitor_point.id)',
  `param_code` VARCHAR(30) NOT NULL COMMENT '参数编码',
  `param_value` DECIMAL(12,2) NOT NULL COMMENT '监测数据值',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位',
  `collect_time` DATETIME NOT NULL COMMENT '监测时间(采集时刻)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_realtime_device_time` (`device_id`, `collect_time`),
  KEY `idx_realtime_point_time` (`point_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备实时数据表';

-- =============================================
-- 3. 塔吊与升降机作业记录
-- =============================================

-- 3.1 塔吊作业记录表
DROP TABLE IF EXISTS `t_tower_crane_record`;
CREATE TABLE `t_tower_crane_record` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` BIGINT(20) NOT NULL COMMENT '塔吊设备ID(关联t_device.id)',
  `start_time` DATETIME NOT NULL COMMENT '作业开始时间',
  `end_time` DATETIME NOT NULL COMMENT '作业结束时间',
  `hoisting_weight` DECIMAL(12,2) NOT NULL COMMENT '吊重(t)',
  `max_load_percent` DECIMAL(6,2) NOT NULL COMMENT '最大载荷百分比(%)',
  `max_radius` DECIMAL(10,2) DEFAULT NULL COMMENT '最大半径(m)',
  `min_radius` DECIMAL(10,2) DEFAULT NULL COMMENT '最小半径(m)',
  `max_height` DECIMAL(10,2) DEFAULT NULL COMMENT '最大高度(m)',
  `min_height` DECIMAL(10,2) DEFAULT NULL COMMENT '最小高度(m)',
  `max_wind_speed` DECIMAL(6,2) DEFAULT NULL COMMENT '最大风速(m/s)',
  `max_load` DECIMAL(12,2) DEFAULT NULL COMMENT '最大吊重(t)',
  `start_angle` DECIMAL(8,2) DEFAULT NULL COMMENT '起始角度(°)',
  `end_angle` DECIMAL(8,2) DEFAULT NULL COMMENT '结束角度(°)',
  `hook_radius` DECIMAL(10,2) DEFAULT NULL COMMENT '吊点半径(m)',
  `hook_height` DECIMAL(10,2) DEFAULT NULL COMMENT '吊点高度(m)',
  `unload_radius` DECIMAL(10,2) DEFAULT NULL COMMENT '卸料点半径(m)',
  `unload_height` DECIMAL(10,2) DEFAULT NULL COMMENT '卸料点高度(m)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tc_device_time` (`device_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='塔吊作业记录表';

-- 3.2 升降机作业记录表
DROP TABLE IF EXISTS `t_lift_record`;
CREATE TABLE `t_lift_record` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` BIGINT(20) NOT NULL COMMENT '升降机设备ID(关联t_device.id)',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `load_weight` DECIMAL(12,2) NOT NULL COMMENT '载重(kg)',
  `person_count` INT(11) NOT NULL COMMENT '载人数',
  `start_floor` INT(11) DEFAULT NULL COMMENT '起始楼层',
  `end_floor` INT(11) DEFAULT NULL COMMENT '结束楼层',
  `wind_speed` DECIMAL(6,2) DEFAULT NULL COMMENT '风速(m/s)',
  `run_speed` DECIMAL(8,2) DEFAULT NULL COMMENT '运行速度(m/s)',
  `tilt_angle_x` DECIMAL(8,2) DEFAULT NULL COMMENT 'X方向倾斜角(°)',
  `tilt_angle_y` DECIMAL(8,2) DEFAULT NULL COMMENT 'Y方向倾斜角(°)',
  `start_height` DECIMAL(10,2) DEFAULT NULL COMMENT '起始高度(m)',
  `end_height` DECIMAL(10,2) DEFAULT NULL COMMENT '结束高度(m)',
  `direction` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '运行方向(1-上升,2-下降)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_lift_device_time` (`device_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='升降机作业记录表';

-- =============================================
-- 4. 视频监控模块
-- =============================================

DROP TABLE IF EXISTS `t_camera`;
CREATE TABLE `t_camera` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `camera_code` VARCHAR(50) NOT NULL COMMENT '摄像头编码(唯一)',
  `camera_name` VARCHAR(50) NOT NULL COMMENT '摄像头名称',
  `location_id` BIGINT(20) NOT NULL COMMENT '安装位置ID(关联t_device_location.id)',
  `stream_url` VARCHAR(255) NOT NULL COMMENT '视频播放地址',
  `online_status` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '在线状态(1-在线,0-离线)',
  `enable_status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '启用状态(1-启用,0-禁用)',
  `ai_helmet` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '安全帽检测(1-开启,0-关闭)',
  `ai_vest` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '安全服检测(1-开启,0-关闭)',
  `ai_smoke` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '吸烟检测(1-开启,0-关闭)',
  `ai_fire` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '明火检测(1-开启,0-关闭)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_camera_code` (`camera_code`),
  KEY `idx_camera_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头表';

-- =============================================
-- 5. 告警管理模块
-- =============================================

DROP TABLE IF EXISTS `t_alarm`;
CREATE TABLE `t_alarm` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_no` VARCHAR(30) NOT NULL COMMENT '告警编号(如AL202608040001)',
  `batch_no` VARCHAR(30) DEFAULT NULL COMMENT '批次号(同一事件多个监测点共享)',
  `alarm_source` TINYINT(4) NOT NULL COMMENT '告警来源(1-设备监测,2-环境监测,3-AI识别)',
  `alarm_level` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '告警级别(1-预警,2-警报,3-控制)',
  `device_id` BIGINT(20) DEFAULT NULL COMMENT '来源设备ID(关联t_device.id)',
  `point_id` BIGINT(20) DEFAULT NULL COMMENT '监测点ID(关联t_device_monitor_point.id或t_env_monitor_point.id)',
  `camera_id` BIGINT(20) DEFAULT NULL COMMENT '关联摄像头ID(AI告警时关联t_camera.id)',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '告警截图URL(AI告警)',
  `alarm_content` VARCHAR(255) NOT NULL COMMENT '告警内容',
  `alarm_value` DECIMAL(12,2) DEFAULT NULL COMMENT '监测数据值(触发值)',
  `alarm_time` DATETIME NOT NULL COMMENT '报警时间(触发时刻)',
  `handle_status` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '处置状态(0-未处置,1-处置中,2-已处置)',
  `handle_person` VARCHAR(50) DEFAULT NULL COMMENT '处置人',
  `handle_measure` VARCHAR(500) DEFAULT NULL COMMENT '处置措施',
  `handle_conclusion` VARCHAR(500) DEFAULT NULL COMMENT '处置结论',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处置时间',
  `recover_time` DATETIME DEFAULT NULL COMMENT '恢复时间(数据恢复正常)',
  `recover_value` DECIMAL(12,2) DEFAULT NULL COMMENT '恢复数据值',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_alarm_status_level` (`handle_status`, `alarm_level`),
  KEY `idx_alarm_source_time` (`alarm_source`, `alarm_time`),
  KEY `idx_alarm_device` (`device_id`, `alarm_time`),
  KEY `idx_alarm_batch` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警表';

-- =============================================
-- 6. 环境监测模块
-- =============================================

-- 6.1 环境监测点表
DROP TABLE IF EXISTS `t_env_monitor_point`;
CREATE TABLE `t_env_monitor_point` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `point_code` VARCHAR(50) NOT NULL COMMENT '监测点编码(唯一)',
  `point_name` VARCHAR(50) NOT NULL COMMENT '监测点名称',
  `device_id` BIGINT(20) NOT NULL COMMENT '所属设备ID(关联t_device.id)',
  `monitor_type` VARCHAR(20) NOT NULL COMMENT '监测类型(固定为env)',
  `monitor_sub_type` VARCHAR(30) NOT NULL COMMENT '监测子类型(PM2.5/PM10/噪声/温度/湿度/风速)',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位',
  `warn_min` DECIMAL(12,2) DEFAULT NULL COMMENT '预警下限',
  `warn_max` DECIMAL(12,2) DEFAULT NULL COMMENT '预警上限',
  `alarm_min` DECIMAL(12,2) DEFAULT NULL COMMENT '报警下限',
  `alarm_max` DECIMAL(12,2) DEFAULT NULL COMMENT '报警上限',
  `status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '状态(1-启用,0-禁用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_env_point_code` (`point_code`),
  KEY `idx_env_point_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境监测点表';

-- 6.2 环境数据表
DROP TABLE IF EXISTS `t_env_data`;
CREATE TABLE `t_env_data` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `point_id` BIGINT(20) NOT NULL COMMENT '监测点ID(关联t_env_monitor_point.id)',
  `index_value` DECIMAL(12,2) NOT NULL COMMENT '监测值',
  `collect_time` DATETIME NOT NULL COMMENT '监测时间(采集时刻)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_env_point_time` (`point_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境数据表';

-- 6.3 环境日统计表
DROP TABLE IF EXISTS `t_env_daily_stat`;
CREATE TABLE `t_env_daily_stat` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `point_id` BIGINT(20) NOT NULL COMMENT '监测点ID(关联t_env_monitor_point.id)',
  `stat_date` DATE NOT NULL COMMENT '统计日期(如2026-08-04)',
  `max_value` DECIMAL(12,2) NOT NULL COMMENT '当日最大值',
  `min_value` DECIMAL(12,2) NOT NULL COMMENT '当日最小值',
  `avg_value` DECIMAL(12,2) NOT NULL COMMENT '当日平均值',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_env_daily_point_date` (`point_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境日统计表';

-- =============================================
-- 7. 喷淋降尘模块
-- =============================================

-- 7.1 喷淋定时任务表
DROP TABLE IF EXISTS `t_spray_task`;
CREATE TABLE `t_spray_task` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_name` VARCHAR(50) NOT NULL COMMENT '任务名称',
  `location_id` BIGINT(20) NOT NULL COMMENT '执行位置ID(关联t_device_location.id)',
  `start_time` TIME NOT NULL COMMENT '开始时间(每天)',
  `duration` INT(11) NOT NULL COMMENT '持续时长(分钟)',
  `period_value` INT(11) NOT NULL COMMENT '执行周期数值(如10)',
  `period_unit` VARCHAR(20) NOT NULL COMMENT '周期单位(如分钟)',
  `status` TINYINT(4) NOT NULL DEFAULT '1' COMMENT '启用状态(1-启用,0-禁用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_spray_task_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喷淋定时任务表';

-- 7.2 喷淋操作记录表
DROP TABLE IF EXISTS `t_spray_record`;
CREATE TABLE `t_spray_record` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `point_id` BIGINT(20) DEFAULT NULL COMMENT '关联监测点ID(自动联动时记录)',
  `location_id` BIGINT(20) NOT NULL COMMENT '喷淋位置ID(关联t_device_location.id)',
  `device_id` BIGINT(20) DEFAULT NULL COMMENT '喷淋设备ID(关联t_device.id)',
  `trigger_type` TINYINT(4) NOT NULL COMMENT '触发类型(1-自动联动,2-定时任务,3-手动触发)',
  `action` TINYINT(4) NOT NULL COMMENT '操作类型(1-启动,2-关闭)',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '操作原因',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人(手动时记录)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_spray_record_time` (`location_id`, `create_time`),
  KEY `idx_spray_record_point` (`point_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喷淋操作记录表';

-- =============================================
-- 8. IoT与日志模块
-- =============================================

-- 8.1 IoT智能硬件数据表
DROP TABLE IF EXISTS `t_iot_data`;
CREATE TABLE `t_iot_data` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_tag` VARCHAR(20) NOT NULL COMMENT '设备标识(its/shs/ic/ax)',
  `data_sub_type` VARCHAR(20) DEFAULT NULL COMMENT '数据子类型(如IA1)',
  `payload` TEXT NOT NULL COMMENT '上报数据(JSON格式)',
  `report_time` DATETIME NOT NULL COMMENT '上报时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_iot_tag_time` (`device_tag`, `report_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT智能硬件数据表';

-- 8.2 操作日志表
DROP TABLE IF EXISTS `t_operation_log`;
CREATE TABLE `t_operation_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT(20) DEFAULT NULL COMMENT '用户ID(关联t_sys_user.id)',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `module` VARCHAR(50) NOT NULL COMMENT '操作模块(如告警管理/喷淋控制)',
  `action` VARCHAR(50) NOT NULL COMMENT '操作动作(如处置告警/启停喷淋)',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '操作内容',
  `ip` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operlog_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 恢复外键约束检查
SET FOREIGN_KEY_CHECKS = 1;