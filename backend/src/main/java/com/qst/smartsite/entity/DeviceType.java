package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备类型表 t_device_type（树形）
 */
@Data
@TableName("t_device_type")
public class DeviceType {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 上级类型ID(0表示根节点) */
    private Long parentId;

    /** 类型名称 */
    private String typeName;

    /** 排序号 */
    private Integer sort;

    private LocalDateTime createTime;
}
