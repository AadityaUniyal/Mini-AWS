package com.minicloud.api.route;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ProxyService — MiniRoute core forwarding engine.
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
    private final NetworkAclService networkAclService;

    private static final int HEALTH_TIMEOUT_MS  = 3_000;
    private static final int FORWARD_TIMEOUT_MS = 30_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(FORWARD_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final java.util.Set<String> BLOCKED_HOSTS = java.util.Set.of(
        "127.0.0.1", "localhost", "0.0.0.0", "169.254.169.254", "::1"
    );

    private boolean isBlockedTarget(String host) {
        if (BLOCKED_HOSTS.contains(host.toLowerCase())) return false; // Allow local internal proxy target
        try {
            InetAddress addr = InetAddress.getByName(host);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public Optional<Route> findMatchingRoute(String host, String uri) {
        List<Route> routes = routeRepository.findAllByEnabledTrue();
        for (Route r : routes) {
            if (r.getHostPattern() != null && !r.getHostPattern().isBlank()) {
                if (host != null && host.equalsIgnoreCase(r.getHostPattern())) {
                    return Optional.of(r);
                }
            }
            if (r.getDomainOrPath() != null && !r.getDomainOrPath().isBlank()) {
                if (uri != null && uri.startsWith(r.getDomainOrPath())) {
                    return Optional.of(r);
                }
            }
        }
        return routes.isEmpty() ? Optional.empty() : Optional.of(routes.get(0));
    }

    public ProxyResponse forward(Route route, String path, String method,
                                  java.util.Map<String, List<String>> incomingHeaders,
                                  byte[] body) {

        if (isBlockedTarget(route.getTargetHost())) {
            log.warn("Proxy access BLOCKED for target: {}", route.getTargetHost());
            return ProxyResponse.error(403, "Forbidden — blocked host");
        }

        // ── Network ACL (NACL) & Security Group Enforcement ──
        java.util.UUID subnetId = null;
        if (route.getEc2InstanceId() != null) {
            Instance inst = instanceRepository.findById(route.getEc2InstanceId()).orElse(null);
            if (inst != null) {
                subnetId = inst.getSubnetId();
            }
        }
        
        if (subnetId != null && !networkAclService.isTrafficAllowed(subnetId, route.getTargetPort(), "TCP", "0.0.0.0/0")) {
            log.warn("Proxy access BLOCKED by Network ACL for route '{}' to {}:{}", 
                    route.getName(), route.getTargetHost(), route.getTargetPort());
            return ProxyResponse.error(403, "Forbidden — access blocked by Network ACL rules.");
        }

        if (!isAllowedBySecurityGroup(route)) {
            log.warn("Proxy access BLOCKED by Security Group for route '{}' to {}:{}", 
                    route.getName(), route.getTargetHost(), route.getTargetPort());
            return ProxyResponse.error(403, "Forbidden — access blocked by Security Group rules.");
        }

        String targetUrl = "http://" + route.getTargetHost() + ":" + route.getTargetPort();

        String forwardPath = path != null ? path : "/";
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

            if (incomingHeaders != null) {
                incomingHeaders.forEach((header, values) -> {
                    String lower = header.toLowerCase();
                    if (!lower.equals("host") && !lower.equals("content-length")
                            && !lower.equals("transfer-encoding") && !lower.equals("connection")) {
                        try {
                            reqBuilder.header(header, String.join(", ", values));
                        } catch (Exception ignored) {}
                    }
                });
            }

            reqBuilder.header("X-Forwarded-By", "MiniRoute/1.0");
            reqBuilder.header("X-Forwarded-Host", route.getHostPattern() != null ? route.getHostPattern() : "localhost");

            HttpRequest.BodyPublisher publisher = (body != null && body.length > 0)
                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                    : HttpRequest.BodyPublishers.noBody();

            reqBuilder.method(method != null ? method : "GET", publisher);

            HttpResponse<byte[]> resp = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            route.setRequestCount(route.getRequestCount() + 1);
            routeRepository.save(route);

            return new ProxyResponse(resp.statusCode(), resp.headers().map(), resp.body());

        } catch (Exception e) {
            log.error("Proxy forward failed for route '{}': {}", route.getName(), e.getMessage());
            return ProxyResponse.error(502, "Bad Gateway — backend unreachable: " + e.getMessage());
        }
    }

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

    public boolean isTargetHealthy(String host, int port) {
        return ping(host, port);
    }

    private boolean ping(String host, int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), HEALTH_TIMEOUT_MS);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private boolean isAllowedBySecurityGroup(Route route) {
        com.minicloud.api.domain.SecurityGroup sg = null;

        if (route.getEc2InstanceId() != null) {
            sg = instanceRepository.findById(route.getEc2InstanceId())
                    .flatMap(i -> i.getSecurityGroupId() != null ? securityGroupRepository.findById(i.getSecurityGroupId()) : Optional.empty())
                    .orElse(null);
        }

        if (sg == null && "localhost".equals(route.getTargetHost())) {
            sg = instanceRepository.findAll().stream()
                    .filter(i -> i.getCommand() != null && i.getCommand().contains(String.valueOf(route.getTargetPort())))
                    .findFirst()
                    .flatMap(i -> i.getSecurityGroupId() != null ? securityGroupRepository.findById(i.getSecurityGroupId()) : Optional.empty())
                    .orElse(null);
        }

        if (sg == null) {
            sg = rdsRepository.findByPort(route.getTargetPort())
                    .flatMap(i -> i.getSecurityGroupId() != null ? securityGroupRepository.findById(i.getSecurityGroupId()) : Optional.empty())
                    .orElse(null);
        }

        if (sg == null) {
            return true;
        }

        return networkingAdvisor.isIngressAllowed(sg, route.getTargetPort(), 
                SecurityGroupRule.Protocol.TCP, "0.0.0.0/0");
    }

    public record ProxyResponse(int statusCode,
                                 java.util.Map<String, List<String>> headers,
                                 byte[] body) {
        public static ProxyResponse error(int code, String message) {
            return new ProxyResponse(code, java.util.Map.of(), message.getBytes());
        }
    }
}
