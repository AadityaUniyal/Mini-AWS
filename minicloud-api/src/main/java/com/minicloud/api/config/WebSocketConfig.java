package com.minicloud.api.config;

import com.minicloud.api.monitoring.MetricsWebSocketHandler;
import com.minicloud.api.websocket.TaskWebSocketHandler;
import lombok.RequiredArgsConstructor;
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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(metricsWebSocketHandler, "/ws-events/metrics")
                .setAllowedOrigins("*");
        registry.addHandler(taskWebSocketHandler, "/ws-events/tasks")
                .setAllowedOrigins("*");
    }
}
