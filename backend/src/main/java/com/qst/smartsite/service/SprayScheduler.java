package com.qst.smartsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.entity.*;
import com.qst.smartsite.mapper.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 喷淋调度器（T-25 定时任务执行 / T-27 自动联动，RQ-33 / RQ-35）
 * - checkTasks：每 30 秒检查启用的定时任务，命中开始时间则写入喷淋记录（triggerType=2）
 * - autoLink：每 5 秒检查启用喷淋联动的监测点，超阈值自动开、恢复正常自动关（triggerType=3）
 */
@Component
public class SprayScheduler {

    @Autowired
    private SprayRecordMapper sprayRecordMapper;

    @Autowired
    private SprayTaskMapper sprayTaskMapper;

    @Autowired
    private DeviceMonitorPointMapper deviceMonitorPointMapper;

    @Autowired
    private EnvMonitorPointMapper envMonitorPointMapper;

    @Autowired
    private EnvDataMapper envDataMapper;

    @Autowired
    private RealtimeDataMapper realtimeDataMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    /** 自动联动中已开启喷淋的监测点ID集合（内存态） */
    private final Set<Long> sprayingPoints = new HashSet<>();

    /** 定时任务最后执行标记：taskId -> yyyy-MM-dd HH:mm（同一天同一分钟只执行一次） */
    private final Map<Long, String> taskLastRun = new HashMap<>();

    private static final DateTimeFormatter MIN_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /* ==================== UC-003 联动状态持久化与手动同步 ==================== */

    /**
     * 服务启动时从数据库恢复自动联动状态（内存态 sprayingPoints 不丢失）：
     * 取每个监测点最近一条 triggerType=3（自动联动）喷淋记录，若 action=1 视为仍处于联动喷淋中
     */
    @PostConstruct
    public void initSprayingPoints() {
        try {
            List<SprayRecord> autoRecords = sprayRecordMapper.selectList(
                    new LambdaQueryWrapper<SprayRecord>()
                            .eq(SprayRecord::getTriggerType, 3)
                            .orderByAsc(SprayRecord::getId));
            // 按 pointId 保留最新一条
            Map<Long, SprayRecord> latestByPoint = new HashMap<>();
            for (SprayRecord r : autoRecords) {
                if (r.getPointId() != null) {
                    latestByPoint.put(r.getPointId(), r);
                }
            }
            for (SprayRecord r : latestByPoint.values()) {
                if (r.getAction() != null && r.getAction() == 1) {
                    sprayingPoints.add(r.getPointId());
                }
            }
            System.out.println("[SPRAY-INIT] 恢复自动联动喷淋中监测点: " + sprayingPoints);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 手动控制后同步自动联动状态（UC-003 手动干预）：
     * 开启 → 将关联监测点加入 sprayingPoints（联动期间不重复自动开启）；
     * 关闭 → 移出 sprayingPoints（使后续超标可再次自动开启）
     */
    public void syncManualAction(Long deviceId, int action) {
        try {
            if (deviceId == null) return;
            List<DeviceMonitorPoint> points = deviceMonitorPointMapper.selectList(
                    new LambdaQueryWrapper<DeviceMonitorPoint>()
                            .eq(DeviceMonitorPoint::getSprayEnabled, 1)
                            .eq(DeviceMonitorPoint::getSprayDeviceId, deviceId));
            for (DeviceMonitorPoint p : points) {
                if (action == 1) {
                    sprayingPoints.add(p.getId());
                    System.out.println("[SPRAY-SYNC] 手动开启 → 监测点" + p.getId() + " 加入联动集合");
                } else {
                    sprayingPoints.remove(p.getId());
                    System.out.println("[SPRAY-SYNC] 手动关闭 → 监测点" + p.getId() + " 移出联动集合");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ==================== T-25 定时任务执行 ==================== */

    @Scheduled(fixedRate = 30000)
    public void checkTasks() {
        try {
            List<SprayTask> tasks = sprayTaskMapper.selectList(
                    new LambdaQueryWrapper<SprayTask>().eq(SprayTask::getStatus, 1));
            if (tasks.isEmpty()) return;
            String nowMin = LocalTime.now().format(MIN_FMT);
            String dayKey = LocalDate.now().toString() + " " + nowMin;
            for (SprayTask t : tasks) {
                if (t.getStartTime() == null) continue;
                String startMin = t.getStartTime().format(MIN_FMT);
                if (startMin.equals(nowMin) && !dayKey.equals(taskLastRun.get(t.getId()))) {
                    SprayRecord rec = new SprayRecord();
                    rec.setLocationId(t.getLocationId());
                    rec.setTriggerType(2);
                    rec.setAction(1);
                    rec.setReason("定时任务[" + t.getTaskName() + "]执行喷淋，持续" + t.getDuration() + "分钟");
                    rec.setOperator("system");
                    sprayRecordMapper.insert(rec);
                    taskLastRun.put(t.getId(), dayKey);
                    System.out.println("[SPRAY-TASK] " + t.getTaskName() + " 于 " + nowMin + " 执行喷淋");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ==================== T-27 自动联动 ==================== */

    @Scheduled(fixedRate = 5000)
    public void autoLink() {
        try {
            List<DeviceMonitorPoint> points = deviceMonitorPointMapper.selectList(
                    new LambdaQueryWrapper<DeviceMonitorPoint>()
                            .eq(DeviceMonitorPoint::getSprayEnabled, 1)
                            .isNotNull(DeviceMonitorPoint::getSprayOnThreshold));
            for (DeviceMonitorPoint p : points) {
                BigDecimal value = currentMonitorValue(p);
                if (value == null) continue;
                if (!sprayingPoints.contains(p.getId())
                        && value.compareTo(p.getSprayOnThreshold()) >= 0) {
                    insertAutoRecord(p, 1, p.getPointName() + "超标(" + value + ")自动联动开启喷淋");
                    sprayingPoints.add(p.getId());
                } else if (sprayingPoints.contains(p.getId())
                        && p.getSprayOffThreshold() != null
                        && value.compareTo(p.getSprayOffThreshold()) < 0) {
                    insertAutoRecord(p, 2, p.getPointName() + "恢复正常(" + value + ")自动关闭喷淋");
                    sprayingPoints.remove(p.getId());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 自动联动写记录（位置取关联喷淋设备所在位置） */
    private void insertAutoRecord(DeviceMonitorPoint p, int action, String reason) {
        Long locationId = null;
        if (p.getSprayDeviceId() != null) {
            Device d = deviceMapper.selectById(p.getSprayDeviceId());
            if (d != null) locationId = d.getLocationId();
        }
        SprayRecord rec = new SprayRecord();
        rec.setPointId(p.getId());
        rec.setLocationId(locationId);
        rec.setDeviceId(p.getSprayDeviceId());
        rec.setTriggerType(3);
        rec.setAction(action);
        rec.setReason(reason);
        rec.setOperator("system");
        sprayRecordMapper.insert(rec);
        System.out.println("[SPRAY-AUTO] " + reason);
    }

    /** 监测点当前值：环境类子类型取同名环境监测点最新值，其余取设备最新实时值 */
    private BigDecimal currentMonitorValue(DeviceMonitorPoint p) {
        String sub = p.getMonitorSubType();
        if (sub != null && (sub.equals("PM2.5") || sub.equals("PM10") || sub.equals("噪声")
                || sub.equals("温度") || sub.equals("湿度") || sub.equals("风速"))) {
            EnvMonitorPoint ep = envMonitorPointMapper.selectOne(
                    new LambdaQueryWrapper<EnvMonitorPoint>()
                            .eq(EnvMonitorPoint::getMonitorSubType, sub)
                            .last("LIMIT 1"));
            if (ep != null) {
                EnvData d = envDataMapper.selectOne(
                        new LambdaQueryWrapper<EnvData>()
                                .eq(EnvData::getPointId, ep.getId())
                                .orderByDesc(EnvData::getCollectTime)
                                .last("LIMIT 1"));
                if (d != null) return d.getIndexValue();
            }
        }
        if (p.getDeviceId() == null) return null;
        RealtimeData d = realtimeDataMapper.selectOne(
                new LambdaQueryWrapper<RealtimeData>()
                        .eq(RealtimeData::getDeviceId, p.getDeviceId())
                        .orderByDesc(RealtimeData::getCollectTime)
                        .last("LIMIT 1"));
        return d == null ? null : d.getParamValue();
    }
}