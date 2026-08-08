package com.qst.smartsite.config;

import com.qst.smartsite.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * JWT 登录拦截器：校验请求头 Authorization: Bearer <token>
 * 通过后把 userId / username 放入 request attribute，供 Controller 使用
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired(required = false)
    private com.qst.smartsite.service.RedisCacheService redisCacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                // T-34 Redis 会话校验：Redis 在线时要求 token 在会话表中；Redis 不可用降级放行
                if (redisCacheService != null && !redisCacheService.isSessionValid(token)) {
                    response.setStatus(401);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType("application/json;charset=UTF-8");
                    ObjectMapper mapper = new ObjectMapper();
                    response.getWriter().write(mapper.writeValueAsString(Result.fail(401, "会话已失效，请重新登录")));
                    return false;
                }
                request.setAttribute("userId", Long.valueOf(claims.getSubject()));
                request.setAttribute("username", claims.get("username", String.class));
                return true;
            } catch (Exception ignored) {
                // token 无效/过期，走下方 401
            }
        }
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(Result.fail(401, "未登录或登录已过期")));
        return false;
    }
}
