package com.qst.smartsite.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 /ws 端点，用于实时数据推送
 * 前端连接：new WebSocket('ws://localhost:8080/ws')，服务端广播纯 JSON 文本
 * 处理器 RealtimeWebSocketHandler 由 @Component 注册，此处直接注入
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private RealtimeWebSocketHandler realtimeWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeWebSocketHandler, "/ws")
                .setAllowedOrigins("*");
    }
}
