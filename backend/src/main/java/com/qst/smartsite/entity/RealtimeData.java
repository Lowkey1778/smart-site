package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备实时数据表 t_realtime_data
 */
@Data
@TableName("t_realtime_data")
public class RealtimeData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    /** 监测点ID */
    private Long pointId;

    /** 参数编码(load/radius/wind_speed/height/angle/load_weight/person_count/door_front...) */
    private String paramCode;

    private BigDecimal paramValue;

    private String unit;

    private LocalDateTime collectTime;

    private LocalDateTime createTime;
}
