package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警表 t_alarm
 */
@Data
@TableName("t_alarm")
public class Alarm {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 告警编号 */
    private String alarmNo;

    /** 批次号(同一事件多个监测点共享) */
    private String batchNo;

    /** 告警来源(1-设备监测,2-环境监测,3-AI识别) */
    private Integer alarmSource;

    /** 告警级别(1-预警,2-警报,3-控制) */
    private Integer alarmLevel;

    private Long deviceId;

    private Long pointId;

    private Long cameraId;

    /** 告警截图URL(AI告警) */
    private String imageUrl;

    private String alarmContent;

    private BigDecimal alarmValue;

    private LocalDateTime alarmTime;

    /** 处置状态(0-未处置,1-处置中,2-已处置) */
    private Integer handleStatus;

    private String handlePerson;

    private String handleMeasure;

    private String handleConclusion;

    private LocalDateTime handleTime;

    private LocalDateTime recoverTime;

    private BigDecimal recoverValue;

    private LocalDateTime createTime;
}
