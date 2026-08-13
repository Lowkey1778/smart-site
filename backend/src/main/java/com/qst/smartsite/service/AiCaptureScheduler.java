package com.qst.smartsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qst.smartsite.config.RealtimeWebSocketHandler;
import com.qst.smartsite.entity.Alarm;
import com.qst.smartsite.entity.Camera;
import com.qst.smartsite.mapper.AlarmMapper;
import com.qst.smartsite.mapper.CameraMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 智能识别采集调度器
 * 链路: HLS视频流 → FFmpeg截帧 → Flask AI推理服务 → 危险行为告警 → WebSocket推送
 * 对应《页面功能清单》七、AI智能识别 与《概要设计说明书》4.1 AI推理服务接口
 */
@Component
public class AiCaptureScheduler {

    @Autowired
    private CameraMapper cameraMapper;
    @Autowired
    private AlarmMapper alarmMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.server-url}")
    private String aiServerUrl;

    @Value("${ai.ffmpeg-path}")
    private String ffmpegPath;

    @Value("${ai.capture-dir}")
    private String captureDir;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 每 30 秒对在线摄像头截帧检测一次 */
    @Scheduled(fixedRateString = "${ai.interval-ms:30000}")
    public void captureAndDetect() {
        List<Camera> cameras = cameraMapper.selectList(
                new LambdaQueryWrapper<Camera>().eq(Camera::getOnlineStatus, 1));
        for (Camera cam : cameras) {
            try {
                processCamera(cam);
            } catch (Exception e) {
                // 单路失败不影响其他摄像头
                e.printStackTrace();
            }
        }
    }

    private void processCamera(Camera cam) throws Exception {
        if (cam.getStreamUrl() == null || cam.getStreamUrl().isBlank()) {
            return;
        }
        // 1. FFmpeg 从 HLS 流截取一帧
        String fileName = cam.getCameraCode() + "_" + LocalDateTime.now().format(TS) + ".jpg";
        Path out = Path.of(captureDir, fileName);
        Files.createDirectories(out.getParent());
        if (!captureFrame(cam.getStreamUrl(), out.toString())) {
            return; // 流不可用，跳过
        }

        // 2. 调用 Flask AI 推理服务
        List<Map<String, Object>> results = detect(out.toString());
        if (results.isEmpty()) {
            Files.deleteIfExists(out); // 无危险行为，清理截图
            return;
        }

        // 3. 生成 AI 告警（去重：同摄像头同类型 10 分钟内不重复）
        for (Map<String, Object> r : results) {
            String label = (String) r.get("label");
            String labelZh = (String) r.get("label_zh");
            double conf = ((Number) r.get("confidence")).doubleValue();
            int level = levelOf(label);
            Long count = alarmMapper.selectCount(
                    new LambdaQueryWrapper<Alarm>()
                            .eq(Alarm::getAlarmSource, 3)
                            .eq(Alarm::getCameraId, cam.getId())
                            .like(Alarm::getAlarmContent, labelZh)
                            .ne(Alarm::getHandleStatus, 2)
                            .ge(Alarm::getAlarmTime, LocalDateTime.now().minusMinutes(10)));
            if (count != null && count > 0) {
                continue;
            }
            Alarm alarm = new Alarm();
            alarm.setAlarmNo("AI" + LocalDateTime.now().format(TS)
                    + String.format("%03d", (int) (Math.random() * 900) + 100));
            alarm.setBatchNo("AIB" + LocalDateTime.now().format(TS));
            alarm.setAlarmSource(3);
            alarm.setAlarmLevel(level);
            alarm.setCameraId(cam.getId());
            alarm.setImageUrl("/ai-capture/" + fileName);
            alarm.setAlarmContent("【" + labelZh + "】" + cam.getCameraName()
                    + " 置信度" + String.format("%.1f", conf * 100) + "%");
            alarm.setAlarmValue(null);
            alarm.setAlarmTime(LocalDateTime.now());
            alarm.setHandleStatus(0);
            alarmMapper.insert(alarm);
            System.out.println("[AI-ALARM] " + alarm.getAlarmContent());

            // WebSocket 推送：携带告警详情触发全局提醒（T-35），同时通知 AI 告警页刷新
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("id", alarm.getId());
                data.put("alarmNo", alarm.getAlarmNo());
                data.put("alarmContent", alarm.getAlarmContent());
                data.put("alarmLevel", alarm.getAlarmLevel());
                data.put("alarmSource", alarm.getAlarmSource());
                data.put("alarmTime", alarm.getAlarmTime() == null ? null : alarm.getAlarmTime().toString());
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "alarm");
                msg.put("aiAlarm", true);
                msg.put("data", data);
                RealtimeWebSocketHandler.broadcast(objectMapper.writeValueAsString(msg));
            } catch (Exception ignored) {
            }
        }

    }

    /** FFmpeg 截帧 */
    private boolean captureFrame(String streamUrl, String outPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-y",
                    "-rw_timeout", "15000000",
                    "-i", streamUrl,
                    "-frames:v", "1", "-q:v", "3", outPath);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // 读取输出避免管道阻塞
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                while (br.readLine() != null) {
                    // 丢弃输出
                }
            }
            boolean done = p.waitFor(25, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0 && Files.exists(Path.of(outPath)) && Files.size(Path.of(outPath)) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 调用 Flask 推理服务（curl multipart） */
    private List<Map<String, Object>> detect(String imagePath) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("curl.exe", "-s", "-m", "20",
                    "-F", "image=@" + imagePath, aiServerUrl + "/api/ai/detect");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            boolean done = p.waitFor(20, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return results;
            }
            JsonNode root = objectMapper.readTree(sb.toString());
            if (root.path("code").asInt() == 0) {
                JsonNode arr = root.path("data").path("results");
                for (JsonNode item : arr) {
                    results.add(objectMapper.convertValue(item, Map.class));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    /** 告警级别映射: 明火-控制(3) 吸烟-警报(2) 安全帽/安全服-预警(1) */
    private int levelOf(String label) {
        return switch (label) {
            case "fire" -> 3;
            case "smoke" -> 2;
            default -> 1;
        };
    }
}
