package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 喷淋定时任务表 t_spray_task
 * T-25 / RQ-33：定时执行位置/时间/时长/周期配置
 */
@Data
@TableName("t_spray_task")
public class SprayTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 喷淋位置ID */
    private Long locationId;

    /** 开始时间（每天） */
    private LocalTime startTime;

    /** 持续时长（分钟） */
    private Integer duration;

    /** 周期值（与周期单位配合，如每 1 天） */
    private Integer periodValue;

    /** 周期单位：day-天 week-周 */
    private String periodUnit;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    private LocalDateTime createTime;
}