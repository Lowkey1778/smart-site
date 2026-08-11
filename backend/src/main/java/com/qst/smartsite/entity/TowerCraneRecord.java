package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊作业记录表 t_tower_crane_record（T-14 / RQ-16）
 */
@Data
@TableName("t_tower_crane_record")
public class TowerCraneRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 吊重(t) */
    private BigDecimal hoistingWeight;

    /** 最大载荷百分比(%) */
    private BigDecimal maxLoadPercent;

    private BigDecimal maxRadius;

    private BigDecimal minRadius;

    private BigDecimal maxHeight;

    private BigDecimal minHeight;

    private BigDecimal maxWindSpeed;

    private BigDecimal maxLoad;

    private BigDecimal startAngle;

    private BigDecimal endAngle;

    /** 吊点位置：幅度(m) */
    private BigDecimal hookRadius;

    /** 吊点位置：高度(m) */
    private BigDecimal hookHeight;

    /** 卸料位置：幅度(m) */
    private BigDecimal unloadRadius;

    /** 卸料位置：高度(m) */
    private BigDecimal unloadHeight;

    private String remark;

    private LocalDateTime createTime;
}
