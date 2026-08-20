package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.Alarm;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.mapper.AlarmMapper;
import com.qst.smartsite.mapper.DeviceMapper;
import com.qst.smartsite.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏 / 首页统计接口
 * 对应《页面功能清单》十一、数据大屏 与 二、首页工作台
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private AlarmMapper alarmMapper;
    @Autowired
    private MonitorService monitorService;

    /** 首页数据概览统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> result = new HashMap<>();
        long online = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
        long offline = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 0));
        long todayAlarms = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>().ge(Alarm::getAlarmTime, LocalDate.now().atStartOfDay()));
        long unhandled = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 0));

        result.put("onlineDevices", online);
        result.put("offlineDevices", offline);
        result.put("todayAlarms", todayAlarms);
        result.put("unhandledAlarms", unhandled);
        return Result.ok(result);
    }

    /** 数据大屏聚合数据 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new HashMap<>();
        long online = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
        long offline = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 0));
        Map<String, Object> deviceSummary = new HashMap<>();
        deviceSummary.put("online", online);
        deviceSummary.put("offline", offline);
        deviceSummary.put("total", online + offline);
        result.put("deviceSummary", deviceSummary);

        long unhandled = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 0));
        long handling = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 1));
        long handled = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getHandleStatus, 2));
        Map<String, Object> alarmSummary = new HashMap<>();
        alarmSummary.put("unhandled", unhandled);
        alarmSummary.put("handling", handling);
        alarmSummary.put("handled", handled);
        alarmSummary.put("total", unhandled + handling + handled);
        result.put("alarmSummary", alarmSummary);

        result.put("cranes", monitorService.listCraneStatus());
        result.put("lifts", monitorService.listLiftStatus());
        result.put("env", monitorService.listEnvStatus());
        result.put("alarmTrend", alarmMapper.countByDay(LocalDateTime.now().minusDays(7).toString()));
        result.put("byLevel", alarmMapper.countByLevel());
        return Result.ok(result);
    }
}
