package com.qst.smartsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.dto.CraneStatusVO;
import com.qst.smartsite.dto.LiftStatusVO;
import com.qst.smartsite.entity.*;
import com.qst.smartsite.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 监控聚合服务：组装塔吊/升降机/环境实时状态
 * Controller 与 WebSocket 广播共用
 */
@Service
public class MonitorService {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private TowerCraneParamMapper towerCraneParamMapper;
    @Autowired
    private LiftParamMapper liftParamMapper;
    @Autowired
    private EnvMonitorPointMapper envMonitorPointMapper;
    @Autowired
    private RealtimeDataMapper realtimeDataMapper;
    @Autowired
    private EnvDataMapper envDataMapper;
    @Autowired
    private DeviceLocationMapper deviceLocationMapper;

    /** 塔吊类型ID：大型机械→塔吊（数据来自 init_data.sql） */
    private static final long TYPE_TOWER_CRANE = 2;
    /** 升降机类型ID */
    private static final long TYPE_LIFT = 3;

    /** 设备类型→最新实时数据索引 key: deviceId + ":" + paramCode */
    private Map<String, RealtimeData> latestIndex() {
        return realtimeDataMapper.selectLatestByDeviceParam().stream()
                .collect(Collectors.toMap(
                        d -> d.getDeviceId() + ":" + d.getParamCode(),
                        Function.identity(), (a, b) -> a));
    }

    /**
     * 塔吊实时状态列表
     */
    public List<CraneStatusVO> listCraneStatus() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getTypeId, TYPE_TOWER_CRANE));
        List<TowerCraneParam> params = towerCraneParamMapper.selectList(null);
        Map<Long, TowerCraneParam> paramMap = params.stream()
                .collect(Collectors.toMap(TowerCraneParam::getDeviceId, Function.identity()));
        Map<String, RealtimeData> latest = latestIndex();

        List<CraneStatusVO> result = new ArrayList<>();
        for (Device d : devices) {
            CraneStatusVO vo = new CraneStatusVO();
            vo.setDeviceId(d.getId());
            vo.setDeviceCode(d.getDeviceCode());
            vo.setDeviceName(d.getDeviceName());
            vo.setStatus(d.getStatus());
            TowerCraneParam p = paramMap.get(d.getId());
            if (p != null) {
                vo.setFrontArmLen(p.getFrontArmLen());
                vo.setMaxHeight(p.getMaxHeight());
                vo.setRatedLoad(p.getRatedLoad());
                vo.setMaxLoad(p.getMaxLoad());
                vo.setRatedMoment(p.getRatedMoment());
            }
            vo.setLoadVal(val(latest, d.getId(), "load"));
            vo.setRadiusVal(val(latest, d.getId(), "radius"));
            vo.setWindSpeed(val(latest, d.getId(), "wind_speed"));
            vo.setHeight(val(latest, d.getId(), "height"));
            vo.setAngle(val(latest, d.getId(), "angle"));
            result.add(vo);
        }
        return result;
    }

    /**
     * 升降机实时状态列表
     */
    public List<LiftStatusVO> listLiftStatus() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getTypeId, TYPE_LIFT));
        List<LiftParam> params = liftParamMapper.selectList(null);
        Map<Long, LiftParam> paramMap = params.stream()
                .collect(Collectors.toMap(LiftParam::getDeviceId, Function.identity()));
        Map<String, RealtimeData> latest = latestIndex();

        List<LiftStatusVO> result = new ArrayList<>();
        for (Device d : devices) {
            LiftStatusVO vo = new LiftStatusVO();
            vo.setDeviceId(d.getId());
            vo.setDeviceCode(d.getDeviceCode());
            vo.setDeviceName(d.getDeviceName());
            vo.setStatus(d.getStatus());
            LiftParam p = paramMap.get(d.getId());
            if (p != null) {
                vo.setRatedLoad(p.getRatedLoad());
                vo.setMaxLiftHeight(p.getMaxLiftHeight());
                vo.setLiftSpeed(p.getLiftSpeed());
                vo.setBaseHeight(p.getBaseHeight());
            }
            vo.setLoadWeight(val(latest, d.getId(), "load_weight"));
            BigDecimal persons = val(latest, d.getId(), "person_count");
            vo.setPersonCount(persons == null ? null : persons.intValue());
            vo.setHeight(val(latest, d.getId(), "height"));
            vo.setWindSpeed(val(latest, d.getId(), "wind_speed"));
            BigDecimal dir = val(latest, d.getId(), "direction");
            vo.setDirection(dir == null ? null : dir.intValue());
            BigDecimal doorFront = val(latest, d.getId(), "door_front");
            vo.setDoorFront(doorFront == null ? null : doorFront.intValue());
            BigDecimal doorBack = val(latest, d.getId(), "door_back");
            vo.setDoorBack(doorBack == null ? null : doorBack.intValue());
            result.add(vo);
        }
        return result;
    }

    /**
     * 环境监测点实时状态（监测点 + 最新值）
     */
    public List<Map<String, Object>> listEnvStatus() {
        List<EnvMonitorPoint> points = envMonitorPointMapper.selectList(null);
        List<EnvData> latestList = envDataMapper.selectList(
                new LambdaQueryWrapper<EnvData>()
                        .in(EnvData::getPointId, points.stream().map(EnvMonitorPoint::getId).collect(Collectors.toList()))
                        .orderByDesc(EnvData::getCollectTime)
                        .last("LIMIT 300")); // 只取最近 300 条，避免全表扫描
        // 每个监测点取第一条（最新）
        Map<Long, EnvData> latestMap = new HashMap<>();
        for (EnvData e : latestList) {
            latestMap.putIfAbsent(e.getPointId(), e);
        }
        // 设备 → 位置映射（按位置分组展示用）
        Map<Long, Device> deviceMap = deviceMapper.selectList(null).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity(), (a, b) -> a));
        Map<Long, DeviceLocation> locationMap = deviceLocationMapper.selectList(null).stream()
                .collect(Collectors.toMap(DeviceLocation::getId, Function.identity(), (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        for (EnvMonitorPoint p : points) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pointId", p.getId());
            item.put("pointName", p.getPointName());
            item.put("pointCode", p.getPointCode());
            item.put("monitorSubType", p.getMonitorSubType());
            item.put("unit", p.getUnit());
            item.put("warnMax", p.getWarnMax());
            item.put("alarmMax", p.getAlarmMax());
            item.put("warnMin", p.getWarnMin());
            item.put("alarmMin", p.getAlarmMin());
            Device dev = p.getDeviceId() == null ? null : deviceMap.get(p.getDeviceId());
            if (dev != null) {
                item.put("deviceName", dev.getDeviceName());
                DeviceLocation loc = dev.getLocationId() == null ? null : locationMap.get(dev.getLocationId());
                item.put("locationId", loc == null ? null : loc.getId());
                item.put("locationName", loc == null ? null : loc.getLocationName());
            } else {
                item.put("deviceName", null);
                item.put("locationId", null);
                item.put("locationName", null);
            }
            EnvData latest = latestMap.get(p.getId());
            if (latest != null) {
                item.put("value", latest.getIndexValue());
                item.put("collectTime", latest.getCollectTime());
            } else {
                item.put("value", null);
                item.put("collectTime", null);
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 实时推送 payload：塔吊 + 升降机 + 环境
     */
    public Map<String, Object> buildRealtimePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cranes", listCraneStatus());
        payload.put("lifts", listLiftStatus());
        payload.put("env", listEnvStatus());
        payload.put("time", java.time.LocalDateTime.now().toString());
        return payload;
    }

    private BigDecimal val(Map<String, RealtimeData> latest, Long deviceId, String paramCode) {
        RealtimeData d = latest.get(deviceId + ":" + paramCode);
        return d == null ? null : d.getParamValue();
    }
}
