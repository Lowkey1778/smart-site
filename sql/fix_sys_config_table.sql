-- 补齐缺失的 t_sys_config 表（Coze 智能体接入配置存储，init_data.sql 未执行导致缺失）
USE `smart_site`;
CREATE TABLE IF NOT EXISTS `t_sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(50) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `remark` varchar(200) DEFAULT NULL COMMENT '说明',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';
INSERT IGNORE INTO `t_sys_config` (`config_key`, `config_value`, `remark`) VALUES
('coze.api_token', '', 'Coze API Token'),
('coze.bot_id', '', 'Coze Bot ID'),
('coze.base_url', 'https://api.coze.cn', 'Coze 接口地址(国内 api.coze.cn / 海外 api.coze.com)');
