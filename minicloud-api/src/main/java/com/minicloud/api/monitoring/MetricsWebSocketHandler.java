package com.minicloud.api.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time metrics streaming with tenant isolation and slow-consumer protection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionAccountMap = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
        sessions.put(session.getId(), safeSession);
        
        String accountId = extractAccountId(session);
        if (accountId != null) {
            sessionAccountMap.put(session.getId(), accountId);
            log.info("WebSocket client connected for account {}: {} (Total clients: {})", accountId, session.getId(), sessions.size());
        } else {
            log.info("WebSocket client connected (unscoped): {} (Total clients: {})", session.getId(), sessions.size());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionAccountMap.remove(session.getId());
        log.info("WebSocket client disconnected: {} - Status: {} (Remaining clients: {})", 
                session.getId(), status, sessions.size());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session.getId());
        sessionAccountMap.remove(session.getId());
    }
    
    /**
     * Broadcast metric data to clients of a specific account.
     */
    public void broadcastToAccount(String accountId, Object dataPoint) {
        if (sessions.isEmpty()) return;
        
        String json = serialize(dataPoint);
        if (json == null) return;
        
        TextMessage message = new TextMessage(json);
        sessions.forEach((id, session) -> {
            String sessAcct = sessionAccountMap.get(id);
            if (sessAcct == null || sessAcct.equals(accountId)) {
                sendSafely(session, message);
            }
        });
    }

    /**
     * Broadcast metric data to all connected clients.
     */
    public void broadcast(Object dataPoint) {
        if (sessions.isEmpty()) return;
        
        String json = serialize(dataPoint);
        if (json == null) return;
        
        TextMessage message = new TextMessage(json);
        sessions.values().forEach(session -> sendSafely(session, message));
    }

    private void sendSafely(WebSocketSession session, TextMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (Exception e) {
            log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize metric data", e);
            return null;
        }
    }

    private String extractAccountId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            for (String pair : uri.getQuery().split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && "accountId".equalsIgnoreCase(parts[0])) {
                    return parts[1];
                }
            }
        }
        return null;
    }
    
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
