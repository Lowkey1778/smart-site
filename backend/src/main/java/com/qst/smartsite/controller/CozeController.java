package com.qst.smartsite.controller;

import com.qst.smartsite.common.Result;
import com.qst.smartsite.service.CozeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coze 智能体接口（T-31 / RQ-38）
 * POST /api/coze/chat  智能问答（安全态势/告警分析/设备诊断/安全建议）
 */
@RestController
@RequestMapping("/api/coze")
public class CozeController {

    @Autowired
    private CozeService cozeService;

    /** 智能问答 */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestAttribute("userId") Long userId,
                                            @RequestAttribute(value = "username", required = false) String username,
                                            @RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        if (message.isBlank()) {
            message = "你好";
        }
        Map<String, Object> data = cozeService.chat(message, userId, username);
        return Result.ok(data);
    }

    /** 快捷问题列表 */
    @GetMapping("/quick-questions")
    public Result<List<String>> quickQuestions() {
        return Result.ok(List.of(CozeService.QUICK_QUESTIONS));
    }

    /** 智能体状态（是否已接入真实 Coze） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quickQuestions", List.of(CozeService.QUICK_QUESTIONS));
        return Result.ok(data);
    }
}
