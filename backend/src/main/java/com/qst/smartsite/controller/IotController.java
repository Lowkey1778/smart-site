package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.IotData;
import com.qst.smartsite.mapper.IotDataMapper;
import com.qst.smartsite.service.IotDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备通信模拟平台接口（T-32 / 接口章节 4.2）
 * 上报记录查询 / TCP 连接状态 / 平台概览
 */
@RestController
@RequestMapping("/api/iot")
public class IotController {

    @Autowired
    private IotDataMapper iotDataMapper;

    @Autowired
    private IotDataService iotDataService;

    /** 上报记录分页查询 */
    @GetMapping("/records")
    public Result<Page<IotData>> records(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) String deviceTag,
                                         @RequestParam(required = false) String subType) {
        Page<IotData> page = iotDataMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<IotData>()
                        .eq(deviceTag != null && !deviceTag.isBlank(), IotData::getDeviceTag, deviceTag)
                        .eq(subType != null && !subType.isBlank(), IotData::getDataSubType, subType)
                        .orderByDesc(IotData::getId));
        return Result.ok(page);
    }

    /** TCP 连接状态（在线设备 + 上报统计） */
    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        return Result.ok(iotDataService.overview());
    }

    /** 平台概览（累计报文/连接数/最近上报时间等） */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = iotDataService.overview();
        IotData latest = iotDataMapper.selectOne(
                new LambdaQueryWrapper<IotData>().orderByDesc(IotData::getId).last("LIMIT 1"));
        data.put("latestReportTime", latest == null ? null : latest.getReportTime());
        return Result.ok(data);
    }

    /* ==================== 手动推送（演示人调节推送数据） ==================== */

    /** 单次推送：立即按给定报文模拟一次设备上报 */
    @PostMapping("/sim/push")
    public Result<Void> simPush(@RequestBody Map<String, Object> body) {
        String deviceCode = body.get("deviceCode") == null ? null : body.get("deviceCode").toString();
        String type = body.get("type") == null ? null : body.get("type").toString();
        Object data = body.get("data");
        iotDataService.simPushOnce(deviceCode, type, data);
        return Result.ok();
    }

    /** 周期推送：按给定报文每 intervalSec 秒推送一次 */
    @PostMapping("/sim/start")
    public Result<Void> simStart(@RequestBody Map<String, Object> body) {
        String deviceCode = body.get("deviceCode") == null ? null : body.get("deviceCode").toString();
        String type = body.get("type") == null ? null : body.get("type").toString();
        Object data = body.get("data");
        long interval = body.get("intervalSec") == null ? 5 : Long.parseLong(body.get("intervalSec").toString());
        iotDataService.simStartPeriodic(deviceCode, type, data, interval);
        return Result.ok();
    }

    /** 停止周期推送 */
    @PostMapping("/sim/stop")
    public Result<Void> simStop(@RequestBody Map<String, Object> body) {
        String deviceCode = body.get("deviceCode") == null ? null : body.get("deviceCode").toString();
        if (deviceCode != null && !deviceCode.isBlank()) {
            iotDataService.stopPeriodic(deviceCode);
        }
        return Result.ok();
    }

    /** 周期推送状态 */
    @GetMapping("/sim/status")
    public Result<Map<String, Object>> simStatus() {
        return Result.ok(iotDataService.simStatus());
    }
}
