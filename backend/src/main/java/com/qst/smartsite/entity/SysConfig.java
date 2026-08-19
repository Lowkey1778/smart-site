package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置表 t_sys_config（Coze 智能体接入配置等）
 */
@Data
@TableName("t_sys_config")
public class SysConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    private String remark;

    private LocalDateTime updateTime;
}
