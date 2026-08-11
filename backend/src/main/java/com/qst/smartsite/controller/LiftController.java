package com.qst.smartsite.controller;

import com.qst.smartsite.common.Result;
import com.qst.smartsite.dto.LiftStatusVO;
import com.qst.smartsite.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.entity.LiftRecord;
import com.qst.smartsite.mapper.LiftRecordMapper;

/**
 * 升降机监控接口
 * 对应《页面功能清单》五、升降机监控
 */
@RestController
@RequestMapping("/api/lift")
public class LiftController {

    @Autowired
    private MonitorService monitorService;

    /** 升降机列表（实时状态） */
    @GetMapping("/list")
    public Result<List<LiftStatusVO>> list() {
        return Result.ok(monitorService.listLiftStatus());
    }

    /** 升降机实时监控详情 */
    @GetMapping("/{id}")
    public Result<LiftStatusVO> detail(@PathVariable Long id) {
        return monitorService.listLiftStatus().stream()
                .filter(l -> l.getDeviceId().equals(id))
                .findFirst()
                .map(Result::ok)
                .orElse(Result.fail(404, "升降机不存在"));
    }

    @Autowired
    private LiftRecordMapper liftRecordMapper;

    /** 升降机作业记录分页查询（T-15 / RQ-20） */
    @GetMapping("/records")
    public Result<Page<LiftRecord>> records(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Long deviceId) {
        return Result.ok(liftRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LiftRecord>()
                        .eq(deviceId != null, LiftRecord::getDeviceId, deviceId)
                        .orderByDesc(LiftRecord::getId)));
    }
}