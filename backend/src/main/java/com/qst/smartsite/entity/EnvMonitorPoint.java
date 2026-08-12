package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 环境监测点表 t_env_monitor_point
 */
@Data
@TableName("t_env_monitor_point")
public class EnvMonitorPoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String pointCode;

    private String pointName;

    /** 所属设备ID */
    private Long deviceId;

    /** 监测类型(固定env) */
    private String monitorType;

    /** 监测子类型(PM2.5/PM10/噪声/温度/湿度/风速) */
    private String monitorSubType;

    private String unit;

    private BigDecimal warnMin;

    private BigDecimal warnMax;

    private BigDecimal alarmMin;

    private BigDecimal alarmMax;

    private Integer status;

    private LocalDateTime createTime;
}
