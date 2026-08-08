package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.SysUser;
import com.qst.smartsite.entity.SysUserRole;
import com.qst.smartsite.mapper.SysUserMapper;
import com.qst.smartsite.mapper.SysUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理接口（T-01）
 * 对应《页面功能清单》1.2 权限管理页面：用户新增/编辑/删除/查询、分配角色、重置密码
 */
@RestController
@RequestMapping("/api/sys/user")
public class SysUserController {

    /** 内置管理员ID（admin 账号，禁止删除） */
    private static final long BUILTIN_ADMIN_ID = 1L;
    /** 默认初始密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private com.qst.smartsite.mapper.OperationLogMapper operationLogMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 用户分页查询（支持关键字/状态筛选） */
    @GetMapping("/page")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer status) {
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysUser>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getRealName, keyword)
                                .or().like(SysUser::getPhone, keyword))
                        .eq(status != null, SysUser::getStatus, status)
                        .orderByAsc(SysUser::getId));
        // 回填角色信息并清除密码
        for (SysUser u : page.getRecords()) {
            fillRoles(u);
            u.setPassword(null);
        }
        return Result.ok(page);
    }

    /** 用户详情（含角色ID集合） */
    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        fillRoles(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    /** 新增用户（默认密码 123456，可分配角色） */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> add(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BusinessException(400, "登录账号不能为空");
        }
        Long exists = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "登录账号已存在");
        }
        user.setId(null);
        user.setPassword(encoder.encode(user.getPassword() == null || user.getPassword().isBlank()
                ? DEFAULT_PASSWORD : user.getPassword()));
        if (user.getStatus() == null) user.setStatus(1);
        if (user.getLocked() == null) user.setLocked(0);
        sysUserMapper.insert(user);
        saveUserRoles(user.getId(), user.getRoleIds());
        log(1L, "admin", "用户管理", "新增用户", "新增用户[" + user.getUsername() + "]");
        return Result.ok();
    }

    /** 编辑用户（基本信息 + 角色分配，先删后插） */
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        SysUser db = sysUserMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 不允许改账号名（唯一标识），其余基本信息可改
        user.setId(id);
        user.setUsername(null);
        user.setPassword(null);
        sysUserMapper.updateById(user);
        saveUserRoles(id, user.getRoleIds());
        log(1L, "admin", "用户管理", "编辑用户", "编辑用户ID[" + id + "]");
        return Result.ok();
    }

    /** 删除用户（同时删除用户-角色关联；内置 admin 禁止删除） */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        if (id == BUILTIN_ADMIN_ID) {
            throw new BusinessException(400, "内置系统管理员账号不允许删除");
        }
        sysUserMapper.deleteById(id);
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        log(1L, "admin", "用户管理", "删除用户", "删除用户ID[" + id + "]");
        return Result.ok();
    }

    /** 重置密码（body.password 为空时重置为默认 123456） */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) SysUser req) {
        SysUser db = sysUserMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "用户不存在");
        }
        String newPwd = (req != null && req.getPassword() != null && !req.getPassword().isBlank())
                ? req.getPassword() : DEFAULT_PASSWORD;
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setPassword(encoder.encode(newPwd));
        sysUserMapper.updateById(upd);
        return Result.ok();
    }

    /** 启用/禁用账号 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object status = body.get("status");
        if (status == null) {
            throw new BusinessException(400, "缺少状态参数");
        }
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setStatus(Integer.valueOf(status.toString()));
        sysUserMapper.updateById(upd);
        return Result.ok();
    }

    /** 回填用户角色ID与角色名称 */
    private void fillRoles(SysUser user) {
        List<Long> roleIds = sysUserMapper.selectRoleIds(user.getId());
        user.setRoleIds(roleIds);
        List<Map<String, Object>> roles = sysUserMapper.selectRolesByUserId(user.getId());
        user.setRoleNames(roles.stream()
                .map(r -> String.valueOf(r.get("roleName")))
                .collect(Collectors.joining("、")));
    }

    /** 保存用户角色关联（先删后插） */
    private void saveUserRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            if (roleId == null) continue;
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
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
