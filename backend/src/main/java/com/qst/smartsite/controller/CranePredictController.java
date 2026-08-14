package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.entity.RealtimeData;
import com.qst.smartsite.mapper.DeviceMapper;
import com.qst.smartsite.mapper.RealtimeDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 塔吊安全状态预测接口（T-33 / RQ-17）
 * 取塔吊最近 60 个时间点实时数据，调用 Flask 预测服务（CNN+LSTM+Attention）返回风险预测。
 */
@RestController
@RequestMapping("/api/crane")
public class CranePredictController {

    private static final String[] PARAMS = {"load", "radius", "wind_speed", "height", "angle"};

    @Autowired
    private RealtimeDataMapper realtimeDataMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.predict-url:http://localhost:5001}")
    private String predictUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    /**
     * GET /api/crane/predict/{deviceId}
     */
    @GetMapping("/predict/{deviceId}")
    public Result<Map<String, Object>> predict(@PathVariable Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        // 组装最近 60 个时间点特征（按收集时间排序，取每条时间点最新值）
        List<RealtimeData> rows = realtimeDataMapper.selectList(
                new LambdaQueryWrapper<RealtimeData>()
                        .eq(RealtimeData::getDeviceId, deviceId)
                        .in(RealtimeData::getParamCode, (Object[]) PARAMS)
                        .orderByDesc(RealtimeData::getCollectTime)
                        .last("LIMIT 2000"));

        // 按 collectTime 聚合成时间序列
        Map<String, Map<String, Double>> byTime = new LinkedHashMap<>();
        for (RealtimeData r : rows) {
            byTime.computeIfAbsent(r.getCollectTime().toString(), k -> new LinkedHashMap<>())
                    .put(r.getParamCode(), r.getParamValue().doubleValue());
        }
        List<Map<String, Double>> ordered = new ArrayList<>(byTime.values());
        // 时间升序
        java.util.Collections.reverse(ordered);

        List<List<Double>> history = new ArrayList<>();
        for (Map<String, Double> point : ordered) {
            List<Double> vec = new ArrayList<>();
            boolean complete = true;
            for (String p : PARAMS) {
                Double v = point.get(p);
                if (v == null) {
                    complete = false;
                    break;
                }
                vec.add(v);
            }
            if (complete) {
                history.add(vec);
            }
        }
        if (history.size() < 60) {
            throw new BusinessException(400, "历史数据不足 60 个时间点，暂无法预测（请稍后重试）");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("deviceCode", device.getDeviceCode());
            body.put("history", history);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(predictUrl + "/api/predict/crane"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(resp.body());
            if (json.path("code").asInt() != 0) {
                throw new BusinessException(500, json.path("message").asText("预测服务异常"));
            }
            return Result.ok(objectMapper.convertValue(json.path("data"), Map.class));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "预测服务不可用：" + e.getMessage()
                    + "（请启动 ai-server 预测服务 predict_server.py）");
        }
    }

    /** 预测服务健康状态 */
    @GetMapping("/predict/health")
    public Result<Map<String, Object>> predictHealth() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(predictUrl + "/api/predict/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(resp.body());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("online", true);
            data.put("modelReady", json.path("model_ready").asBoolean(false));
            return Result.ok(data);
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("online", false);
            data.put("modelReady", false);
            return Result.ok(data);
        }
    }
}
