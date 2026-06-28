package com.minicloud.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.domain.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Task WebSocket client connected: {} (Total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Task WebSocket client disconnected: {} (Total: {})", session.getId(), sessions.size());
    }

    @EventListener
    public void handleTaskUpdateEvent(Task task) {
        broadcast(task);
    }

    public void broadcast(Task task) {
        if (sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(task);
        } catch (Exception e) {
            log.error("Failed to serialize task info", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send task update to session {}: {}", session.getId(), e.getMessage());
            }
        });
    }
}
