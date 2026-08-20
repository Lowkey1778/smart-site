package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.OperationLog;
import com.qst.smartsite.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志查询接口（T-36 / 3.4节）
 * 登录、权限变更、告警处置、喷淋控制等关键操作自动留痕
 */
@RestController
@RequestMapping("/api/log")
public class OperationLogController {

    @Autowired
    private OperationLogMapper operationLogMapper;

    /** 操作日志分页查询（模块/关键字筛选） */
    @GetMapping("/list")
    public Result<Page<OperationLog>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(operationLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OperationLog>()
                        .eq(module != null && !module.isBlank(), OperationLog::getModule, module)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(OperationLog::getUsername, keyword)
                                .or().like(OperationLog::getContent, keyword))
                        .orderByDesc(OperationLog::getId)));
    }
}
