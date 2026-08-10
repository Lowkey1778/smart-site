package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IoT 上报数据表 t_iot_data（设备通信模拟平台接入，接口章节 4.2）
 */
@Data
@TableName("t_iot_data")
public class IotData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备标识(its-塔吊/shs-升降机/ic-环境监测/ax-其他) */
    private String deviceTag;

    /** 数据子类型(如 crane/lift/env) */
    private String dataSubType;

    /** 上报报文(JSON 原文) */
    private String payload;

    /** 上报时间 */
    private LocalDateTime reportTime;

    private LocalDateTime createTime;
}
