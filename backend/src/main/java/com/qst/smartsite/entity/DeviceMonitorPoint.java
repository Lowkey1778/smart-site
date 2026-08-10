package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备监测点表 t_device_monitor_point
 */
@Data
@TableName("t_device_monitor_point")
public class DeviceMonitorPoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String pointCode;

    /** 所属设备ID */
    private Long deviceId;

    private String pointName;

    /** 监测类型(固定device) */
    private String monitorType;

    /** 监测子类型(力矩/吊重/风速等) */
    private String monitorSubType;

    private String unit;

    private BigDecimal warnMin;

    private BigDecimal warnMax;

    private BigDecimal alarmMin;

    private BigDecimal alarmMax;

    /** 是否关联喷淋 */
    private Integer sprayEnabled;

    private BigDecimal sprayOnThreshold;

    private BigDecimal sprayOffThreshold;

    private Long sprayDeviceId;

    /** 采集间隔(秒) */
    private Integer collectInterval;

    private Integer status;

    private LocalDateTime createTime;
}
