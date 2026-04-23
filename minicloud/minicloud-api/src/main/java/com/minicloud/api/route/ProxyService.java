package com.minicloud.api.route;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ProxyService — MiniRoute core forwarding engine.
 *
 * Responsibilities:
 *  1. forward(route, path, method, headers, body) → forwards HTTP request to backend
 *  2. Health-check scheduler — pings each enabled route every 30 s and marks healthy/unhealthy
 *
 * The actual HTTP forwarding in MiniRoute works by acting as a pass-through proxy:
 *   Client → Spring (RouteController) → ProxyService → Backend process on targetPort
 * This is intentionally lightweight (Java HttpClient) rather than Netty,
 * so we stay within the existing modular monolith architecture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final RouteRepository routeRepository;
    private final InstanceRepository instanceRepository;
    private final RdsRepository rdsRepository;
    private final SecurityGroupRepository securityGroupRepository;
    private final NetworkingAdvisor networkingAdvisor;

    private static final int HEALTH_TIMEOUT_MS  = 3_000;
    private static final int FORWARD_TIMEOUT_MS = 30_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(FORWARD_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ─────────────────────────── Forwarding ───────────────────────────

    /**
     * Constructs and sends a forwarded HTTP request to the backend.
     * Returns a ProxyResponse wrapping status code, headers, and body bytes.
     */
    public ProxyResponse forward(Route route, String path, String method,
                                  java.util.Map<String, List<String>> incomingHeaders,
                                  byte[] body) {

        // ── Security Group Enforcement ──
        if (!isAllowedBySecurityGroup(route)) {
            log.warn("Proxy access BLOCKED by Security Group for route '{}' to {}:{}", 
                    route.getName(), route.getTargetHost(), route.getTargetPort());
            return ProxyResponse.error(403, "Forbidden — access blocked by Security Group rules.");
        }

        String targetUrl = "http://" + route.getTargetHost() + ":" + route.getTargetPort();

        // Strip prefix if configured
        String forwardPath = path;
        if (route.getStripPrefix() != null && !route.getStripPrefix().isBlank()
                && forwardPath.startsWith(route.getStripPrefix())) {
            forwardPath = forwardPath.substring(route.getStripPrefix().length());
        }

        String fullUrl = targetUrl + (forwardPath.startsWith("/") ? forwardPath : "/" + forwardPath);
        log.debug("Proxying {} {} → {}", method, path, fullUrl);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofMillis(FORWARD_TIMEOUT_MS));

            // Forward safe headers (skip hop-by-hop headers)
            incomingHeaders.forEach((header, values) -> {
                String lower = header.toLowerCase();
                if (!lower.equals("host") && !lower.equals("content-length")
                        && !lower.equals("transfer-encoding") && !lower.equals("connection")) {
                    try {
                        reqBuilder.header(header, String.join(", ", values));
                    } catch (Exception ignored) {} // some headers are restricted
                }
            });

            reqBuilder.header("X-Forwarded-By", "MiniRoute/1.0");
            reqBuilder.header("X-Forwarded-Host", route.getHostPattern());

            HttpRequest.BodyPublisher publisher = (body != null && body.length > 0)
                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                    : HttpRequest.BodyPublishers.noBody();

            reqBuilder.method(method, publisher);

            HttpResponse<byte[]> resp = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            // Update request count
            route.setRequestCount(route.getRequestCount() + 1);
            routeRepository.save(route);

            return new ProxyResponse(resp.statusCode(), resp.headers().map(), resp.body());

        } catch (Exception e) {
            log.error("Proxy forward failed for route '{}': {}", route.getName(), e.getMessage());
            return ProxyResponse.error(502, "Bad Gateway — backend unreachable: " + e.getMessage());
        }
    }

    // ─────────────────────────── Health Checks ───────────────────────────

    /**
     * Runs every 30 seconds. Pings each enabled route's target port.
     * Marks routes healthy/unhealthy and logs state changes.
     */
    @Scheduled(fixedDelay = 30_000)
    public void runHealthChecks() {
        List<Route> routes = routeRepository.findAllByEnabledTrue();
        for (Route route : routes) {
            boolean wasHealthy = route.isHealthy();
            boolean nowHealthy = ping(route.getTargetHost(), route.getTargetPort());
            route.setHealthy(nowHealthy);
            route.setLastHealthCheck(LocalDateTime.now());
            routeRepository.save(route);

            if (wasHealthy != nowHealthy) {
                log.warn("Route '{}' health changed: {} → {}",
                        route.getName(), wasHealthy ? "HEALTHY" : "UNHEALTHY", nowHealthy ? "HEALTHY" : "UNHEALTHY");
            }
        }
    }

    /** TCP ping to check if a port is open at host:port within the timeout. */
    private boolean ping(String host, int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), HEALTH_TIMEOUT_MS);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    // ─────────────────────────── Security Helpers ───────────────────────

    /**
     * Attempts to find the Security Group for the route's target and evaluates rules.
     */
    private boolean isAllowedBySecurityGroup(Route route) {
        com.minicloud.api.domain.SecurityGroup sg = null;

        // 1. Precise lookup via linked instance ID
        if (route.getEc2InstanceId() != null) {
            sg = instanceRepository.findById(route.getEc2InstanceId())
                    .flatMap(i -> i.getSecurityGroupId() != null ? securityGroupRepository.findById(i.getSecurityGroupId()) : Optional.empty())
                    .orElse(null);
        }

        // 2. Fallback: Search EC2 instances by port (for localhost targets)
        if (sg == null && "localhost".equals(route.getTargetHost())) {
            sg = instanceRepository.findAll().stream()
                    .filter(i -> i.getCommand() != null && i.getCommand().contains(String.valueOf(route.getTargetPort())))
                    .findFirst()
                    .flatMap(i -> i.getSecurityGroupId() != null ? securityGroupRepository.findById(i.getSecurityGroupId()) : Optional.empty())
                    .orElse(null);
        }

        // 3. Fallback: Search RDS instances by port
        if (sg == null) {
            sg = rdsRepository.findByPort(route.getTargetPort())
                    .flatMap(i -> i.getSecurityGroupId() != null ? securityGroupRepository.findById(i.getSecurityGroupId()) : Optional.empty())
                    .orElse(null);
        }

        // If no SG is found, we permit it (assume it's an external or UNMANAGED resource)
        // In a strict VPC, we might block this.
        if (sg == null) {
            log.debug("No managed Security Group found for target {}:{}. Permitting unmanaged traffic.",
                    route.getTargetHost(), route.getTargetPort());
            return true;
        }

        // Evaluate Ingress (all proxy traffic is TCP)
        return networkingAdvisor.isIngressAllowed(sg, route.getTargetPort(), 
                SecurityGroupRule.Protocol.TCP, "0.0.0.0/0");
    }

    // ─────────────────────────── Response DTO ───────────────────────────

    public record ProxyResponse(int statusCode,
                                 java.util.Map<String, List<String>> headers,
                                 byte[] body) {
        public static ProxyResponse error(int code, String message) {
            return new ProxyResponse(code, java.util.Map.of(), message.getBytes());
        }
    }
}
