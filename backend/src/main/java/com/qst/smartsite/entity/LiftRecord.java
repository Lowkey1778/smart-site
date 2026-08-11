package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 升降机作业记录表 t_lift_record（T-15 / RQ-20）
 */
@Data
@TableName("t_lift_record")
public class LiftRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 载重(kg) */
    private BigDecimal loadWeight;

    /** 载人数 */
    private Integer personCount;

    private Integer startFloor;

    private Integer endFloor;

    private BigDecimal windSpeed;

    /** 运行速度(m/s) */
    private BigDecimal runSpeed;

    private BigDecimal tiltAngleX;

    private BigDecimal tiltAngleY;

    private BigDecimal startHeight;

    private BigDecimal endHeight;

    /** 1-上升 2-下降 */
    private Integer direction;

    private String remark;

    private LocalDateTime createTime;
}
