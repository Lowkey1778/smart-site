package com.qst.smartsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 缓存与会话服务（T-34 / 接口章节 4.6）
 * -------------------------------------------------
 * 1. 会话存储：登录签发 JWT 后写入 Redis（token -> userId，TTL 24h），
 *    退出登录删除；JwtInterceptor 校验时校验 Redis 会话（服务端可控失效）。
 * 2. 实时数据缓存：模拟器/设备上报的最新值同步写 Redis，
 *    查询侧可优先读缓存（当前查询仍走数据库，缓存用于展示与扩展）。
 *
 * 降级策略：Redis 不可用时所有操作静默失败，系统退化为原有 JWT 无状态模式，
 * 保证演示环境不因 Redis 故障而中断。
 */
@Service
public class RedisCacheService {

    private static final String SESSION_PREFIX = "smart:session:";
    private static final String RT_PREFIX = "smart:rt:";
    private static final String ALARM_CNT_KEY = "smart:alarm:unhandled";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /* ==================== 会话存储 ==================== */

    /** 登录成功后写入会话（TTL 与 JWT 一致 24 小时） */
    public void saveSession(String token, Long userId, long expireHours) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(SESSION_PREFIX + token, String.valueOf(userId),
                        Duration.ofHours(expireHours));
            }
        } catch (Exception ignored) {
        }
    }

    /** 会话是否有效（token 已签名且存在于 Redis） */
    public boolean isSessionValid(String token) {
        try {
            if (redisTemplate == null) return true; // Redis 不可用降级放行
            return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_PREFIX + token));
        } catch (Exception e) {
            return true; // 降级放行
        }
    }

    /** 退出登录删除会话 */
    public void removeSession(String token) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(SESSION_PREFIX + token);
            }
        } catch (Exception ignored) {
        }
    }

    /** 会话在线数 */
    public long onlineCount() {
        try {
            if (redisTemplate == null) return -1;
            var keys = redisTemplate.keys(SESSION_PREFIX + "*");
            return keys == null ? 0 : keys.size();
        } catch (Exception e) {
            return -1;
        }
    }

    /* ==================== 实时数据缓存 ==================== */

    /** 缓存设备最新实时值（5 秒过期兜底，避免脏数据） */
    public void cacheLatest(Long deviceId, String paramCode, String value) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(RT_PREFIX + deviceId + ":" + paramCode, value,
                        Duration.ofSeconds(30));
            }
        } catch (Exception ignored) {
        }
    }

    /** 读取设备最新实时值缓存 */
    public String getLatest(Long deviceId, String paramCode) {
        try {
            if (redisTemplate == null) return null;
            return redisTemplate.opsForValue().get(RT_PREFIX + deviceId + ":" + paramCode);
        } catch (Exception e) {
            return null;
        }
    }

    /** 未处理告警数缓存（首页/角标高频读取） */
    public void cacheUnhandledAlarmCount(Long count) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(ALARM_CNT_KEY, String.valueOf(count), Duration.ofSeconds(10));
            }
        } catch (Exception ignored) {
        }
    }

    public String getUnhandledAlarmCount() {
        try {
            if (redisTemplate == null) return null;
            return redisTemplate.opsForValue().get(ALARM_CNT_KEY);
        } catch (Exception e) {
            return null;
        }
    }
}
