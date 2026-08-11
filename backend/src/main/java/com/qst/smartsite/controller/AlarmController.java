package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.Alarm;
import com.qst.smartsite.mapper.AlarmMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警管理接口
 * 对应《页面功能清单》八、告警管理
 */
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    @Autowired
    private AlarmMapper alarmMapper;
    @Autowired
    private com.qst.smartsite.mapper.OperationLogMapper operationLogMapper;

    /** 告警分页列表 */
    @GetMapping("/list")
    public Result<Page<Alarm>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                    @RequestParam(required = false) Integer alarmLevel,
                                    @RequestParam(required = false) Integer alarmSource,
                                    @RequestParam(required = false) Integer handleStatus,
                                    @RequestParam(required = false) String keyword) {
        Page<Alarm> page = alarmMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Alarm>()
                        .eq(alarmLevel != null, Alarm::getAlarmLevel, alarmLevel)
                        .eq(alarmSource != null, Alarm::getAlarmSource, alarmSource)
                        .eq(handleStatus != null, Alarm::getHandleStatus, handleStatus)
                        .like(keyword != null && !keyword.isBlank(), Alarm::getAlarmContent, keyword)
                        .orderByDesc(Alarm::getAlarmTime));
        return Result.ok(page);
    }

    /** 开始处置（未处置→处置中，T-17 / RQ-25） */
    @PutMapping("/{id}/start-handle")
    public Result<Void> startHandle(@PathVariable Long id,
                                    @RequestBody(required = false) Alarm req,
                                    @RequestAttribute("username") String username) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BusinessException(404, "告警不存在");
        }
        if (alarm.getHandleStatus() != null && alarm.getHandleStatus() == 2) {
            throw new BusinessException(400, "该告警已完成处置");
        }
        alarm.setHandleStatus(1);
        alarm.setHandlePerson(req != null && req.getHandlePerson() != null
                ? req.getHandlePerson() : username);
        alarm.setHandleTime(LocalDateTime.now());
        alarmMapper.updateById(alarm);
        log(null, username, "告警处置", "开始处置", "告警[" + alarm.getAlarmNo() + "]开始处置");
        return Result.ok();
    }

    /** 告警处置（未处置→已处置） */
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id,
                               @RequestBody Alarm req,
                               @RequestAttribute("username") String username) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BusinessException(404, "告警不存在");
        }
        alarm.setHandleStatus(2);
        alarm.setHandlePerson(req.getHandlePerson() == null ? username : req.getHandlePerson());
        alarm.setHandleMeasure(req.getHandleMeasure());
        alarm.setHandleConclusion(req.getHandleConclusion());
        alarm.setHandleTime(LocalDateTime.now());
        alarmMapper.updateById(alarm);
        log(null, username, "告警处置", "完成处置", "告警[" + alarm.getAlarmNo() + "]完成处置");
        return Result.ok();
    }

    /** 告警统计：级别分布/状态分布/来源分布/近N天趋势/设备类型分布/告警类型占比/处置及时率 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Integer days) {
        Map<String, Object> result = new HashMap<>();
        int statDays = (days == null || days <= 0) ? 7 : Math.min(days, 90);
        result.put("byLevel", alarmMapper.countByLevel());
        result.put("byStatus", alarmMapper.countByHandleStatus());
        result.put("bySource", alarmMapper.countBySource());
        result.put("trend", alarmMapper.countByDay(LocalDateTime.now().minusDays(statDays).toString()));
        result.put("byDeviceType", alarmMapper.countByDeviceType());
        result.put("byType", alarmMapper.countByType());
        // 处置及时率（24h 内处置为及时）
        Map<String, Object> timeliness = alarmMapper.handleTimeliness();
        long totalHandled = ((Number) timeliness.getOrDefault("total_handled", 0)).longValue();
        long timelyCnt = ((Number) timeliness.getOrDefault("timely_cnt", 0)).longValue();
        result.put("timeliness", Map.of(
                "totalHandled", totalHandled,
                "timelyCnt", timelyCnt,
                "timelyRate", totalHandled == 0 ? 0
                        : Math.round(timelyCnt * 10000.0 / totalHandled) / 100.0,
                "avgMinutes", timeliness.getOrDefault("avg_minutes", 0)));
        result.put("statDays", statDays);
        return Result.ok(result);
    }

    /**
     * 关联告警查看（T-20 / RQ-27）：同一次事件（batchNo）触发的多条告警
     */
    @GetMapping("/{id}/related")
    public Result<List<Alarm>> related(@PathVariable Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BusinessException(404, "告警不存在");
        }
        if (alarm.getBatchNo() == null || alarm.getBatchNo().isBlank()) {
            return Result.ok(List.of(alarm));
        }
        return Result.ok(alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getBatchNo, alarm.getBatchNo())
                        .orderByAsc(Alarm::getAlarmTime)));
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
