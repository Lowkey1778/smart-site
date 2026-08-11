package com.qst.smartsite.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 升降机实时监控状态
 */
@Data
public class LiftStatusVO {

    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    /** 1-在线 0-离线 */
    private Integer status;

    // 基础参数
    private BigDecimal ratedLoad;      // 额定载荷(kg)
    private BigDecimal maxLiftHeight;  // 最大提升高度(m)
    private BigDecimal liftSpeed;      // 提升速度(m/s)
    private BigDecimal baseHeight;

    // 实时参数
    private BigDecimal loadWeight;     // 载重(kg)
    private Integer personCount;       // 载人数
    private BigDecimal height;         // 当前高度(m)
    private BigDecimal windSpeed;      // 风速(m/s)
    private Integer direction;         // 1-上升 2-下降
    private Integer doorFront;         // 前门锁(1-关 0-开)
    private Integer doorBack;          // 后门锁

    /** 载重百分比 */
    public BigDecimal getLoadPercent() {
        if (loadWeight == null || ratedLoad == null || ratedLoad.compareTo(BigDecimal.ZERO) == 0) return null;
        return loadWeight.divide(ratedLoad, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
