package com.qst.smartsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qst.smartsite.dto.CraneStatusVO;
import com.qst.smartsite.dto.LiftStatusVO;
import com.qst.smartsite.entity.Alarm;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.mapper.AlarmMapper;
import com.qst.smartsite.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Coze 智能体服务（T-31 / RQ-38）
 * -------------------------------------------------
 * 两级策略：
 *  1) 配置了 Coze API Token + Bot ID 时：调用 Coze 开放平台 v3 对话接口（真实智能体）；
 *  2) 未配置（演示环境默认）：降级为本地知识问答引擎，基于系统真实数据回答
 *     安全态势、告警分析、设备诊断、安全建议等常见问题。
 */
@Service
public class CozeService {

    @Autowired
    private AlarmMapper alarmMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private MonitorService monitorService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private com.qst.smartsite.mapper.SysConfigMapper sysConfigMapper;
    @Autowired
    private com.qst.smartsite.mapper.DeviceTypeMapper deviceTypeMapper;

    @Value("${coze.api-token:}")
    private String apiToken;
    @Value("${coze.bot-id:}")
    private String botId;
    @Value("${coze.base-url:https://api.coze.cn}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 会话持久化（修复"每次打开网站都要重新接入"问题）：
     * 为每个用户保存 Coze conversation_id，后续对话复用同一会话，
     * 智能体能记住上下文，无需每次重新建立对话。
     */
    private final Map<String, String> userConversations = new ConcurrentHashMap<>();

    /** 快捷问题（前端展示用） */
    public static final String[] QUICK_QUESTIONS = {
            "今日安全态势如何？",
            "有哪些未处理的告警？",
            "塔吊运行状态怎么样？",
            "升降机运行状态怎么样？",
            "环境空气质量如何？",
            "给我一些安全建议",
    };

    /* ==================== 配置读取（数据库优先，application.yml 兜底） ==================== */

    /** 读取库中配置：token/botId 任一非空即视为已接入真实 Coze（空值同样生效，用于清除接入） */
    private void loadDbConfig() {
        try {
            String dbToken = getConfig("coze.api_token");
            String dbBot = getConfig("coze.bot_id");
            String dbBase = getConfig("coze.base_url");
            apiToken = (dbToken == null || dbToken.isBlank()) ? "" : dbToken.trim();
            botId = (dbBot == null || dbBot.isBlank()) ? "" : dbBot.trim();
            if (dbBase != null && !dbBase.isBlank()) baseUrl = dbBase.trim();
        } catch (Exception ignored) {
        }
    }

    private String getConfig(String key) {
        com.qst.smartsite.entity.SysConfig c = sysConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.qst.smartsite.entity.SysConfig>()
                        .eq(com.qst.smartsite.entity.SysConfig::getConfigKey, key));
        return c == null ? null : c.getConfigValue();
    }

    /** 是否已接入真实 Coze（供前端展示状态） */
    public boolean isCozeConfigured() {
        loadDbConfig();
        return apiToken != null && !apiToken.isBlank() && botId != null && !botId.isBlank();
    }

    /** 当前 Bot ID（已脱敏判断过配置存在时调用） */
    public String currentBotId() {
        loadDbConfig();
        return botId == null ? "" : botId;
    }

    /** 当前接口地址 */
    public String currentBaseUrl() {
        loadDbConfig();
        return baseUrl == null ? "" : baseUrl;
    }

    /** 当前 Token 脱敏展示（只显示后 4 位） */
    public String currentApiTokenMasked() {
        loadDbConfig();
        if (apiToken == null || apiToken.isBlank()) return "";
        int len = apiToken.length();
        if (len <= 8) return "****";
        return "****" + apiToken.substring(len - 4);
    }

    /** 保存配置（管理端界面调用） */
    public void saveConfig(String token, String bot, String base) {
        saveConfigItem("coze.api_token", token);
        saveConfigItem("coze.bot_id", bot);
        saveConfigItem("coze.base_url", base == null || base.isBlank() ? "https://api.coze.cn" : base.trim());
        // 保存后立即生效
        loadDbConfig();
    }

    private void saveConfigItem(String key, String value) {
        com.qst.smartsite.entity.SysConfig exist = sysConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.qst.smartsite.entity.SysConfig>()
                        .eq(com.qst.smartsite.entity.SysConfig::getConfigKey, key));
        if (exist == null) {
            com.qst.smartsite.entity.SysConfig c = new com.qst.smartsite.entity.SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            sysConfigMapper.insert(c);
        } else {
            com.qst.smartsite.entity.SysConfig upd = new com.qst.smartsite.entity.SysConfig();
            upd.setId(exist.getId());
            upd.setConfigValue(value);
            sysConfigMapper.updateById(upd);
        }
    }

    /**
     * 对话入口
     *
     * @return {reply, source, engine}
     */
    public Map<String, Object> chat(String message, Long userId, String username) {
        loadDbConfig();
        Map<String, Object> result = new LinkedHashMap<>();
        String reply;
        String source;
        if (apiToken != null && !apiToken.isBlank() && botId != null && !botId.isBlank()) {
            reply = chatWithCoze(message, userId);
            source = "coze";
        } else {
            reply = localAnswer(message);
            source = "local";
        }
        result.put("reply", reply);
        result.put("source", source);
        result.put("engine", source.equals("coze") ? "Coze 智能体" : "本地知识引擎（未配置 Coze Token，可在页面右上角「接入配置」填写）");
        return result;
    }

    /* ==================== Coze 真实接口（v3） ==================== */

    private String chatWithCoze(String message, Long userId) {
        String user = userId == null ? "guest" : "user_" + userId;
        String cachedConv = userConversations.get(user);
        String reply = doCozeChat(message, user, cachedConv);
        // 会话失效（如 Coze 侧会话被清理/过期）时清缓存重试一次，避免一直报 conversation_id 错误
        if (reply != null && reply.contains("conversation_id") && cachedConv != null && !cachedConv.isBlank()) {
            userConversations.remove(user);
            reply = doCozeChat(message, user, null);
        }
        return reply;
    }

    private String doCozeChat(String message, String user, String conversationId) {
        try {
            // 1. 创建对话（Coze v3 规范：用户消息字段为 additional_messages）
            //    会话复用：同一用户复用 conversation_id，智能体记住上下文，不再每次重新接入
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("bot_id", botId);
            body.put("user_id", user);
            body.put("stream", false);
            body.put("auto_save_history", true);
            if (conversationId != null && !conversationId.isBlank()) {
                body.put("conversation_id", conversationId);
            }
            // 数据接入：把系统真实数据（资产台账 + 监控实时值 + 告警）作为上下文注入，
            // 让智能体基于平台实际数据回答，而不是泛泛而谈
            List<Map<String, Object>> messages = new ArrayList<>();
            String context = buildSystemContext();
            if (context != null && !context.isBlank()) {
                messages.add(Map.of(
                        "role", "user",
                        "content_type", "text",
                        "content", context));
            }
            messages.add(Map.of(
                    "role", "user",
                    "content_type", "text",
                    "content", message));
            body.put("additional_messages", messages);

            HttpResponse<String> createResp = post(baseUrl + "/v3/chat", body);
            JsonNode createJson = objectMapper.readTree(createResp.body());
            if (createJson.path("code").asInt() != 0) {
                return "Coze 调用失败：" + createJson.path("msg").asText("未知错误") + "（请检查 Token/Bot 配置）";
            }
            String newConversationId = createJson.path("data").path("conversation_id").asText();
            String chatId = createJson.path("data").path("id").asText();
            // 保存会话 ID，下次对话直接复用（后端重启前持续有效）
            if (newConversationId != null && !newConversationId.isBlank()) {
                userConversations.put(user, newConversationId);
            }
            // 本次对话的有效会话：优先取创建响应返回的，其次取传入的
            String activeConversationId = (newConversationId != null && !newConversationId.isBlank())
                    ? newConversationId : conversationId;

            // 2. 轮询对话结果（最多 30 秒）
            for (int i = 0; i < 30; i++) {
                TimeUnit.SECONDS.sleep(1);
                HttpResponse<String> pollResp = get(baseUrl + "/v3/chat/retrieve?conversation_id="
                        + activeConversationId + "&chat_id=" + chatId);
                JsonNode pollJson = objectMapper.readTree(pollResp.body());
                if (pollJson.path("code").asInt() != 0) {
                    return "Coze 查询失败：" + pollJson.path("msg").asText();
                }
                String status = pollJson.path("data").path("status").asText();
                if ("completed".equals(status)) {
                    // 3. 拉取消息列表取助手最后一条回答
                    HttpResponse<String> msgResp = get(baseUrl + "/v3/chat/message/list?conversation_id="
                            + activeConversationId + "&chat_id=" + chatId);
                    JsonNode msgJson = objectMapper.readTree(msgResp.body());
                    JsonNode items = msgJson.path("data");
                    for (int j = items.size() - 1; j >= 0; j--) {
                        JsonNode item = items.get(j);
                        if ("assistant".equals(item.path("role").asText())
                                && "answer".equals(item.path("type").asText())) {
                            return item.path("content").asText();
                        }
                    }
                    return "Coze 未返回回答内容";
                }
                if ("failed".equals(status) || "requires_action".equals(status)) {
                    return "Coze 对话状态异常：" + status;
                }
            }
            return "Coze 响应超时，请稍后重试";
        } catch (Exception e) {
            return "Coze 调用异常：" + e.getMessage() + "（已降级为本地回答，请检查网络与配置）";
        }
    }

    private HttpResponse<String> post(String url, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiToken)
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /* ==================== 系统数据上下文（数据接入：资产 + 监控数据） ==================== */

    /**
     * 构建系统实时数据快照（设备资产台账 + 塔吊/升降机/环境监控实时值 + 告警概况），
     * 作为上下文注入 Coze 智能体，使其基于平台真实数据回答问题。
     * 与本地知识引擎同源，保证"数据接入与实际需求相匹配"。
     */
    private String buildSystemContext() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("【智慧工地平台系统数据快照】时间：")
                    .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .append("\n");

            // 1. 资产台账
            Long total = deviceMapper.selectCount(null);
            Long online = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
            List<Device> all = deviceMapper.selectList(null);
            Map<Long, String> typeNames = new java.util.HashMap<>();
            for (com.qst.smartsite.entity.DeviceType t : deviceTypeMapper.selectList(null)) {
                typeNames.put(t.getId(), t.getTypeName());
            }
            Map<String, Long> byType = new java.util.TreeMap<>();
            for (Device d : all) {
                String t = typeNames.getOrDefault(d.getTypeId(), "其他");
                byType.merge(t, 1L, Long::sum);
            }
            sb.append("一、设备资产台账：共 ").append(total).append(" 台（在线 ").append(online)
                    .append(" / 离线 ").append(total - online).append("），构成：");
            List<String> typeDesc = new ArrayList<>();
            byType.forEach((k, v) -> typeDesc.add(k + v + "台"));
            sb.append(String.join("、", typeDesc)).append("\n");

            // 2. 塔吊实时
            List<CraneStatusVO> cranes = monitorService.listCraneStatus();
            sb.append("二、塔吊实时：");
            if (cranes.isEmpty()) {
                sb.append("无数据");
            } else {
                for (CraneStatusVO c : cranes) {
                    sb.append(c.getDeviceName())
                            .append(" 吊重").append(c.getLoadVal() == null ? "-" : c.getLoadVal() + "t")
                            .append(" 力矩").append(c.getMoment() == null ? "-" : c.getMoment() + "t·m")
                            .append(" 风速").append(c.getWindSpeed() == null ? "-" : c.getWindSpeed() + "m/s")
                            .append(" 幅度").append(c.getRadiusVal() == null ? "-" : c.getRadiusVal() + "m")
                            .append(c.getStatus() == 1 ? "在线" : "离线").append("；");
                }
            }
            sb.append("\n");

            // 3. 升降机实时
            List<LiftStatusVO> lifts = monitorService.listLiftStatus();
            sb.append("三、升降机实时：");
            if (lifts.isEmpty()) {
                sb.append("无数据");
            } else {
                for (LiftStatusVO l : lifts) {
                    sb.append(l.getDeviceName())
                            .append(" 载重").append(l.getLoadWeight() == null ? "-" : l.getLoadWeight() + "kg")
                            .append(" 人数").append(l.getPersonCount() == null ? "-" : l.getPersonCount() + "人")
                            .append(" 门锁前/后").append(l.getDoorFront() == 1 ? "开" : "关")
                            .append("/").append(l.getDoorBack() == 1 ? "开" : "关").append("；");
                }
            }
            sb.append("\n");

            // 4. 环境实时
            List<Map<String, Object>> env = monitorService.listEnvStatus();
            sb.append("四、环境实时：");
            if (env.isEmpty()) {
                sb.append("无数据");
            } else {
                for (Map<String, Object> p : env) {
                    sb.append(p.get("pointName"))
                            .append(" ").append(p.get("monitorSubType"))
                            .append(" ").append(p.get("value") == null ? "-" : p.get("value").toString())
                            .append(p.get("unit") == null ? "" : p.get("unit")).append("；");
                }
            }
            sb.append("\n");

            // 5. 告警概况
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long today = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().ge(Alarm::getAlarmTime, todayStart));
            Long unhandled = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 0));
            sb.append("五、告警：今日 ").append(today).append(" 条，未处理 ").append(unhandled).append(" 条");
            Alarm latest = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                    .eq(Alarm::getHandleStatus, 0)
                    .orderByDesc(Alarm::getAlarmTime).last("LIMIT 1"));
            if (latest != null) {
                sb.append("，最新未处理：").append(latest.getAlarmContent());
            }
            sb.append("\n请基于以上平台真实数据回答用户问题，如数据不足可说明。");
            return sb.toString();
        } catch (Exception e) {
            return "【系统数据快照生成失败】请回答用户问题。";
        }
    }

    /* ==================== 本地知识问答（降级，基于真实库数据） ==================== */

    private String localAnswer(String message) {
        if (message == null || message.isBlank()) {
            return "您好，我是智慧工地安全助手，可以为您分析安全态势、查询告警与设备状态。请问有什么可以帮您？";
        }
        String msg = message.trim();

        // 1. 告警相关
        if (containsAny(msg, "告警", "报警", "隐患", "安全态势", "安全情况", "安全状况")) {
            return alarmSummary(msg);
        }
        // 2. 设备相关（资产台账）
        if (containsAny(msg, "设备", "在线", "离线", "台账", "资产", "多少台", "数量")) {
            return deviceSummary();
        }
        // 3. 塔吊相关
        if (containsAny(msg, "塔吊", "吊塔", "起重机", "吊重", "力矩", "力矩")) {
            return craneSummary();
        }
        // 4. 升降机相关
        if (containsAny(msg, "升降机", "施工电梯", "载重", "超员")) {
            return liftSummary();
        }
        // 5. 环境相关
        if (containsAny(msg, "环境", "空气", "PM", "pm", "粉尘", "噪声", "温度", "湿度", "天气")) {
            return envSummary();
        }
        // 6. 建议相关
        if (containsAny(msg, "建议", "怎么", "如何", "措施", "整改")) {
            return advice(msg);
        }
        // 7. 打招呼
        if (containsAny(msg, "你好", "您好", "hi", "hello", "在吗", "help", "帮助")) {
            return "您好！我是智慧工地安全助手，可以回答：\n" +
                    "· 今日安全态势 / 告警分析\n" +
                    "· 设备在线状态 / 塔吊 / 升降机 / 环境监测\n" +
                    "· 安全建议与整改措施\n" +
                    "试试点击下方快捷问题，或直接输入您的问题。";
        }
        // 8. 兜底
        return "我暂时无法理解这个问题。您可以试试询问：\n" +
                "· 今日安全态势如何？\n" +
                "· 有哪些未处理的告警？\n" +
                "· 塔吊运行状态怎么样？\n" +
                "· 环境空气质量如何？\n" +
                "（演示环境使用本地知识引擎；配置 Coze Token 后即可接入真实智能体）";
    }

    /** 告警分析：今日告警、未处理、按级别/来源统计 */
    private String alarmSummary(String kw) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long todayCount = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().ge(Alarm::getAlarmTime, todayStart));
        Long unhandled = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 0));
        Long handling = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 1));
        Long level1 = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getAlarmLevel, 1));
        Long level2 = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getAlarmLevel, 2));
        Long level3 = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getAlarmLevel, 3));

        StringBuilder sb = new StringBuilder();
        sb.append("【安全态势分析】截至 ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .append("：\n");
        sb.append("· 今日新增告警 ").append(todayCount).append(" 条\n");
        sb.append("· 未处理 ").append(unhandled).append(" 条，处置中 ").append(handling).append(" 条\n");
        sb.append("· 按级别：预警 ").append(level1).append(" 条 / 警报 ").append(level2).append(" 条 / 控制 ")
                .append(level3).append(" 条\n");
        if (unhandled > 0) {
            Alarm latest = alarmMapper.selectOne(
                    new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 0)
                            .orderByDesc(Alarm::getAlarmTime).last("LIMIT 1"));
            if (latest != null) {
                sb.append("· 最新未处理告警：").append(latest.getAlarmContent())
                        .append("（").append(latest.getAlarmTime()).append("）\n");
            }
            sb.append("建议尽快在「告警管理」中处置未处理告警，避免隐患升级。");
        } else {
            sb.append("当前无未处理告警，现场安全状态良好。");
        }
        return sb.toString();
    }

    /** 设备诊断：在线/离线统计 + 告警设备 */
    private String deviceSummary() {
        Long total = deviceMapper.selectCount(null);
        Long online = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
        Long offline = total - online;
        StringBuilder sb = new StringBuilder();
        sb.append("【设备诊断】当前共有设备 ").append(total).append(" 台：\n");
        sb.append("· 在线 ").append(online).append(" 台，离线 ").append(offline).append(" 台\n");
        if (offline > 0) {
            List<Device> off = deviceMapper.selectList(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 0));
            sb.append("· 离线设备：");
            for (int i = 0; i < off.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(off.get(i).getDeviceName());
            }
            sb.append("\n建议检查离线设备网络与供电，确保数据正常上报。");
        } else {
            sb.append("所有设备在线，通信正常。");
        }
        return sb.toString();
    }

    /** 塔吊状态 */
    private String craneSummary() {
        List<CraneStatusVO> cranes = monitorService.listCraneStatus();
        StringBuilder sb = new StringBuilder();
        sb.append("【塔吊运行状态】共 ").append(cranes.size()).append(" 台：\n");
        boolean risk = false;
        for (CraneStatusVO c : cranes) {
            sb.append("· ").append(c.getDeviceName())
                    .append("：吊重 ").append(c.getLoadVal() == null ? "-" : c.getLoadVal() + "t")
                    .append("，力矩 ").append(c.getMoment() == null ? "-" : c.getMoment() + "t·m")
                    .append("，风速 ").append(c.getWindSpeed() == null ? "-" : c.getWindSpeed() + "m/s")
                    .append(c.getStatus() == 1 ? "（在线）" : "（离线）").append("\n");
            if (c.getMomentPercent() != null && c.getMomentPercent().compareTo(java.math.BigDecimal.valueOf(90)) >= 0) {
                risk = true;
            }
        }
        sb.append(risk ? "检测到塔吊力矩占比偏高，请关注超限风险。" : "塔吊运行参数在正常范围内。");
        return sb.toString();
    }

    /** 升降机状态 */
    private String liftSummary() {
        List<LiftStatusVO> lifts = monitorService.listLiftStatus();
        StringBuilder sb = new StringBuilder();
        sb.append("【升降机运行状态】共 ").append(lifts.size()).append(" 台：\n");
        for (LiftStatusVO l : lifts) {
            sb.append("· ").append(l.getDeviceName())
                    .append("：载重 ").append(l.getLoadWeight() == null ? "-" : l.getLoadWeight() + "kg")
                    .append("，载人 ").append(l.getPersonCount() == null ? "-" : l.getPersonCount() + "人")
                    .append("，高度 ").append(l.getHeight() == null ? "-" : l.getHeight() + "m")
                    .append("，门锁前/后：").append(l.getDoorFront() == 1 ? "开" : "关")
                    .append("/").append(l.getDoorBack() == 1 ? "开" : "关").append("\n");
        }
        sb.append("请确认双门不同时开启、载重不超限，保障乘员安全。");
        return sb.toString();
    }

    /** 环境监测 */
    private String envSummary() {
        List<Map<String, Object>> points = monitorService.listEnvStatus();
        StringBuilder sb = new StringBuilder();
        sb.append("【环境空气质量】当前监测点：\n");
        for (Map<String, Object> p : points) {
            sb.append("· ").append(p.get("pointName"))
                    .append("（").append(p.get("monitorSubType")).append("）：")
                    .append(p.get("value") == null ? "-" : p.get("value").toString() + (p.get("unit") == null ? "" : p.get("unit")))
                    .append("\n");
        }
        sb.append("各项指标均在监测中，超标会自动生成告警并联动喷淋降尘。");
        return sb.toString();
    }

    /** 安全建议 */
    private String advice(String kw) {
        Long unhandled = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 0));
        StringBuilder sb = new StringBuilder();
        sb.append("【安全建议】基于当前系统状态：\n");
        sb.append("1. 未处理告警 ").append(unhandled).append(" 条，建议 30 分钟内完成核实与处置，避免风险扩大；\n");
        sb.append("2. 塔吊作业前检查力矩限制器与风速仪，大风（≥6级）停止吊装；\n");
        sb.append("3. 升降机严禁超载、超员，确保前后门互锁装置有效；\n");
        sb.append("4. 现场人员必须佩戴安全帽、穿安全服，AI 识别将持续监测；\n");
        sb.append("5. 环境指标超标时及时开启喷淋降尘，减少粉尘与噪声污染。\n");
        sb.append("如需针对某一设备详细分析，请告诉我设备名称。");
        return sb.toString();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
