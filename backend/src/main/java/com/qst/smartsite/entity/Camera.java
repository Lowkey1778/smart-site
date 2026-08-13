package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 摄像头表 t_camera
 */
@Data
@TableName("t_camera")
public class Camera {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 摄像头编码(唯一) */
    private String cameraCode;

    /** 摄像头名称 */
    private String cameraName;

    /** 安装位置ID */
    private Long locationId;

    /** 视频播放地址(rtmp或hls) */
    private String streamUrl;

    /** 在线状态(1-在线,0-离线) */
    private Integer onlineStatus;

    /** 启用状态(1-启用,0-禁用) */
    private Integer enableStatus;

    /** AI识别开关 */
    private Integer aiHelmet;
    private Integer aiVest;
    private Integer aiSmoke;
    private Integer aiFire;

    private LocalDateTime createTime;
}
