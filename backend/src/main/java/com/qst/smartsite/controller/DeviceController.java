package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.*;
import com.qst.smartsite.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备资产管理接口（T-06~T-13，RQ-06~RQ-13）
 * 台账 CRUD + 全生命周期 + 监测点 + 实时/历史数据 + 告警/离线记录 + 在线率
 */
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceMonitorPointMapper monitorPointMapper;

    @Autowired
    private RealtimeDataMapper realtimeDataMapper;

    @Autowired
    private DeviceOfflineRecordMapper offlineRecordMapper;

    @Autowired
    private AlarmMapper alarmMapper;

    /** 设备分页查询（关键字/类型/位置/状态筛选） */
    @GetMapping("/page")
    public Result<IPage<Device>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long typeId,
                                     @RequestParam(required = false) Long locationId,
                                     @RequestParam(required = false) Integer status) {
        return Result.ok(deviceMapper.selectDevicePage(
                new Page<>(pageNum, pageSize), keyword, typeId, locationId, status));
    }

    /** 设备全量列表（兼容旧接口） */
    @GetMapping("/list")
    public Result<List<Device>> list() {
        return Result.ok(deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().orderByDesc(Device::getCreateTime)));
    }

    /** 设备详情（含类型/位置名称） */
    @GetMapping("/{id}")
    public Result<Device> detail(@PathVariable Long id) {
        Device device = deviceMapper.selectDetail(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        return Result.ok(device);
    }

    /** 新增设备（T-08） */
    @PostMapping
    public Result<Void> add(@RequestBody Device device) {
        if (device.getDeviceCode() == null || device.getDeviceCode().isBlank()) {
            throw new BusinessException(400, "设备编码不能为空");
        }
        Long exists = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceCode, device.getDeviceCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "设备编码已存在");
        }
        if (device.getStatus() == null) device.setStatus(0);
        if (device.getEnableStatus() == null) device.setEnableStatus(1);
        // 生命周期联动：有启用日期时自动生成预计报废日期
        fillExpectScrap(device);
        deviceMapper.insert(device);
        return Result.ok();
    }

    /** 编辑设备（基本信息 + 全生命周期日期维护 T-12） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Device device) {
        Device db = deviceMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "设备不存在");
        }
        device.setId(id);
        device.setDeviceCode(null); // 编码为唯一标识，不允许修改
        fillExpectScrap(device);
        deviceMapper.updateById(device);
        return Result.ok();
    }

    /** 删除设备（同步删除其监测点；告警/离线等历史记录保留） */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        deviceMapper.deleteById(id);
        monitorPointMapper.delete(new LambdaQueryWrapper<DeviceMonitorPoint>()
                .eq(DeviceMonitorPoint::getDeviceId, id));
        return Result.ok();
    }

    // ============ 设备监测点管理（T-10，RQ-12） ============

    /** 设备监测点列表 */
    @GetMapping("/{id}/points")
    public Result<List<DeviceMonitorPoint>> points(@PathVariable Long id) {
        return Result.ok(monitorPointMapper.selectList(
                new LambdaQueryWrapper<DeviceMonitorPoint>()
                        .eq(DeviceMonitorPoint::getDeviceId, id)
                        .orderByAsc(DeviceMonitorPoint::getId)));
    }

    /** 新增监测点（含预警/报警上下限、喷淋联动配置） */
    @PostMapping("/point")
    public Result<Void> addPoint(@RequestBody DeviceMonitorPoint point) {
        if (point.getPointCode() == null || point.getPointCode().isBlank()) {
            throw new BusinessException(400, "监测点编码不能为空");
        }
        Long exists = monitorPointMapper.selectCount(
                new LambdaQueryWrapper<DeviceMonitorPoint>()
                        .eq(DeviceMonitorPoint::getPointCode, point.getPointCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "监测点编码已存在");
        }
        if (point.getMonitorType() == null) point.setMonitorType("device");
        if (point.getCollectInterval() == null) point.setCollectInterval(30);
        if (point.getSprayEnabled() == null) point.setSprayEnabled(0);
        if (point.getStatus() == null) point.setStatus(1);
        monitorPointMapper.insert(point);
        return Result.ok();
    }

    /** 编辑监测点 */
    @PutMapping("/point/{pointId}")
    public Result<Void> updatePoint(@PathVariable Long pointId, @RequestBody DeviceMonitorPoint point) {
        DeviceMonitorPoint db = monitorPointMapper.selectById(pointId);
        if (db == null) {
            throw new BusinessException(404, "监测点不存在");
        }
        point.setId(pointId);
        point.setPointCode(null); // 编码为唯一标识，不允许修改
        monitorPointMapper.updateById(point);
        return Result.ok();
    }

    /** 删除监测点 */
    @DeleteMapping("/point/{pointId}")
    public Result<Void> deletePoint(@PathVariable Long pointId) {
        monitorPointMapper.deleteById(pointId);
        return Result.ok();
    }

    // ============ 实时/历史数据（T-09） ============

    /** 设备实时数据（各参数最新值） */
    @GetMapping("/{id}/realtime")
    public Result<List<RealtimeData>> realtime(@PathVariable Long id) {
        return Result.ok(realtimeDataMapper.selectLatestByDevice(id));
    }

    /** 设备历史数据（按监测点 pointId 或参数编码 paramCode 查近 N 小时） */
    @GetMapping("/{id}/history")
    public Result<List<RealtimeData>> history(@PathVariable Long id,
                                              @RequestParam(required = false) Long pointId,
                                              @RequestParam(required = false) String paramCode,
                                              @RequestParam(defaultValue = "24") Integer hours) {
        LambdaQueryWrapper<RealtimeData> wrapper = new LambdaQueryWrapper<RealtimeData>()
                .eq(RealtimeData::getDeviceId, id)
                .ge(RealtimeData::getCollectTime, LocalDateTime.now().minusHours(hours))
                .orderByAsc(RealtimeData::getCollectTime);
        if (pointId != null) {
            wrapper.eq(RealtimeData::getPointId, pointId);
        } else if (paramCode != null && !paramCode.isBlank()) {
            wrapper.eq(RealtimeData::getParamCode, paramCode);
        }
        return Result.ok(realtimeDataMapper.selectList(wrapper));
    }

    // ============ 离线记录 / 在线率（T-11，RQ-13） ============

    /** 设备离线/上线变更记录（按时间段查询） */
    @GetMapping("/{id}/offline-records")
    public Result<List<DeviceOfflineRecord>> offlineRecords(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "30") Integer days) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        return Result.ok(offlineRecordMapper.selectRange(id, start.toString(), LocalDateTime.now().toString()));
    }

    /**
     * 设备在线率统计（按时间段状态变更记录推断在线时长占比）
     * 返回：offlineCount 离线次数 / changeCount 变更次数 / onlineRate 在线率(%)
     */
    @GetMapping("/{id}/online-rate")
    public Result<Map<String, Object>> onlineRate(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "30") Integer days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<DeviceOfflineRecord> recs = offlineRecordMapper.selectRange(id, start.toString(), end.toString());
        long offlineCount = offlineRecordMapper.countOffline(id, start.toString(), end.toString());
        long changeCount = offlineRecordMapper.countChange(id, start.toString(), end.toString());

        double onlineRate;
        if (recs.isEmpty()) {
            onlineRate = 100.0; // 无变更记录视为始终在线
        } else {
            Device device = deviceMapper.selectById(id);
            boolean online = device != null && device.getStatus() != null && device.getStatus() == 1;
            long totalSec = java.time.Duration.between(start, end).getSeconds();
            long onlineSec = 0;
            LocalDateTime cursor = start;
            for (DeviceOfflineRecord r : recs) {
                if (online) {
                    onlineSec += java.time.Duration.between(cursor, r.getRecordTime()).getSeconds();
                }
                online = (r.getStatus() != null && r.getStatus() == 2); // 2-上线
                cursor = r.getRecordTime();
            }
            if (online) {
                onlineSec += java.time.Duration.between(cursor, end).getSeconds();
            }
            onlineRate = totalSec <= 0 ? 100.0 : Math.round(onlineSec * 10000.0 / totalSec) / 100.0;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("offlineCount", offlineCount);
        result.put("changeCount", changeCount);
        result.put("onlineRate", onlineRate);
        result.put("statDays", days);
        return Result.ok(result);
    }

    // ============ 详情聚合（T-09） ============

    /** 设备详情页聚合数据：基本信息 + 监测点 + 实时数据 + 告警记录 + 离线记录 + 在线率 */
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> detailAgg(@PathVariable Long id) {
        Device device = deviceMapper.selectDetail(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        LocalDateTime start30 = LocalDateTime.now().minusDays(30);
        Map<String, Object> result = new HashMap<>();
        result.put("device", device);
        result.put("points", monitorPointMapper.selectList(
                new LambdaQueryWrapper<DeviceMonitorPoint>()
                        .eq(DeviceMonitorPoint::getDeviceId, id)
                        .orderByAsc(DeviceMonitorPoint::getId)));
        result.put("realtime", realtimeDataMapper.selectLatestByDevice(id));
        result.put("alarms", alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getDeviceId, id)
                        .ge(Alarm::getAlarmTime, start30)
                        .orderByDesc(Alarm::getAlarmTime)));
        result.put("offlineRecords", offlineRecordMapper.selectRange(
                id, start30.toString(), LocalDateTime.now().toString()));
        return Result.ok(result);
    }

    /** 生命周期联动：启用日期 + 设计使用年限 → 预计报废日期 */
    private void fillExpectScrap(Device device) {
        if (device.getEnableDate() != null && device.getDesignServiceLife() != null
                && device.getExpectScrapDate() == null) {
            device.setExpectScrapDate(device.getEnableDate().plusYears(device.getDesignServiceLife()));
        }
    }
}
