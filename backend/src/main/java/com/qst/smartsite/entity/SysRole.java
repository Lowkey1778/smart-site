package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统角色表 t_sys_role
 */
@Data
@TableName("t_sys_role")
public class SysRole {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色编码(ADMIN/LEADER/SAFETY) */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

    /** 角色状态(1-启用,0-禁用) */
    private Integer status;

    private LocalDateTime createTime;

    /** 非表字段：角色已分配的菜单ID集合（编辑回显用） */
    @TableField(exist = false)
    private List<Long> menuIds;
}
