package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartsite.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户角色编码列表（多对多：t_sys_user_role -> t_sys_role）
     */
    @Select("SELECT r.role_code FROM t_sys_role r " +
            "JOIN t_sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodes(Long userId);

    /**
     * 查询用户角色ID列表
     */
    @Select("SELECT role_id FROM t_sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(Long userId);

    /**
     * 查询用户角色信息（id/code/name），用于列表回显
     */
    @Select("SELECT r.id, r.role_code AS roleCode, r.role_name AS roleName " +
            "FROM t_sys_role r JOIN t_sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<Map<String, Object>> selectRolesByUserId(Long userId);

    /**
     * 查询用户可访问的菜单（按角色关联去重），仅启用状态
     */
    @Select("SELECT DISTINCT m.* FROM t_sys_menu m " +
            "JOIN t_sys_role_menu rm ON m.id = rm.menu_id " +
            "JOIN t_sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 " +
            "ORDER BY m.sort")
    List<com.qst.smartsite.entity.SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}
