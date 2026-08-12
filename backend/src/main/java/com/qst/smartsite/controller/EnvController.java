package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.EnvDailyStat;
import com.qst.smartsite.entity.EnvData;
import com.qst.smartsite.entity.EnvMonitorPoint;
import com.qst.smartsite.mapper.EnvDailyStatMapper;
import com.qst.smartsite.mapper.EnvDataMapper;
import com.qst.smartsite.mapper.EnvMonitorPointMapper;
import com.qst.smartsite.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境监测接口
 * 对应《页面功能清单》九、环境监测
 * T-22 环境监测点管理 / T-23 日统计数据
 */
@RestController
@RequestMapping("/api/env")
public class EnvController {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private EnvDataMapper envDataMapper;

    @Autowired
    private EnvMonitorPointMapper envMonitorPointMapper;

    @Autowired
    private EnvDailyStatMapper envDailyStatMapper;

    /** 环境监测点实时状态 */
    @GetMapping("/points")
    public Result<List<Map<String, Object>>> points() {
        return Result.ok(monitorService.listEnvStatus());
    }

    /** 单个监测点历史趋势（默认最近 24 小时） */
    @GetMapping("/history")
    public Result<List<EnvData>> history(@RequestParam Long pointId,
                                         @RequestParam(defaultValue = "24") Integer hours) {
        LocalDateTime start = LocalDateTime.now().minusHours(hours);
        List<EnvData> list = envDataMapper.selectList(
                new LambdaQueryWrapper<EnvData>()
                        .eq(EnvData::getPointId, pointId)
                        .ge(EnvData::getCollectTime, start)
                        .orderByAsc(EnvData::getCollectTime)
                        .last("LIMIT 500"));
        return Result.ok(list);
    }

    /* ==================== T-22 环境监测点管理 ==================== */

    /** 新增监测点（编码唯一；监测类型固定 env） */
    @PostMapping("/point")
    public Result<Void> addPoint(@RequestBody EnvMonitorPoint point) {
        validatePoint(point);
        Long exists = envMonitorPointMapper.selectCount(
                new LambdaQueryWrapper<EnvMonitorPoint>()
                        .eq(EnvMonitorPoint::getPointCode, point.getPointCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "监测点编码已存在");
        }
        point.setId(null);
        if (point.getMonitorType() == null || point.getMonitorType().isBlank()) {
            point.setMonitorType("env");
        }
        if (point.getStatus() == null) {
            point.setStatus(1);
        }
        envMonitorPointMapper.insert(point);
        return Result.ok();
    }

    /** 编辑监测点（编码为唯一标识，不允许修改） */
    @PutMapping("/point/{id}")
    public Result<Void> updatePoint(@PathVariable Long id, @RequestBody EnvMonitorPoint point) {
        EnvMonitorPoint db = envMonitorPointMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "监测点不存在");
        }
        point.setId(id);
        point.setPointCode(null);
        envMonitorPointMapper.updateById(point);
        return Result.ok();
    }

    /** 删除监测点 */
    @DeleteMapping("/point/{id}")
    public Result<Void> deletePoint(@PathVariable Long id) {
        EnvMonitorPoint db = envMonitorPointMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "监测点不存在");
        }
        envMonitorPointMapper.deleteById(id);
        return Result.ok();
    }

    /** 校验监测点必填字段 */
    private void validatePoint(EnvMonitorPoint point) {
        if (point.getPointCode() == null || point.getPointCode().isBlank()) {
            throw new BusinessException(400, "监测点编码不能为空");
        }
        if (point.getPointName() == null || point.getPointName().isBlank()) {
            throw new BusinessException(400, "监测点名称不能为空");
        }
        if (point.getDeviceId() == null) {
            throw new BusinessException(400, "所属设备不能为空");
        }
        if (point.getMonitorSubType() == null || point.getMonitorSubType().isBlank()) {
            throw new BusinessException(400, "监测子类型不能为空");
        }
    }

    /* ==================== T-23 日统计数据 ==================== */

    /**
     * 日统计数据（默认今天；days 表示最近 N 天）
     * 数据由 t_env_data 实时按天聚合生成，并同步落表 t_env_daily_stat
     */
    @GetMapping("/daily-stats")
    public Result<List<Map<String, Object>>> dailyStats(@RequestParam Long pointId,
                                                        @RequestParam(defaultValue = "1") Integer days) {
        EnvMonitorPoint point = envMonitorPointMapper.selectById(pointId);
        if (point == null) {
            throw new BusinessException(404, "监测点不存在");
        }
        int statDays = Math.max(1, Math.min(days == null ? 1 : days, 30));
        LocalDateTime start = LocalDate.now().minusDays(statDays - 1L).atStartOfDay();

        List<Map<String, Object>> agg = envDailyStatMapper.aggregateDaily(pointId, start);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : agg) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pointId", pointId);
            item.put("statDate", String.valueOf(row.get("stat_date")));
            item.put("maxValue", row.get("max_val"));
            item.put("minValue", row.get("min_val"));
            item.put("avgValue", row.get("avg_val"));
            item.put("dataCount", row.get("data_count"));
            result.add(item);
            saveDailyStat(pointId, item);
        }
        return Result.ok(result);
    }

    /** 同步日统计到 t_env_daily_stat（存在则更新，不存在则插入） */
    private void saveDailyStat(Long pointId, Map<String, Object> item) {
        try {
            LocalDate date = LocalDate.parse(String.valueOf(item.get("statDate")));
            EnvDailyStat stat = envDailyStatMapper.selectOne(
                    new LambdaQueryWrapper<EnvDailyStat>()
                            .eq(EnvDailyStat::getPointId, pointId)
                            .eq(EnvDailyStat::getStatDate, date));
            if (stat == null) {
                stat = new EnvDailyStat();
                stat.setPointId(pointId);
                stat.setStatDate(date);
                stat.setMaxValue(toDecimal(item.get("maxValue")));
                stat.setMinValue(toDecimal(item.get("minValue")));
                stat.setAvgValue(toDecimal(item.get("avgValue")));
                envDailyStatMapper.insert(stat);
            } else {
                stat.setMaxValue(toDecimal(item.get("maxValue")));
                stat.setMinValue(toDecimal(item.get("minValue")));
                stat.setAvgValue(toDecimal(item.get("avgValue")));
                envDailyStatMapper.updateById(stat);
            }
        } catch (Exception e) {
            // 统计落表失败不影响查询结果
        }
    }

    private BigDecimal toDecimal(Object o) {
        return o == null ? null : new BigDecimal(o.toString());
    }
}