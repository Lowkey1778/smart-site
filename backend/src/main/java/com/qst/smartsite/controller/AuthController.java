package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.config.JwtUtil;
import com.qst.smartsite.dto.ChangePasswordRequest;
import com.qst.smartsite.dto.LoginRequest;
import com.qst.smartsite.dto.LoginResponse;
import com.qst.smartsite.entity.SysMenu;
import com.qst.smartsite.entity.SysUser;
import com.qst.smartsite.mapper.SysUserMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证接口：登录 / 退出 / 当前用户信息 / 当前用户菜单
 * 对应《接口设计》4.2.1 系统登录与权限接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private com.qst.smartsite.service.RedisCacheService redisCacheService;
    @Autowired
    private com.qst.smartsite.mapper.OperationLogMapper operationLogMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 用户登录 POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        List<String> roles = sysUserMapper.selectRoleCodes(user.getId());

        // T-34 Redis 会话存储：登录成功后写入 Redis（TTL 与 JWT 一致）
        try {
            redisCacheService.saveSession(token, user.getId(), jwtUtil.getExpireHours());
        } catch (Exception ignored) {
        }

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoles(roles);
        log(user.getId(), user.getUsername(), "认证", "登录", "用户[" + user.getUsername() + "]登录系统");
        return Result.ok(resp);
    }

    /**
     * 获取当前登录用户信息 GET /api/auth/info
     */
    @GetMapping("/info")
    public Result<SysUser> info(@RequestAttribute("userId") Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setPassword(null); // 不返回密码
        }
        return Result.ok(user);
    }

    /**
     * 获取当前用户可访问菜单树 GET /api/auth/menus
     * 依据：用户角色 → 角色菜单权限（t_sys_role_menu）→ 去重菜单
     * 仅返回目录/菜单（menuType 1/2），按钮（menuType 3）不进入侧边菜单
     */
    @GetMapping("/menus")
    public Result<List<Map<String, Object>>> menus(@RequestAttribute("userId") Long userId) {
        List<SysMenu> menus = sysUserMapper.selectMenusByUserId(userId);
        return Result.ok(buildTree(menus, 0L));
    }

    /**
     * 获取当前用户按钮操作权限集合 GET /api/auth/perms
     * 返回按钮权限编码列表（如 sys:user:add），供前端 v-permission 控制按钮显隐
     */
    @GetMapping("/perms")
    public Result<List<String>> perms(@RequestAttribute("userId") Long userId) {
        List<SysMenu> menus = sysUserMapper.selectMenusByUserId(userId);
        return Result.ok(menus.stream()
                .filter(m -> m.getMenuType() != null && m.getMenuType() == 3)
                .map(SysMenu::getMenuCode)
                .distinct()
                .collect(Collectors.toList()));
    }

    /**
     * 退出登录 POST /api/auth/logout
     * 删除 Redis 会话（服务端失效），前端同时清理本地 token
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(value = "userId", required = false) Long userId,
                               @RequestAttribute(value = "username", required = false) String username,
                               @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            redisCacheService.removeSession(auth.substring(7));
        }
        log(userId, username, "认证", "退出登录", "用户[" + (username == null ? "?" : username) + "]退出系统");
        return Result.ok();
    }

    /**
     * Redis 状态（T-34 / 接口章节 4.6）：是否在线、在线会话数、实时数据缓存示例
     */
    @GetMapping("/redis-status")
    public Result<Map<String, Object>> redisStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        long online = redisCacheService.onlineCount();
        data.put("redisOnline", online >= 0);
        data.put("onlineSessions", online < 0 ? 0 : online);
        // 实时数据缓存示例（设备 1 吊重）
        String sample = redisCacheService.getLatest(1L, "load");
        data.put("cacheSample", sample == null ? null : "device1:load = " + sample + " t");
        return Result.ok(data);
    }

    /**
     * 当前用户修改/重置自己的密码 POST /api/auth/change-password
     * 校验原密码 → BCrypt 加密新密码 → 实时更新 t_sys_user
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestAttribute("userId") Long userId,
                                       @RequestBody ChangePasswordRequest req) {
        if (req.getOldPassword() == null || req.getOldPassword().isBlank()) {
            throw new BusinessException(400, "请输入原密码");
        }
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码长度不能少于 6 位");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!encoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码不正确");
        }
        if (encoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        SysUser upd = new SysUser();
        upd.setId(userId);
        upd.setPassword(encoder.encode(req.getNewPassword()));
        sysUserMapper.updateById(upd);
        return Result.ok();
    }

    /** 构建菜单树（跳过按钮节点） */
    private List<Map<String, Object>> buildTree(List<SysMenu> menus, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SysMenu m : menus) {
            if (m.getMenuType() != null && m.getMenuType() == 3) continue; // 按钮不进菜单树
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
    /** 写操作日志（T-36）：userId/username 可为空（如登录前） */
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
