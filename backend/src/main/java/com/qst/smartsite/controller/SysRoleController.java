package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.SysMenu;
import com.qst.smartsite.entity.SysRole;
import com.qst.smartsite.entity.SysRoleMenu;
import com.qst.smartsite.entity.SysUserRole;
import com.qst.smartsite.mapper.SysMenuMapper;
import com.qst.smartsite.mapper.SysRoleMapper;
import com.qst.smartsite.mapper.SysRoleMenuMapper;
import com.qst.smartsite.mapper.SysUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理接口（T-02）
 * 对应《页面功能清单》1.2 权限管理页面：角色新增/编辑、菜单与操作权限分配
 */
@RestController
@RequestMapping("/api/sys/role")
public class SysRoleController {

    /** 内置角色ID：系统管理员（禁止删除） */
    private static final long BUILTIN_ADMIN_ROLE_ID = 1L;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private com.qst.smartsite.mapper.OperationLogMapper operationLogMapper;

    /** 角色列表（含已分配菜单ID，供编辑回显） */
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        for (SysRole role : roles) {
            role.setMenuIds(sysRoleMapper.selectMenuIdsByRoleId(role.getId()));
        }
        return Result.ok(roles);
    }

    /** 菜单树（全部启用菜单，供角色分配勾选） */
    @GetMapping("/menu-tree")
    public Result<List<Map<String, Object>>> menuTree() {
        List<SysMenu> menus = sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getStatus, 1)
                        .orderByAsc(SysMenu::getSort));
        return Result.ok(buildTree(menus, 0L));
    }

    /** 新增角色（可同时分配菜单） */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> add(@RequestBody SysRole role) {
        if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
            throw new BusinessException(400, "角色编码不能为空");
        }
        Long exists = sysRoleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.getRoleCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "角色编码已存在");
        }
        if (role.getStatus() == null) role.setStatus(1);
        sysRoleMapper.insert(role);
        saveRoleMenus(role.getId(), role.getMenuIds());
        log(1L, "admin", "角色管理", "新增角色", "新增角色[" + role.getRoleCode() + "]");
        return Result.ok();
    }

    /** 编辑角色（基本信息 + 菜单权限分配，先删后插） */
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        SysRole db = sysRoleMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "角色不存在");
        }
        role.setId(id);
        role.setRoleCode(null); // 编码为唯一标识，不允许修改
        sysRoleMapper.updateById(role);
        saveRoleMenus(id, role.getMenuIds());
        log(1L, "admin", "角色管理", "编辑角色", "编辑角色ID[" + id + "]");
        return Result.ok();
    }

    /** 删除角色（同时删除角色-菜单、用户-角色关联；内置角色禁止删除） */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        if (id <= BUILTIN_ADMIN_ROLE_ID) {
            throw new BusinessException(400, "内置角色不允许删除");
        }
        sysRoleMapper.deleteById(id);
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        log(1L, "admin", "角色管理", "删除角色", "删除角色ID[" + id + "]");
        return Result.ok();
    }

    /** 保存角色菜单关联（先删后插） */
    private void saveRoleMenus(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            if (menuId == null) continue;
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            sysRoleMenuMapper.insert(rm);
        }
    }

    /** 构建菜单树（menuType=1 目录可含子节点；type=2 为叶子菜单） */
    private List<Map<String, Object>> buildTree(List<SysMenu> menus, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SysMenu m : menus) {
            if (!parentId.equals(m.getParentId())) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", m.getId());
            node.put("parentId", m.getParentId());
            node.put("menuName", m.getMenuName());
            node.put("menuCode", m.getMenuCode());
            node.put("menuType", m.getMenuType());
            node.put("path", m.getPath());
            node.put("icon", m.getIcon());
            List<Map<String, Object>> children = buildTree(menus, m.getId());
            if (!children.isEmpty()) {
                node.put("children", children);
            }
            nodes.add(node);
        }
        return nodes;
    }
    /** 写操作日志（T-36） */
    private void log(Long userId, String username, String module, String action, String content) {
        try {
            com.qst.smartsite.entity.OperationLog log = new com.qst.smartsite.entity.OperationLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setModule(module);
            log.setAction(action);
            log.setContent(content);
            try {
                org.springframework.web.context.request.ServletRequestAttributes attrs =
                        (org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (attrs != null) log.setIp(attrs.getRequest().getRemoteAddr());
            } catch (Exception ignored) {
            }
            operationLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }
}
