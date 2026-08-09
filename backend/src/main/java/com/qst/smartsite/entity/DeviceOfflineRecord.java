package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备离线记录表 t_device_offline_record
 */
@Data
@TableName("t_device_offline_record")
public class DeviceOfflineRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 状态变更(1-离线,2-上线) */
    private Integer status;

    /** 状态变更时刻 */
    private LocalDateTime recordTime;

    private LocalDateTime createTime;
}
