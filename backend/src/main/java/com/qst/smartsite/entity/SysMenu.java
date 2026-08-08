package com.qst.smartsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单权限表 t_sys_menu
 */
@Data
@TableName("t_sys_menu")
public class SysMenu {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 上级菜单ID(0表示根节点) */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 菜单编码 */
    private String menuCode;

    /** 菜单类型(1-目录,2-菜单,3-按钮) */
    private Integer menuType;

    /** 路由路径 */
    private String path;

    /** 图标 */
    private String icon;

    /** 排序号 */
    private Integer sort;

    /** 状态(1-启用,0-禁用) */
    private Integer status;

    private LocalDateTime createTime;
}
