package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备位置表 t_device_location（树形）
 */
@Data
@TableName("t_device_location")
public class DeviceLocation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 上级位置ID(0表示根节点) */
    private Long parentId;

    /** 位置名称 */
    private String locationName;

    /** 排序号 */
    private Integer sort;

    private LocalDateTime createTime;
}
