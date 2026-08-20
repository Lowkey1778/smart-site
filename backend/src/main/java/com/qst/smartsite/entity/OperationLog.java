package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志表 t_operation_log（T-36 / 3.4节）
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 模块（如 认证/用户管理/告警处置/喷淋控制） */
    private String module;

    /** 操作（如 登录/新增/删除/开始处置/手动开启） */
    private String action;

    private String content;

    private String ip;

    private LocalDateTime createTime;
}
