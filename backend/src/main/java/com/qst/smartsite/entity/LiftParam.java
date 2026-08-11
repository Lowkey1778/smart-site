package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 升降机基础参数表 t_lift_param
 */
@Data
@TableName("t_lift_param")
public class LiftParam {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 升降机设备ID */
    private Long deviceId;

    /** 额定重量(t) */
    private BigDecimal ratedWeight;

    /** 基础高度(m) */
    private BigDecimal baseHeight;

    /** 提升速度(m/s) */
    private BigDecimal liftSpeed;

    /** 额定载荷(kg) */
    private BigDecimal ratedLoad;

    /** 吊笼尺寸(m²) */
    private BigDecimal cageSize;

    /** 最大提升高度(m) */
    private BigDecimal maxLiftHeight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
