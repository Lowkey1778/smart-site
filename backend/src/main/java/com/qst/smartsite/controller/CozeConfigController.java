package com.qst.smartsite.controller;

import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.service.CozeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coze 智能体配置接口（T-31 / RQ-38）
 * 管理端在界面填写 Coze API Token / Bot ID 接入真实智能体，无需改配置文件。
 */
@RestController
@RequestMapping("/api/coze")
public class CozeConfigController {

    @Autowired
    private CozeService cozeService;

    /** 读取当前配置（token 脱敏展示） */
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        // 通过服务内部状态判断：已配置时返回脱敏 token
        boolean configured = cozeService.isCozeConfigured();
        data.put("configured", configured);
        data.put("botId", configured ? cozeService.currentBotId() : "");
        data.put("baseUrl", configured ? cozeService.currentBaseUrl() : "https://api.coze.cn");
        data.put("apiTokenMasked", configured ? cozeService.currentApiTokenMasked() : "");
        return Result.ok(data);
    }

    /** 保存配置（apiToken 为空表示清除接入，回退本地引擎） */
    @PutMapping("/config")
    public Result<Void> saveConfig(@RequestBody Map<String, String> body) {
        String token = body.getOrDefault("apiToken", "");
        String bot = body.getOrDefault("botId", "");
        String base = body.getOrDefault("baseUrl", "https://api.coze.cn");
        if (token != null && !token.isBlank() && (bot == null || bot.isBlank())) {
            throw new BusinessException(400, "填写 API Token 时必须同时填写 Bot ID");
        }
        cozeService.saveConfig(token == null ? "" : token.trim(),
                bot == null ? "" : bot.trim(), base);
        return Result.ok();
    }
}
