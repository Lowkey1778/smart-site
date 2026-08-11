package com.qst.smartsite.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 实时数据推送处理器
 * 模拟器生成数据后调用 broadcast() 推送给所有在线前端
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    /** 在线会话集合（线程安全） */
    private static final Set<WebSocketSession> SESSIONS = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSIONS.add(session);
        System.out.println("[WS] 新连接: " + session.getId() + ", 当前在线: " + SESSIONS.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session);
        System.out.println("[WS] 断开: " + session.getId() + ", 当前在线: " + SESSIONS.size());
    }

    /**
     * 广播 JSON 消息给所有在线客户端
     */
    public static void broadcast(String json) {
        for (WebSocketSession session : SESSIONS) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception e) {
                // 单个会话失败不影响其他会话
                e.printStackTrace();
            }
        }
    }
}
