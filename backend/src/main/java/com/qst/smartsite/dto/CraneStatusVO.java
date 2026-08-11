package com.qst.smartsite.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 塔吊实时监控状态（列表/详情共用）
 * 力矩 = 吊重 × 幅度，实时计算
 */
@Data
public class CraneStatusVO {

    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    /** 1-在线 0-离线 */
    private Integer status;

    // 基础参数
    private BigDecimal frontArmLen;
    private BigDecimal maxHeight;
    private BigDecimal ratedLoad;
    private BigDecimal maxLoad;
    private BigDecimal ratedMoment;

    // 实时参数
    private BigDecimal loadVal;        // 吊重(t)
    private BigDecimal radiusVal;      // 幅度(m)
    private BigDecimal windSpeed;      // 风速(m/s)
    private BigDecimal height;         // 吊钩高度(m)
    private BigDecimal angle;          // 回转角度(°)

    /** 力矩 = 吊重 × 幅度 (实时计算) */
    public BigDecimal getMoment() {
        if (loadVal == null || radiusVal == null) return null;
        return loadVal.multiply(radiusVal).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 力矩占额定力矩百分比 */
    public BigDecimal getMomentPercent() {
        BigDecimal moment = getMoment();
        if (moment == null || ratedMoment == null || ratedMoment.compareTo(BigDecimal.ZERO) == 0) return null;
        return moment.divide(ratedMoment, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
