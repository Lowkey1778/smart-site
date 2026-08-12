package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 环境日统计表 t_env_daily_stat
 * 记录监测点每日最大/最小/平均值（T-23 / RQ-31）
 */
@Data
@TableName("t_env_daily_stat")
public class EnvDailyStat {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 监测点ID */
    private Long pointId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 当日最大值 */
    private BigDecimal maxValue;

    /** 当日最小值 */
    private BigDecimal minValue;

    /** 当日平均值 */
    private BigDecimal avgValue;

    private LocalDateTime createTime;
}