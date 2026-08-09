package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账表 t_device（核心）
 */
@Data
@TableName("t_device")
public class Device {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备编码(唯一) */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型ID */
    private Long typeId;

    /** 安装位置ID */
    private Long locationId;

    /** 品牌/厂家 */
    private String brand;

    /** 型号 */
    private String model;

    /** 供应商 */
    private String supplier;

    /** 二维码编号 */
    private String qrCode;

    /** 生产日期 */
    private LocalDate produceDate;

    /** 供货日期 */
    private LocalDate supplyDate;

    /** 验收日期 */
    private LocalDate acceptDate;

    /** 安装日期 */
    private LocalDate installDate;

    /** 启用日期 */
    private LocalDate enableDate;

    /** 设计使用年限(年) */
    private Integer designServiceLife;

    /** 预计报废日期 */
    private LocalDate expectScrapDate;

    /** 实际报废日期 */
    private LocalDate actualScrapDate;

    /** 最近维修日期 */
    private LocalDate lastMaintainDate;

    /** 设备原值(元) */
    private BigDecimal originalValue;

    /** 设备图片URL */
    private String deviceImage;

    /** 平面坐标 */
    private String coordinate;

    /** 运行状态(1-在线,0-离线) */
    private Integer status;

    /** 启用状态(1-启用,0-禁用) */
    private Integer enableStatus;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 非表字段：设备类型名称（列表/详情回填） */
    @TableField(exist = false)
    private String typeName;

    /** 非表字段：安装位置名称 */
    @TableField(exist = false)
    private String locationName;
}
