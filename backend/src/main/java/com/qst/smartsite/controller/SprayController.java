package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.DeviceLocation;
import com.qst.smartsite.entity.SprayRecord;
import com.qst.smartsite.entity.SprayTask;
import com.qst.smartsite.mapper.DeviceLocationMapper;
import com.qst.smartsite.mapper.SprayRecordMapper;
import com.qst.smartsite.mapper.SprayTaskMapper;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.entity.DeviceType;
import com.qst.smartsite.entity.EnvMonitorPoint;
import com.qst.smartsite.entity.EnvData;
import com.qst.smartsite.mapper.DeviceMapper;
import com.qst.smartsite.mapper.DeviceTypeMapper;
import com.qst.smartsite.mapper.EnvMonitorPointMapper;
import com.qst.smartsite.mapper.EnvDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 喷淋降尘接口（T-24~T-27，RQ-32~RQ-35）
 * 操作记录查询 / 定时任务管理 / 手动控制 / 自动联动（联动调度见 SprayScheduler）
 */
@RestController
@RequestMapping("/api/spray")
public class SprayController {

    @Autowired
    private SprayRecordMapper sprayRecordMapper;

    @Autowired
    private SprayTaskMapper sprayTaskMapper;

    @Autowired
    private DeviceLocationMapper deviceLocationMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private DeviceTypeMapper deviceTypeMapper;
    @Autowired
    private EnvMonitorPointMapper envMonitorPointMapper;
    @Autowired
    private EnvDataMapper envDataMapper;
    @Autowired
    private com.qst.smartsite.mapper.OperationLogMapper operationLogMapper;

    @Autowired
    private com.qst.smartsite.service.SprayScheduler sprayScheduler;

    /* ==================== 喷淋设备状态（卡片式主界面） ==================== */

    /**
     * 喷淋设备状态总览（RQ-35 演示主界面）：
     * 喷淋设备在线状态 + 当前是否喷淋 + 周边湿度/PM2.5（同位置环境监测点最新值）
     */
    @GetMapping("/status")
    public Result<List<Map<String, Object>>> status() {
        Map<Long, String> typeNames = new HashMap<>();
        for (DeviceType t : deviceTypeMapper.selectList(null)) {
            typeNames.put(t.getId(), t.getTypeName());
        }
        Map<Long, String> locNames = loadLocationNames();
        Map<Long, String> deviceNames = new HashMap<>();
        for (Device d : deviceMapper.selectList(null)) {
            deviceNames.put(d.getId(), d.getDeviceName());
        }
        // 同位置的环境监测点（按子类型索引）
        Map<Long, Map<String, EnvMonitorPoint>> envPointsByLoc = new HashMap<>();
        for (EnvMonitorPoint ep : envMonitorPointMapper.selectList(null)) {
            if (ep.getDeviceId() == null) continue;
            Device envDev = deviceMapper.selectById(ep.getDeviceId());
            if (envDev == null || envDev.getLocationId() == null) continue;
            envPointsByLoc.computeIfAbsent(envDev.getLocationId(), k -> new HashMap<>())
                    .put(ep.getMonitorSubType(), ep);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Device d : deviceMapper.selectList(null)) {
            String typeName = typeNames.getOrDefault(d.getTypeId(), "");
            if (!typeName.contains("喷淋")) continue; // 仅统计喷淋设备
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("deviceCode", d.getDeviceCode());
            item.put("deviceName", d.getDeviceName());
            item.put("locationId", d.getLocationId());
            item.put("locationName", locNames.getOrDefault(d.getLocationId(), "-"));
            item.put("status", d.getStatus());          // 1-在线 0-离线
            item.put("enableStatus", d.getEnableStatus());
            item.put("remark", d.getRemark());
            // 最近一次操作：action=1 开启 / action=2 关闭
            SprayRecord last = sprayRecordMapper.selectOne(
                    new LambdaQueryWrapper<SprayRecord>()
                            .eq(d.getId() != null, SprayRecord::getDeviceId, d.getId())
                            .orderByDesc(SprayRecord::getId).last("LIMIT 1"));
            boolean spraying = last != null && last.getAction() != null && last.getAction() == 1;
            item.put("spraying", spraying);
            item.put("lastAction", last == null ? null : last.getAction());
            item.put("lastSprayTime", last == null ? null : last.getCreateTime());
            item.put("lastReason", last == null ? null : last.getReason());
            // 周边湿度 / PM2.5（同位置环境监测点最新值）
            Map<String, EnvMonitorPoint> pts = envPointsByLoc.getOrDefault(d.getLocationId(), Map.of());
            item.put("humidity", latestEnvValue(pts.get("湿度")));
            item.put("humidityUnit", pts.get("湿度") == null ? "%RH" : pts.get("湿度").getUnit());
            item.put("pm25", latestEnvValue(pts.get("PM2.5")));
            item.put("pm25Unit", pts.get("PM2.5") == null ? "μg/m³" : pts.get("PM2.5").getUnit());
            item.put("envDeviceName", pts.isEmpty() ? null : deviceNames.get(envDeviceIdOf(pts)));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 环境监测点最新值 */
    private BigDecimal latestEnvValue(EnvMonitorPoint ep) {
        if (ep == null) return null;
        EnvData d = envDataMapper.selectOne(
                new LambdaQueryWrapper<EnvData>()
                        .eq(EnvData::getPointId, ep.getId())
                        .orderByDesc(EnvData::getCollectTime).last("LIMIT 1"));
        return d == null ? null : d.getIndexValue();
    }

    private Long envDeviceIdOf(Map<String, EnvMonitorPoint> pts) {
        for (EnvMonitorPoint ep : pts.values()) {
            if (ep.getDeviceId() != null) return ep.getDeviceId();
        }
        return null;
    }

    /* ==================== T-24 喷淋操作记录 ==================== */

    /** 操作记录分页查询（支持位置/触发方式/操作类型筛选） */
    @GetMapping("/record")
    public Result<Page<Map<String, Object>>> record(@RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) Long locationId,
                                                    @RequestParam(required = false) Integer triggerType,
                                                    @RequestParam(required = false) Integer action) {
        Page<SprayRecord> page = sprayRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SprayRecord>()
                        .eq(locationId != null, SprayRecord::getLocationId, locationId)
                        .eq(triggerType != null, SprayRecord::getTriggerType, triggerType)
                        .eq(action != null, SprayRecord::getAction, action)
                        .orderByDesc(SprayRecord::getId));
        // 填充位置名称
        Map<Long, String> locNames = loadLocationNames();
        List<Map<String, Object>> records = new ArrayList<>();
        for (SprayRecord r : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("pointId", r.getPointId());
            item.put("locationId", r.getLocationId());
            item.put("locationName", locNames.getOrDefault(r.getLocationId(), "-"));
            item.put("deviceId", r.getDeviceId());
            item.put("triggerType", r.getTriggerType());
            item.put("action", r.getAction());
            item.put("reason", r.getReason());
            item.put("operator", r.getOperator());
            item.put("createTime", r.getCreateTime());
            records.add(item);
        }
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(records);
        return Result.ok(result);
    }

    /* ==================== T-25 喷淋定时任务管理 ==================== */

    /** 定时任务列表 */
    @GetMapping("/task/list")
    public Result<List<Map<String, Object>>> taskList() {
        List<SprayTask> tasks = sprayTaskMapper.selectList(
                new LambdaQueryWrapper<SprayTask>().orderByDesc(SprayTask::getId));
        Map<Long, String> locNames = loadLocationNames();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SprayTask t : tasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("taskName", t.getTaskName());
            item.put("locationId", t.getLocationId());
            item.put("locationName", locNames.getOrDefault(t.getLocationId(), "-"));
            item.put("startTime", t.getStartTime());
            item.put("duration", t.getDuration());
            item.put("periodValue", t.getPeriodValue());
            item.put("periodUnit", t.getPeriodUnit());
            item.put("status", t.getStatus());
            item.put("createTime", t.getCreateTime());
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 新增定时任务 */
    @PostMapping("/task")
    public Result<Void> addTask(@RequestBody SprayTask task) {
        validateTask(task);
        if (task.getStatus() == null) task.setStatus(1);
        sprayTaskMapper.insert(task);
        return Result.ok();
    }

    /** 编辑定时任务 */
    @PutMapping("/task/{id}")
    public Result<Void> updateTask(@PathVariable Long id, @RequestBody SprayTask task) {
        SprayTask db = sprayTaskMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "定时任务不存在");
        }
        task.setId(id);
        sprayTaskMapper.updateById(task);
        return Result.ok();
    }

    /** 删除定时任务 */
    @DeleteMapping("/task/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        sprayTaskMapper.deleteById(id);
        return Result.ok();
    }

    /** 启用/禁用定时任务 */
    @PutMapping("/task/{id}/status")
    public Result<Void> changeTaskStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SprayTask db = sprayTaskMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "定时任务不存在");
        }
        Object status = body.get("status");
        if (status == null) {
            throw new BusinessException(400, "缺少状态参数");
        }
        SprayTask upd = new SprayTask();
        upd.setId(id);
        upd.setStatus(Integer.valueOf(status.toString()));
        sprayTaskMapper.updateById(upd);
        return Result.ok();
    }

    /* ==================== T-26 手动喷淋控制 ==================== */

    /** 手动开启/关闭喷淋（triggerType=1，UC-003 互斥校验） */
    @PostMapping("/manual")
    public Result<Void> manual(@RequestBody Map<String, Object> body,
                               @RequestAttribute("username") String username) {
        Object locationId = body.get("locationId");
        Object action = body.get("action");
        if (locationId == null) {
            throw new BusinessException(400, "请选择喷淋位置");
        }
        if (action == null) {
            throw new BusinessException(400, "缺少操作类型");
        }
        Long locId = Long.valueOf(locationId.toString());
        DeviceLocation loc = deviceLocationMapper.selectById(locId);
        if (loc == null) {
            throw new BusinessException(404, "喷淋位置不存在");
        }
        int act = Integer.parseInt(action.toString());
        if (act != 1 && act != 2) {
            throw new BusinessException(400, "操作类型不合法");
        }
        // 该位置下的喷淋设备（与 /api/spray/status 过滤口径一致：类型名含"喷淋"）
        Long deviceId = findSprayDeviceId(locId);
        // 状态校验（与 /api/spray/status 的 spraying 判定一致：按设备最近一条记录 action 判断）
        SprayRecord last = sprayRecordMapper.selectOne(
                new LambdaQueryWrapper<SprayRecord>()
                        .eq(deviceId != null, SprayRecord::getDeviceId, deviceId)
                        .eq(deviceId == null, SprayRecord::getLocationId, locId)
                        .orderByDesc(SprayRecord::getId).last("LIMIT 1"));
        boolean spraying = last != null && last.getAction() != null && last.getAction() == 1;
        if (act == 1 && spraying) {
            throw new BusinessException(400, "该位置喷淋已开启，请先关闭");
        }
        if (act == 2 && !spraying) {
            throw new BusinessException(400, "该位置喷淋未开启");
        }
        SprayRecord record = new SprayRecord();
        record.setLocationId(locId);
        record.setDeviceId(deviceId);
        record.setAction(act);
        record.setTriggerType(1);
        record.setReason(body.get("reason") == null ? null : body.get("reason").toString());
        record.setOperator(username);
        sprayRecordMapper.insert(record);
        // 手动干预同步自动联动状态：开启→加入 sprayingPoints；关闭→移除（后续超标可再次自动开启）
        sprayScheduler.syncManualAction(deviceId, act);
        System.out.println("[SPRAY-MANUAL] " + (act == 1 ? "开启" : "关闭")
                + "位置[" + loc.getLocationName() + "] 操作人:" + username);
        log(null, username, "喷淋控制", act == 1 ? "手动开启" : "手动关闭",
                "位置[" + loc.getLocationName() + "] " + record.getReason());
        return Result.ok();
    }

    /** 该位置下的喷淋设备ID（类型名含"喷淋"；与 status 接口过滤口径一致） */
    private Long findSprayDeviceId(Long locationId) {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getLocationId, locationId));
        for (Device d : devices) {
            DeviceType t = deviceTypeMapper.selectById(d.getTypeId());
            if (t != null && t.getTypeName() != null && t.getTypeName().contains("喷淋")) {
                return d.getId();
            }
        }
        return null;
    }

    /** 位置列表（喷淋位置下拉，含层级名称） */
    @GetMapping("/locations")
    public Result<List<DeviceLocation>> locations() {
        return Result.ok(deviceLocationMapper.selectList(
                new LambdaQueryWrapper<DeviceLocation>().orderByAsc(DeviceLocation::getId)));
    }

    /* ==================== 内部方法 ==================== */

    private Map<Long, String> loadLocationNames() {
        Map<Long, String> map = new HashMap<>();
        for (DeviceLocation loc : deviceLocationMapper.selectList(null)) {
            map.put(loc.getId(), loc.getLocationName());
        }
        return map;
    }

    private void validateTask(SprayTask task) {
        if (task.getTaskName() == null || task.getTaskName().isBlank()) {
            throw new BusinessException(400, "任务名称不能为空");
        }
        if (task.getLocationId() == null) {
            throw new BusinessException(400, "请选择喷淋位置");
        }
        if (task.getStartTime() == null) {
            throw new BusinessException(400, "请设置开始时间");
        }
        if (task.getDuration() == null || task.getDuration() <= 0) {
            throw new BusinessException(400, "持续时长必须大于 0");
        }
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