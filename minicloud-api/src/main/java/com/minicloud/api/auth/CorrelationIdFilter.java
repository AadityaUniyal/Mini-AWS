package com.minicloud.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to manage requests' Correlation ID (traceId) for distributed tracing.
 * Assigns a unique ID to every request thread context using MDC.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Run before security and other filters
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
            log.trace("Generated new Correlation ID: {}", correlationId);
        } else {
            log.trace("Reusing incoming Correlation ID: {}", correlationId);
        }

        // Put correlation ID in MDC to be visible in all log outputs
        MDC.put(MDC_KEY, correlationId);

        // Put correlation ID in HTTP response headers so callers can trace it
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
