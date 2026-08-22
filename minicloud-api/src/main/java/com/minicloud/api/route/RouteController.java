package com.minicloud.api.route;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.domain.Route;
import com.minicloud.api.domain.RouteRepository;
import com.minicloud.api.domain.User;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RouteController — REST API for MiniRoute reverse proxy rules.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MiniRoute", description = "Reverse proxy and load balancing (AWS ALB equivalent)")
public class RouteController {

    private final RouteRepository routeRepository;
    private final ProxyService    proxyService;
    private final UserRepository  userRepository;
    private final com.minicloud.api.audit.AuditService auditService;

    public record CreateRouteRequest(
            String name,
            String hostPattern,
            String targetHost,
            int targetPort,
            String stripPrefix
    ) {}

    public record RouteResponse(
            String id,
            String name,
            String hostPattern,
            String targetHost,
            int targetPort,
            String stripPrefix,
            boolean enabled,
            String createdAt
    ) {}

    public record RouteStatusResponse(
            String id,
            String name,
            String hostPattern,
            String targetHost,
            int targetPort,
            boolean enabled,
            String healthStatus
    ) {}

    // ───────────────────── Route CRUD ────────────────────────────────

    @PostMapping("/routes")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Create a new routing rule")
    public ResponseEntity<ApiResponse<RouteResponse>> createRoute(
            @RequestBody CreateRouteRequest req,
            Authentication auth) {

        User user = getUser(auth);
        Route route = Route.builder()
                .userId(user.getId())
                .accountId(user.getAccountId())
                .name(req.name())
                .hostPattern(req.hostPattern())
                .targetHost(req.targetHost() != null ? req.targetHost() : "localhost")
                .targetPort(req.targetPort())
                .stripPrefix(req.stripPrefix())
                .enabled(true)
                .build();

        Route saved = routeRepository.save(route);
        log.info("Route '{}' created: {} → {}:{}", saved.getName(),
                saved.getHostPattern(), saved.getTargetHost(), saved.getTargetPort());
        auditService.recordSuccess(user.getUsername(), "Route", "CreateRoute", saved.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Route created", toResponse(saved)));
    }

    @GetMapping("/routes")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List all routes for the authenticated user/account")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> listRoutes(Authentication auth) {
        User user = getUser(auth);
        List<Route> routes = user.getAccountId() != null
                ? routeRepository.findByAccountId(user.getAccountId())
                : routeRepository.findByUserId(user.getId());
        List<RouteResponse> list = routes.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @DeleteMapping("/routes/{id}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Delete a routing rule by ID")
    public ResponseEntity<ApiResponse<String>> deleteRoute(
            @PathVariable String id, Authentication auth) {

        Route route = routeRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        SecurityUtils.validateAccountOwnership(route.getAccountId());
        routeRepository.delete(route);
        auditService.recordSuccess(getUser(auth).getUsername(), "Route", "DeleteRoute", route.getName());
        return ResponseEntity.ok(ApiResponse.ok("Route deleted", id));
    }

    @PutMapping("/routes/{id}/enable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Enable a routing rule")
    public ResponseEntity<ApiResponse<RouteResponse>> enableRoute(@PathVariable String id) {
        Route route = routeRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        SecurityUtils.validateAccountOwnership(route.getAccountId());
        route.setEnabled(true);
        return ResponseEntity.ok(ApiResponse.ok("Route enabled", toResponse(routeRepository.save(route))));
    }

    @PutMapping("/routes/{id}/disable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Disable a routing rule (keeps the config, pauses forwarding)")
    public ResponseEntity<ApiResponse<RouteResponse>> disableRoute(@PathVariable String id) {
        Route route = routeRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        SecurityUtils.validateAccountOwnership(route.getAccountId());
        route.setEnabled(false);
        return ResponseEntity.ok(ApiResponse.ok("Route disabled", toResponse(routeRepository.save(route))));
    }

    @GetMapping("/routes/status")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get all routes with their live health check status")
    public ResponseEntity<ApiResponse<List<RouteStatusResponse>>> getRoutesStatus(Authentication auth) {
        User user = getUser(auth);
        List<Route> routes = user.getAccountId() != null
                ? routeRepository.findByAccountId(user.getAccountId())
                : routeRepository.findByUserId(user.getId());

        List<RouteStatusResponse> list = routes.stream().map(route -> {
            boolean healthy = proxyService.isTargetHealthy(route.getTargetHost(), route.getTargetPort());
            return new RouteStatusResponse(
                    route.getId().toString(),
                    route.getName(),
                    route.getHostPattern(),
                    route.getTargetHost(),
                    route.getTargetPort(),
                    route.isEnabled(),
                    healthy ? "HEALTHY" : "UNHEALTHY"
            );
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // ───────────────────── Live Proxy Forwarding ──────────────────────

    @RequestMapping(
            value = {"/proxy", "/proxy/{*path}"},
            method = {
                    RequestMethod.GET,     RequestMethod.POST,
                    RequestMethod.PUT,     RequestMethod.DELETE,
                    RequestMethod.PATCH,   RequestMethod.HEAD,
                    RequestMethod.OPTIONS
            }
    )
    @Operation(summary = "Forward a live request through a matched routing rule (public proxy entrance)")
    public ResponseEntity<byte[]> proxyRequest(
            HttpServletRequest request,
            @RequestHeader HttpHeaders headers) throws IOException {

        String hostHeader = request.getHeader("Host");
        String uri        = request.getRequestURI();
        byte[] body       = StreamUtils.copyToByteArray(request.getInputStream());

        Optional<Route> matchingRoute = proxyService.findMatchingRoute(hostHeader, uri);
        if (matchingRoute.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No matching route found".getBytes());
        }

        Map<String, List<String>> headerMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headerMap.put(name, Collections.list(request.getHeaders(name)));
        }

        ProxyService.ProxyResponse resp = proxyService.forward(
                matchingRoute.get(),
                uri,
                request.getMethod(),
                headerMap,
                body
        );

        HttpHeaders responseHeaders = new HttpHeaders();
        resp.headers().forEach(responseHeaders::put);

        return new ResponseEntity<>(resp.body(), responseHeaders, HttpStatus.valueOf(resp.statusCode()));
    }

    // ───────────────────── Helper ────────────────────────────────────

    private User getUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("No authentication context found");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));
    }

    private RouteResponse toResponse(Route r) {
        return new RouteResponse(
                r.getId().toString(),
                r.getName(),
                r.getHostPattern(),
                r.getTargetHost(),
                r.getTargetPort(),
                r.getStripPrefix(),
                r.isEnabled(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null
        );
    }
}
