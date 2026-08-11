package com.qst.smartsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qst.smartsite.config.RealtimeWebSocketHandler;
import com.qst.smartsite.entity.*;
import com.qst.smartsite.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 设备数据模拟器（支撑功能，对应计划书"Express+TCP 模拟平台"的轻量实现）
 * 每 5 秒生成一次塔吊/升降机/环境数据，写入数据库并触发阈值告警，
 * 最后通过 WebSocket 广播最新状态给前端。
 */
@Component
public class MockDataScheduler {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private RealtimeDataMapper realtimeDataMapper;
    @Autowired
    private EnvDataMapper envDataMapper;
    @Autowired
    private AlarmMapper alarmMapper;
    @Autowired
    private EnvMonitorPointMapper envMonitorPointMapper;
    @Autowired
    private DeviceMonitorPointMapper deviceMonitorPointMapper;
    @Autowired
    private TowerCraneRecordMapper towerCraneRecordMapper;
    @Autowired
    private LiftRecordMapper liftRecordMapper;
    @Autowired
    private TowerCraneParamMapper towerCraneParamMapper;
    @Autowired
    private MonitorService monitorService;

    /** T-34 Redis 实时数据缓存（可选，Redis 不可用时自动降级） */
    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    /** 注入 Spring 管理的 ObjectMapper（已注册 JavaTimeModule，可序列化 LocalDateTime） */
    @Autowired
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ============ 阈值配置（与 init_data.sql 一致） ============
    private static final BigDecimal CRANE_LOAD_WARN = new BigDecimal("7.2");   // 吊重预警(t)
    private static final BigDecimal CRANE_LOAD_ALARM = new BigDecimal("8.0");  // 吊重警报(t)
    private static final BigDecimal CRANE_WIND_WARN = new BigDecimal("12");    // 风速预警(m/s)
    private static final BigDecimal CRANE_WIND_ALARM = new BigDecimal("18");
    private static final BigDecimal LIFT_LOAD_WARN = new BigDecimal("1800");   // 载重预警(kg)
    private static final BigDecimal LIFT_LOAD_ALARM = new BigDecimal("2000");
    private static final int LIFT_PERSON_WARN = 8;                              // 超员预警
    private static final BigDecimal CRANE_RATED_MOMENT = new BigDecimal("630");// 额定力矩(t·m)

    /**
     * 定时生成模拟数据（5 秒一次）
     */
    /** 作业记录生成计数（每 12 次 = 60 秒生成一条作业记录，T-14/T-15） */
    private int tick = 0;

    // ============ 环境数据随机游走状态（保证变化平滑、线性，不跳变） ============
    /** 环境监测点当前值：pointId -> 当前值（每 tick 小步长游走） */
    private final Map<Long, BigDecimal> envState = new ConcurrentHashMap<>();
    /** 故障注入剩余 tick 数：pointId -> 剩余次数（>0 表示该点正处在故障/异常事件中） */
    private final Map<Long, Integer> envFaultTicks = new ConcurrentHashMap<>();
    /** 各设备当前温度（用于湿度联动计算：温度越高湿度越低，近似线性关系） */
    private final Map<Long, BigDecimal> deviceTemp = new ConcurrentHashMap<>();

    /** 温度-湿度线性关联：湿度(%) = HUMIDITY_BASE - (温度 - 20) × HUMIDITY_SLOPE + 噪声 */
    private static final BigDecimal HUMIDITY_BASE = new BigDecimal("88");
    private static final BigDecimal HUMIDITY_SLOPE = new BigDecimal("3.2");

    @Scheduled(fixedRate = 5000)
    public void generateData() {
        try {
            generateCrane(1L);
            generateCrane(2L);
            generateLift(3L);
            generateEnv();
            recoverAlarms();
            tick++;
            if (tick % 12 == 0) {
                generateCraneRecord(1L);
                generateCraneRecord(2L);
                generateLiftRecord(3L);
            }
            broadcast();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 塔吊数据 + 告警判断 */
    private void generateCrane(Long deviceId) {
        Random r = ThreadLocalRandom.current();
        write(deviceId, "load", BigDecimal.valueOf(0.8 + r.nextDouble() * 6.5).setScale(2, RoundingMode.HALF_UP), "t");
        write(deviceId, "radius", BigDecimal.valueOf(12 + r.nextDouble() * 40).setScale(1, RoundingMode.HALF_UP), "m");
        write(deviceId, "wind_speed", BigDecimal.valueOf(2 + r.nextDouble() * 14).setScale(1, RoundingMode.HALF_UP), "m/s");
        write(deviceId, "height", BigDecimal.valueOf(8 + r.nextDouble() * 95).setScale(1, RoundingMode.HALF_UP), "m");
        write(deviceId, "angle", BigDecimal.valueOf(r.nextDouble() * 360).setScale(1, RoundingMode.HALF_UP), "°");

        BigDecimal load = latest(deviceId, "load");
        BigDecimal wind = latest(deviceId, "wind_speed");
        BigDecimal radius = latest(deviceId, "radius");
        // 力矩 = 吊重 × 幅度
        BigDecimal moment = (load == null || radius == null) ? null : load.multiply(radius).setScale(2, RoundingMode.HALF_UP);

        checkDeviceAlarm(deviceId, 1L, "吊重", load, CRANE_LOAD_WARN, CRANE_LOAD_ALARM, "t");
        checkDeviceAlarm(deviceId, 3L, "风速", wind, CRANE_WIND_WARN, CRANE_WIND_ALARM, "m/s");
        if (moment != null && moment.compareTo(CRANE_RATED_MOMENT) > 0) {
            addAlarm(1, 3, deviceId, 1L,
                    "塔吊力矩超限！当前" + moment + " t·m > 额定" + CRANE_RATED_MOMENT + " t·m",
                    moment, null);
        }
    }

    /** 升降机数据 + 告警判断 */
    private void generateLift(Long deviceId) {
        Random r = ThreadLocalRandom.current();
        write(deviceId, "load_weight", BigDecimal.valueOf(300 + r.nextDouble() * 1650).setScale(0, RoundingMode.HALF_UP), "kg");
        write(deviceId, "person_count", BigDecimal.valueOf(1 + r.nextInt(8)).setScale(0, RoundingMode.HALF_UP), "人");
        write(deviceId, "height", BigDecimal.valueOf(5 + r.nextDouble() * 115).setScale(1, RoundingMode.HALF_UP), "m");
        write(deviceId, "wind_speed", BigDecimal.valueOf(2 + r.nextDouble() * 12).setScale(1, RoundingMode.HALF_UP), "m/s");
        write(deviceId, "direction", BigDecimal.valueOf(r.nextBoolean() ? 1 : 2).setScale(0, RoundingMode.HALF_UP), "");
        // 门锁：正常互斥；5% 概率双门同时打开（安全隐患）
        boolean bothOpen = r.nextDouble() < 0.05;
        write(deviceId, "door_front", BigDecimal.valueOf(bothOpen || r.nextBoolean() ? 1 : 0).setScale(0, RoundingMode.HALF_UP), "");
        write(deviceId, "door_back", BigDecimal.valueOf(bothOpen || r.nextBoolean() ? 1 : 0).setScale(0, RoundingMode.HALF_UP), "");

        BigDecimal load = latest(deviceId, "load_weight");
        BigDecimal persons = latest(deviceId, "person_count");
        BigDecimal doorF = latest(deviceId, "door_front");
        BigDecimal doorB = latest(deviceId, "door_back");

        checkDeviceAlarm(deviceId, 4L, "载重", load, LIFT_LOAD_WARN, LIFT_LOAD_ALARM, "kg");
        if (persons != null && persons.intValue() > LIFT_PERSON_WARN) {
            addAlarm(1, 1, deviceId, null, "升降机超员！当前" + persons.intValue() + "人", persons, null);
        }
        if (doorF != null && doorB != null && doorF.intValue() == 1 && doorB.intValue() == 1) {
            addAlarm(1, 2, deviceId, null, "升降机前后门同时打开，存在坠落风险", BigDecimal.ONE, null);
        }
    }

    /** 环境监测数据 + 告警判断
     * 改进（演示数据合理性）：
     *  1) 随机游走：每个监测点在上一值基础上小步长变化（线性渐变，不跳变）；
     *  2) 温度-湿度关联：湿度随温度升高近似线性下降（每升高 1℃ 湿度约降 3.2%）；
     *  3) PM10 与 PM2.5 关联：PM10 ≈ PM2.5 × 1.8 + 噪声；
     *  4) 故障注入：小概率触发 PM2.5/噪声/风速/温度异常抬升并持续数分钟，用于演示告警检测。
     */
    private void generateEnv() {
        List<EnvMonitorPoint> points = envMonitorPointMapper.selectList(null);
        Random r = ThreadLocalRandom.current();

        // 第一遍：温度/风速/PM 等基础量随机游走（同时更新设备温度快照，供湿度联动）
        for (EnvMonitorPoint p : points) {
            String sub = p.getMonitorSubType();
            if (sub == null) continue;
            BigDecimal value;
            switch (sub) {
                case "温度" -> value = walkEnv(p.getId(), p.getMonitorSubType(), 0.35, 20, 36, null);
                case "湿度" -> value = null; // 第二遍基于温度计算
                case "PM2.5" -> value = walkEnv(p.getId(), p.getMonitorSubType(), 2.5, 25, 165, 160.0);
                case "PM10" -> value = null; // 第二遍基于 PM2.5 关联计算
                case "噪声" -> value = walkEnv(p.getId(), p.getMonitorSubType(), 1.5, 52, 95, 92.0);
                case "风速" -> value = walkEnv(p.getId(), p.getMonitorSubType(), 0.7, 2, 20, 19.0);
                default -> value = walkEnv(p.getId(), p.getMonitorSubType(), 2.0, 30, 90, null);
            }
            if (value != null) {
                BigDecimal temp = (sub.equals("温度")) ? value : null;
                if (temp != null) deviceTemp.put(p.getDeviceId(), temp);
                insertEnvData(p, value);
            }
        }

        // 第二遍：湿度（由同设备温度线性推导）与 PM10（由同设备 PM2.5 推导）
        for (EnvMonitorPoint p : points) {
            String sub = p.getMonitorSubType();
            if (sub == null) continue;
            if (sub.equals("湿度")) {
                BigDecimal temp = deviceTemp.get(p.getDeviceId());
                BigDecimal value = humidityFromTemp(temp, r);
                insertEnvData(p, value);
            } else if (sub.equals("PM10")) {
                BigDecimal pm25 = latestEnvValue(p.getDeviceId(), "PM2.5");
                BigDecimal value = pm25 == null
                        ? BigDecimal.valueOf(80 + r.nextDouble() * 10).setScale(1, RoundingMode.HALF_UP)
                        : pm25.multiply(new BigDecimal("1.8"))
                        .add(BigDecimal.valueOf(r.nextDouble() * 4 - 2))
                        .setScale(1, RoundingMode.HALF_UP);
                insertEnvData(p, value);
            }
        }
    }

    /** 随机游走生成环境值：last ± 小步长，夹在 [min,max]；故障注入时按 faultTarget 抬升 */
    private BigDecimal walkEnv(Long pointId, String subType, double step, double min, double max, Double faultTarget) {
        Random r = ThreadLocalRandom.current();
        BigDecimal last = envState.get(pointId);
        if (last == null) {
            last = currentEnvValue(pointId);
            if (last == null) {
                last = BigDecimal.valueOf((min + max) / 2).setScale(1, RoundingMode.HALF_UP);
            }
        }
        double cur = last.doubleValue();
        // 故障注入：触发异常事件（概率 1.2%/tick，约每 7~15 分钟一次），持续 24~60 tick（2~5 分钟）
        Integer remain = envFaultTicks.getOrDefault(pointId, 0);
        if (remain <= 0 && faultTarget != null && r.nextDouble() < 0.012) {
            remain = 24 + r.nextInt(37);
            envFaultTicks.put(pointId, remain);
            System.out.println("[ENV-FAULT] " + subType + " 监测点发生异常事件，演示告警检测");
        }
        double value;
        if (remain > 0) {
            // 异常期：向故障目标值快速抬升（仍带渐变，不瞬间跳变）
            double target = faultTarget != null ? faultTarget : cur + step * 10;
            value = cur + (target - cur) * 0.35 + (r.nextDouble() - 0.5) * step * 2;
            envFaultTicks.put(pointId, remain - 1);
        } else {
            // 正常期：小步长随机游走（偏线性）
            value = cur + (r.nextDouble() - 0.5) * step * 2;
        }
        value = Math.max(min, Math.min(max, value));
        BigDecimal bd = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
        envState.put(pointId, bd);
        return bd;
    }

    /** 湿度 = 88 - (温度-20)×3.2 + 噪声，夹在 20~98%（温度升高湿度下降，近似线性关联） */
    private BigDecimal humidityFromTemp(BigDecimal temp, Random r) {
        double t = temp == null ? 28 : temp.doubleValue();
        double h = HUMIDITY_BASE.doubleValue() - (t - 20) * HUMIDITY_SLOPE.doubleValue()
                + (r.nextDouble() - 0.5) * 3;
        h = Math.max(20, Math.min(98, h));
        return BigDecimal.valueOf(h).setScale(1, RoundingMode.HALF_UP);
    }

    /** 写入环境数据并做阈值判断 */
    private void insertEnvData(EnvMonitorPoint p, BigDecimal value) {
        EnvData data = new EnvData();
        data.setPointId(p.getId());
        data.setIndexValue(value);
        data.setCollectTime(LocalDateTime.now());
        envDataMapper.insert(data);

        // 阈值判断：warn/alarm 上下限
        if (p.getAlarmMax() != null && value.compareTo(p.getAlarmMax()) > 0) {
            addAlarm(2, 2, p.getDeviceId(), p.getId(), p.getPointName() + "超标(警报)：" + value + p.getUnit(), value, null);
        } else if (p.getWarnMax() != null && value.compareTo(p.getWarnMax()) > 0) {
            addAlarm(2, 1, p.getDeviceId(), p.getId(), p.getPointName() + "超标(预警)：" + value + p.getUnit(), value, null);
        } else if (p.getAlarmMin() != null && value.compareTo(p.getAlarmMin()) < 0) {
            addAlarm(2, 2, p.getDeviceId(), p.getId(), p.getPointName() + "低于下限(警报)：" + value + p.getUnit(), value, null);
        } else if (p.getWarnMin() != null && value.compareTo(p.getWarnMin()) < 0) {
            addAlarm(2, 1, p.getDeviceId(), p.getId(), p.getPointName() + "低于下限(预警)：" + value + p.getUnit(), value, null);
        }
    }

    /** 同设备某子类型环境点的最新值（用于 PM10/湿度关联计算） */
    private BigDecimal latestEnvValue(Long deviceId, String subType) {
        if (deviceId == null) return null;
        EnvMonitorPoint ep = envMonitorPointMapper.selectOne(
                new LambdaQueryWrapper<EnvMonitorPoint>()
                        .eq(EnvMonitorPoint::getDeviceId, deviceId)
                        .eq(EnvMonitorPoint::getMonitorSubType, subType)
                        .last("LIMIT 1"));
        if (ep == null) return null;
        return envState.get(ep.getId());
    }

    /** 生成塔吊作业记录（T-14 / RQ-16）：基于最新实时数据组装一条吊装作业 */
    private void generateCraneRecord(Long deviceId) {
        try {
            Random r = ThreadLocalRandom.current();
            BigDecimal load = latest(deviceId, "load");
            BigDecimal radius = latest(deviceId, "radius");
            BigDecimal wind = latest(deviceId, "wind_speed");
            BigDecimal height = latest(deviceId, "height");
            if (load == null) return;
            TowerCraneParam param = towerCraneParamMapper.selectOne(
                    new LambdaQueryWrapper<TowerCraneParam>().eq(TowerCraneParam::getDeviceId, deviceId));
            BigDecimal rated = (param == null || param.getRatedLoad() == null)
                    ? BigDecimal.ONE : param.getRatedLoad();
            BigDecimal percent = load.divide(rated, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            TowerCraneRecord rec = new TowerCraneRecord();
            rec.setDeviceId(deviceId);
            rec.setStartTime(LocalDateTime.now().minusMinutes(2).minusSeconds(r.nextInt(50)));
            rec.setEndTime(LocalDateTime.now());
            rec.setHoistingWeight(load);
            rec.setMaxLoadPercent(percent);
            rec.setMaxRadius(radius == null ? null : radius.add(BigDecimal.valueOf(r.nextDouble() * 3)).setScale(2, RoundingMode.HALF_UP));
            rec.setMinRadius(radius == null ? null : radius.subtract(BigDecimal.valueOf(r.nextDouble() * 2)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            rec.setMaxHeight(height == null ? null : height.add(BigDecimal.valueOf(5 + r.nextDouble() * 10)).setScale(2, RoundingMode.HALF_UP));
            rec.setMinHeight(height == null ? null : height.subtract(BigDecimal.valueOf(2 + r.nextDouble() * 4)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            rec.setMaxWindSpeed(wind);
            rec.setMaxLoad(param == null ? null : param.getMaxLoad());
            rec.setStartAngle(BigDecimal.valueOf(r.nextDouble() * 360).setScale(1, RoundingMode.HALF_UP));
            rec.setEndAngle(BigDecimal.valueOf(r.nextDouble() * 360).setScale(1, RoundingMode.HALF_UP));
            rec.setHookRadius(radius == null ? null : radius.setScale(2, RoundingMode.HALF_UP));
            rec.setHookHeight(height == null ? null : height.setScale(2, RoundingMode.HALF_UP));
            rec.setUnloadRadius(BigDecimal.valueOf(10 + r.nextDouble() * 40).setScale(2, RoundingMode.HALF_UP));
            rec.setUnloadHeight(BigDecimal.valueOf(5 + r.nextDouble() * 60).setScale(2, RoundingMode.HALF_UP));
            rec.setRemark("吊装作业（模拟数据）");
            towerCraneRecordMapper.insert(rec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 生成升降机作业记录（T-15 / RQ-20） */
    private void generateLiftRecord(Long deviceId) {
        try {
            Random r = ThreadLocalRandom.current();
            BigDecimal load = latest(deviceId, "load_weight");
            BigDecimal persons = latest(deviceId, "person_count");
            BigDecimal height = latest(deviceId, "height");
            BigDecimal wind = latest(deviceId, "wind_speed");
            BigDecimal dir = latest(deviceId, "direction");
            if (load == null) return;
            LiftRecord rec = new LiftRecord();
            rec.setDeviceId(deviceId);
            rec.setStartTime(LocalDateTime.now().minusMinutes(1).minusSeconds(r.nextInt(40)));
            rec.setEndTime(LocalDateTime.now());
            rec.setLoadWeight(load);
            rec.setPersonCount(persons == null ? 0 : persons.intValue());
            rec.setStartFloor(1 + r.nextInt(12));
            rec.setEndFloor(1 + r.nextInt(12));
            rec.setWindSpeed(wind);
            rec.setRunSpeed(BigDecimal.valueOf(0.5 + r.nextDouble() * 2.5).setScale(2, RoundingMode.HALF_UP));
            rec.setTiltAngleX(BigDecimal.valueOf(r.nextDouble() * 2).setScale(2, RoundingMode.HALF_UP));
            rec.setTiltAngleY(BigDecimal.valueOf(r.nextDouble() * 2).setScale(2, RoundingMode.HALF_UP));
            rec.setStartHeight(height == null ? null : height.subtract(BigDecimal.valueOf(3 + r.nextDouble() * 5)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            rec.setEndHeight(height == null ? null : height.setScale(2, RoundingMode.HALF_UP));
            rec.setDirection(dir == null ? 1 : dir.intValue());
            rec.setRemark("载人/载货运行（模拟数据）");
            liftRecordMapper.insert(rec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 设备参数告警（预警/警报两级） */
    private void checkDeviceAlarm(Long deviceId, Long pointId, String name, BigDecimal value,
                                  BigDecimal warn, BigDecimal alarm, String unit) {
        if (value == null) return;
        if (value.compareTo(alarm) > 0) {
            addAlarm(1, 2, deviceId, pointId, name + "超标(警报)：" + value + unit, value, null);
        } else if (value.compareTo(warn) > 0) {
            addAlarm(1, 1, deviceId, pointId, name + "超标(预警)：" + value + unit, value, null);
        }
    }

    /** 写入实时数据表（同步 Redis 最新值缓存，T-34） */
    private void write(Long deviceId, String paramCode, BigDecimal value, String unit) {
        RealtimeData d = new RealtimeData();
        d.setDeviceId(deviceId);
        d.setParamCode(paramCode);
        d.setParamValue(value);
        d.setUnit(unit);
        d.setCollectTime(LocalDateTime.now());
        realtimeDataMapper.insert(d);
        if (redisCacheService != null) {
            redisCacheService.cacheLatest(deviceId, paramCode, value.toPlainString());
        }
    }

    private BigDecimal latest(Long deviceId, String paramCode) {
        RealtimeData d = realtimeDataMapper.selectOne(
                new LambdaQueryWrapper<RealtimeData>()
                        .eq(RealtimeData::getDeviceId, deviceId)
                        .eq(RealtimeData::getParamCode, paramCode)
                        .orderByDesc(RealtimeData::getCollectTime)
                        .last("LIMIT 1"));
        return d == null ? null : d.getParamValue();
    }

    /**
     * 恢复自动记录（T-21 / RQ-28）：数据恢复正常后自动记录恢复时间与恢复数据值。
     * 对未处置且未恢复的 设备监测(1)/环境监测(2) 告警，若当前值已回到预警阈值范围内，
     * 自动写入 recover_time 与 recover_value（AI 告警无持续数值，不自动恢复）。
     */
    private void recoverAlarms() {
        List<Alarm> active = alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .ne(Alarm::getHandleStatus, 2)
                        .isNull(Alarm::getRecoverTime)
                        .in(Alarm::getAlarmSource, 1, 2));
        for (Alarm a : active) {
            try {
                BigDecimal cur;
                BigDecimal warnMax;
                BigDecimal warnMin;
                if (a.getAlarmSource() == 1 && a.getPointId() != null) {
                    DeviceMonitorPoint p = deviceMonitorPointMapper.selectById(a.getPointId());
                    if (p == null || p.getDeviceId() == null) continue;
                    cur = currentDeviceValue(p.getDeviceId(), p.getMonitorSubType());
                    warnMax = p.getWarnMax();
                    warnMin = p.getWarnMin();
                } else if (a.getAlarmSource() == 2 && a.getPointId() != null) {
                    EnvMonitorPoint p = envMonitorPointMapper.selectById(a.getPointId());
                    if (p == null) continue;
                    cur = currentEnvValue(a.getPointId());
                    warnMax = p.getWarnMax();
                    warnMin = p.getWarnMin();
                } else {
                    continue;
                }
                if (cur == null) continue;
                boolean recovered = (warnMax == null || cur.compareTo(warnMax) <= 0)
                        && (warnMin == null || cur.compareTo(warnMin) >= 0);
                if (recovered) {
                    Alarm upd = new Alarm();
                    upd.setId(a.getId());
                    upd.setRecoverTime(LocalDateTime.now());
                    upd.setRecoverValue(cur);
                    alarmMapper.updateById(upd);
                    System.out.println("[ALARM-RECOVER] " + a.getAlarmNo() + " 数据恢复, 恢复值=" + cur);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** 设备监测点当前实时值（按监测子类型映射实时参数；力矩为计算值） */
    private BigDecimal currentDeviceValue(Long deviceId, String subType) {
        if (subType == null) return null;
        return switch (subType) {
            case "力矩" -> {
                BigDecimal load = latest(deviceId, "load");
                BigDecimal radius = latest(deviceId, "radius");
                yield (load == null || radius == null) ? null
                        : load.multiply(radius).setScale(2, RoundingMode.HALF_UP);
            }
            case "吊重" -> latest(deviceId, "load");
            case "风速" -> latest(deviceId, "wind_speed");
            case "载重" -> latest(deviceId, "load_weight");
            default -> null;
        };
    }

    /** 环境监测点当前值（最新一条 env_data） */
    private BigDecimal currentEnvValue(Long pointId) {
        EnvData d = envDataMapper.selectOne(
                new LambdaQueryWrapper<EnvData>()
                        .eq(EnvData::getPointId, pointId)
                        .orderByDesc(EnvData::getCollectTime)
                        .last("LIMIT 1"));
        return d == null ? null : d.getIndexValue();
    }

    /** 新增告警（10 分钟内同设备同点同级别未处置的不重复插入） */
    private void addAlarm(int source, int level, Long deviceId, Long pointId, String content, BigDecimal value, Long cameraId) {
        LocalDateTime tenMinAgo = LocalDateTime.now().minusMinutes(10);
        Long count = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getAlarmSource, source)
                        .eq(Alarm::getAlarmLevel, level)
                        .eq(Alarm::getDeviceId, deviceId)
                        .eq(pointId != null, Alarm::getPointId, pointId)
                        .ne(Alarm::getHandleStatus, 2)
                        .ge(Alarm::getAlarmTime, tenMinAgo));
        if (count != null && count > 0) {
            return; // 已有活跃告警，不重复生成
        }
        Alarm alarm = new Alarm();
        alarm.setAlarmNo("AL" + LocalDateTime.now().format(NO_FMT) + ThreadLocalRandom.current().nextInt(100, 999));
        alarm.setBatchNo("B" + LocalDateTime.now().format(NO_FMT));
        alarm.setAlarmSource(source);
        alarm.setAlarmLevel(level);
        alarm.setDeviceId(deviceId);
        alarm.setPointId(pointId);
        alarm.setCameraId(cameraId);
        alarm.setAlarmContent(content);
        alarm.setAlarmValue(value);
        alarm.setAlarmTime(LocalDateTime.now());
        alarm.setHandleStatus(0);
        alarmMapper.insert(alarm);
        System.out.println("[ALARM] " + alarm.getAlarmNo() + " " + content);
        broadcastAlarm(alarm);
    }

    /** 广播新告警事件（T-35 全局告警提醒） */
    private void broadcastAlarm(Alarm alarm) {
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
            msg.put("data", data);
            RealtimeWebSocketHandler.broadcast(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 广播最新状态给所有前端 */
    private void broadcast() {
        try {
            RealtimeWebSocketHandler.broadcast(objectMapper.writeValueAsString(monitorService.buildRealtimePayload()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
