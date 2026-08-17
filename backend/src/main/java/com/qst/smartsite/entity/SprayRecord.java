package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 喷淋操作记录表 t_spray_record
 * T-24 / RQ-32：手动/定时/自动联动的喷淋开启与关闭历史
 */
@Data
@TableName("t_spray_record")
public class SprayRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联监测点ID（自动联动时记录触发监测点） */
    private Long pointId;

    /** 喷淋位置ID */
    private Long locationId;

    /** 关联设备ID（喷淋装置） */
    private Long deviceId;

    /** 触发方式：1-手动 2-定时任务 3-自动联动 */
    private Integer triggerType;

    /** 操作类型：1-开启 2-关闭 */
    private Integer action;

    /** 原因/说明 */
    private String reason;

    /** 操作人（手动时记录登录用户名） */
    private String operator;

    private LocalDateTime createTime;
}