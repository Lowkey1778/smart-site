package com.qst.smartsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qst.smartsite.config.RealtimeWebSocketHandler;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.entity.IotData;
import com.qst.smartsite.entity.RealtimeData;
import com.qst.smartsite.mapper.DeviceMapper;
import com.qst.smartsite.mapper.IotDataMapper;
import com.qst.smartsite.mapper.RealtimeDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备通信接入服务（T-32 / 接口章节 4.2）
 * 接收 TCP 模拟设备上报报文，完成：
 *   1) 原始报文落库 t_iot_data（设备标识 + JSON payload）
 *   2) 解析指标写入 t_realtime_data（供监控页/告警判断复用）
 *   3) WebSocket 广播最新状态（与内置模拟器同链路）
 * 告警判断不在此处重复实现：MockDataScheduler 每 5 秒基于最新实时值统一判断，
 * 因此 TCP 上报的数据同样能触发阈值告警。
 */
@Service
public class IotDataService {

    @Autowired
    private IotDataMapper iotDataMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private RealtimeDataMapper realtimeDataMapper;
    @Autowired
    private MonitorService monitorService;
    @Autowired
    private ObjectMapper objectMapper;

    /** 设备类型 → 设备标识（t_iot_data.device_tag 约定：its-塔吊/shs-升降机/ic-环境/ax-其他） */
    private static final Map<String, String> TAG_MAP = Map.of(
            "crane", "its", "lift", "shs", "env", "ic", "spray", "ax", "other", "ax");

    /** 当前 TCP 连接统计：deviceCode -> 连接信息 */
    private final Map<String, Map<String, Object>> connections = new ConcurrentHashMap<>();
    /** 累计连接数（含历史断开） */
    private final AtomicLong totalConnections = new AtomicLong(0);
    /** 累计上报报文数 */
    private final AtomicLong totalReports = new AtomicLong(0);

    /* ==================== 手动推送（演示平台 T-32） ==================== */
    /** 周期推送调度器 */
    private final ScheduledExecutorService simExecutor = Executors.newScheduledThreadPool(2);
    /** 正在周期推送的设备：deviceCode -> 定时任务 */
    private final Map<String, ScheduledFuture<?>> simTasks = new ConcurrentHashMap<>();

    /**
     * 单次推送：按演示人给定的报文内容（deviceCode/type/data）模拟一次设备上报
     */
    public void simPushOnce(String deviceCode, String type, Object data) {
        if (deviceCode == null || deviceCode.isBlank() || data == null) {
            throw new IllegalArgumentException("缺少设备编码或上报数据");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceCode", deviceCode);
        payload.put("type", type == null || type.isBlank() ? "other" : type);
        payload.put("data", data);
        payload.put("ts", System.currentTimeMillis());
        try {
            handleReport(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            System.out.println("[IOT-SIM] 单次推送失败: " + e.getMessage());
            throw new RuntimeException("推送报文格式错误：" + e.getMessage());
        }
    }

    /**
     * 周期推送：按给定报文每 intervalSec 秒推送一次（同一设备重复调用会先停止旧的）
     */
    public void simStartPeriodic(String deviceCode, String type, Object data, long intervalSec) {
        if (intervalSec <= 0) intervalSec = 5;
        stopPeriodic(deviceCode);
        ScheduledFuture<?> future = simExecutor.scheduleAtFixedRate(() -> {
            try {
                simPushOnce(deviceCode, type, data);
            } catch (Exception e) {
                System.out.println("[IOT-SIM] 周期推送异常: " + e.getMessage());
            }
        }, 0, intervalSec, TimeUnit.SECONDS);
        simTasks.put(deviceCode, future);
    }

    /** 停止指定设备的周期推送 */
    public void stopPeriodic(String deviceCode) {
        ScheduledFuture<?> f = simTasks.remove(deviceCode);
        if (f != null) f.cancel(false);
    }

    /** 周期推送状态（演示页面展示正在推送的设备） */
    public Map<String, Object> simStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", new java.util.ArrayList<>(simTasks.keySet()));
        result.put("intervalSec", 5);
        return result;
    }

    /**
     * 处理一条设备上报报文（由 TCP 服务调用）
     *
     * @param raw 报文原文，格式：
     *            {"deviceCode":"TC-001","type":"crane","data":{"load":3.2,"radius":25.0,...}}
     */
    public void handleReport(String raw) {
        try {
            Map<String, Object> msg = objectMapper.readValue(raw, Map.class);
            String deviceCode = String.valueOf(msg.get("deviceCode"));
            String type = String.valueOf(msg.getOrDefault("type", "other"));
            Object dataObj = msg.get("data");
            if (deviceCode == null || deviceCode.equals("null") || dataObj == null) {
                return;
            }
            // 1. 原始报文落库
            IotData iot = new IotData();
            iot.setDeviceTag(TAG_MAP.getOrDefault(type, "ax"));
            iot.setDataSubType(type);
            iot.setPayload(raw);
            iot.setReportTime(LocalDateTime.now());
            iotDataMapper.insert(iot);
            totalReports.incrementAndGet();

            // 2. 设备编码 → deviceId
            Device device = deviceMapper.selectOne(
                    new LambdaQueryWrapper<Device>().eq(Device::getDeviceCode, deviceCode));
            if (device == null) {
                return; // 未知设备：仅存档报文
            }
            // 3. 指标写入实时数据表
            if (dataObj instanceof Map<?, ?> dataMap) {
                for (Map.Entry<?, ?> e : dataMap.entrySet()) {
                    String paramCode = String.valueOf(e.getKey());
                    if (paramCode == null || paramCode.equals("null")) continue;
                    try {
                        RealtimeData d = new RealtimeData();
                        d.setDeviceId(device.getId());
                        d.setParamCode(paramCode);
                        d.setParamValue(new BigDecimal(String.valueOf(e.getValue())));
                        d.setUnit(guessUnit(paramCode));
                        d.setCollectTime(LocalDateTime.now());
                        realtimeDataMapper.insert(d);
                    } catch (NumberFormatException ignored) {
                        // 非数值指标（如门锁布尔值）跳过数值转换
                    }
                }
            }
            // 4. 更新连接统计
            Map<String, Object> conn = connections.computeIfAbsent(deviceCode, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("deviceCode", k);
                m.put("connectTime", LocalDateTime.now().toString());
                m.put("reportCount", 0L);
                return m;
            });
            conn.put("lastReportTime", LocalDateTime.now().toString());
            conn.put("reportCount", ((Number) conn.getOrDefault("reportCount", 0L)).longValue() + 1);

            // 5. WebSocket 广播最新状态
            RealtimeWebSocketHandler.broadcast(objectMapper.writeValueAsString(monitorService.buildRealtimePayload()));
        } catch (Exception e) {
            System.out.println("[IOT] 报文处理失败: " + e.getMessage());
        }
    }

    /** 连接建立时登记 */
    public void onConnect(String deviceCode) {
        totalConnections.incrementAndGet();
        if (deviceCode != null && !deviceCode.equals("null")) {
            Map<String, Object> conn = connections.computeIfAbsent(deviceCode, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("deviceCode", k);
                m.put("reportCount", 0L);
                return m;
            });
            conn.put("connectTime", LocalDateTime.now().toString());
        }
    }

    /** 连接断开时移除 */
    public void onDisconnect(String deviceCode) {
        if (deviceCode != null) {
            connections.remove(deviceCode);
        }
    }

    /** 当前连接概览 */
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReports", totalReports.get());
        result.put("totalConnections", totalConnections.get());
        result.put("activeConnections", connections.size());
        result.put("connectedDevices", connections.values().stream()
                .map(m -> {
                    Map<String, Object> copy = new HashMap<>(m);
                    return copy;
                })
                .toList());
        result.put("tcpPort", 9001);
        return result;
    }

    private String guessUnit(String paramCode) {
        return switch (paramCode) {
            case "load", "load_weight" -> paramCode.equals("load") ? "t" : "kg";
            case "radius", "height" -> "m";
            case "wind_speed" -> "m/s";
            case "angle" -> "°";
            case "person_count" -> "人";
            case "PM2.5" -> "μg/m³";
            case "PM10" -> "μg/m³";
            case "噪声" -> "dB";
            case "温度" -> "℃";
            case "湿度" -> "%RH";
            default -> "";
        };
    }
}
