package com.minicloud.api.config;

import com.minicloud.api.monitoring.MetricsWebSocketHandler;
import com.minicloud.api.websocket.TaskWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricsWebSocketHandler metricsWebSocketHandler;
    private final TaskWebSocketHandler taskWebSocketHandler;

    @Value("${minicloud.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }

        registry.addHandler(metricsWebSocketHandler, "/ws-events/metrics")
                .setAllowedOriginPatterns(origins);
        registry.addHandler(taskWebSocketHandler, "/ws-events/tasks")
                .setAllowedOriginPatterns(origins);
    }
}
