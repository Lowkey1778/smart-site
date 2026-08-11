package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊基础参数表 t_tower_crane_param
 */
@Data
@TableName("t_tower_crane_param")
public class TowerCraneParam {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 塔吊设备ID */
    private Long deviceId;

    /** 前臂长(m) */
    private BigDecimal frontArmLen;

    /** 后臂长(m) */
    private BigDecimal rearArmLen;

    /** 最大高度(m) */
    private BigDecimal maxHeight;

    /** 额定载荷(t) */
    private BigDecimal ratedLoad;

    /** 最大载荷(t) */
    private BigDecimal maxLoad;

    /** 额定力矩(t·m) */
    private BigDecimal ratedMoment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
