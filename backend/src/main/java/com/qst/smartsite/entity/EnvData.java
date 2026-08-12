package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 环境数据表 t_env_data
 */
@Data
@TableName("t_env_data")
public class EnvData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 监测点ID */
    private Long pointId;

    private BigDecimal indexValue;

    private LocalDateTime collectTime;

    private LocalDateTime createTime;
}
