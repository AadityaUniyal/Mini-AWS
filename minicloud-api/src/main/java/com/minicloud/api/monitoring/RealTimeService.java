package com.minicloud.api.monitoring;

import com.minicloud.api.dto.MetricsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeService {

    private final MetricsWebSocketHandler metricsWebSocketHandler;
    private final MetricsService metricsService;

    @Scheduled(fixedRate = 5000)
    public void broadcastMetrics() {
        try {
            MetricsResponse metrics = metricsService.getSystemMetrics();
            metricsWebSocketHandler.broadcast(metrics);
        } catch (Exception e) {
            log.debug("Failed to broadcast metrics: {}", e.getMessage());
        }
    }
}
