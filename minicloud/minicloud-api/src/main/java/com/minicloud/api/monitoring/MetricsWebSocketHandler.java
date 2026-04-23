package com.minicloud.api.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time metrics streaming.
 * Manages WebSocket connections and broadcasts metric data to all connected clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: {} (Total clients: {})", session.getId(), sessions.size());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: {} - Status: {} (Remaining clients: {})", 
                session.getId(), status, sessions.size());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }
    
    /**
     * Broadcast metric data to all connected clients.
     * This method is called by the MonitoringService when new metrics are collected.
     * 
     * @param dataPoint the metric data to broadcast
     */
    public void broadcast(Object dataPoint) {
        if (sessions.isEmpty()) {
            return; // No clients connected, skip serialization
        }
        
        String json;
        try {
            json = objectMapper.writeValueAsString(dataPoint);
        } catch (Exception e) {
            log.error("Failed to serialize metric data", e);
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
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        });
    }
    
    /**
     * Get the number of currently connected WebSocket clients.
     * 
     * @return the number of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
