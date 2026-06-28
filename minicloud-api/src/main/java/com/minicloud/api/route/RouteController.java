package com.minicloud.api.route;

import com.minicloud.api.exception.ResourceNotFoundException;
import com.minicloud.api.domain.Route;
import com.minicloud.api.domain.RouteRepository;
import com.minicloud.api.domain.User;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RouteController — REST API for MiniRoute reverse proxy rules.
 *
 *  POST   /routes                → create a route
 *  GET    /routes                → list routes for current user
 *  DELETE /routes/{id}           → remove a route
 *  PUT    /routes/{id}/enable    → enable a route
 *  PUT    /routes/{id}/disable   → disable a route
 *  GET    /routes/status         → list all routes with health status
 *  ANY    /proxy/{*path}         → forward a live request through a named route
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
    @Operation(summary = "List all routes for the authenticated user")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> listRoutes(Authentication auth) {
        User user = getUser(auth);
        List<RouteResponse> list = routeRepository.findByUserId(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @DeleteMapping("/routes/{id}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Delete a routing rule by ID")
    public ResponseEntity<ApiResponse<String>> deleteRoute(
            @PathVariable String id, Authentication auth) {

        Route route = routeRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        routeRepository.delete(route);
        auditService.recordSuccess(getUser(auth).getUsername(), "Route", "DeleteRoute", route.getName());
        return ResponseEntity.ok(ApiResponse.ok("Route deleted", id));
    }

    @PutMapping("/routes/{id}/enable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Enable a routing rule")
    public ResponseEntity<ApiResponse<RouteResponse>> enableRoute(@PathVariable String id) {
        Route route = routeRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        route.setEnabled(true);
        return ResponseEntity.ok(ApiResponse.ok("Route enabled", toResponse(routeRepository.save(route))));
    }

    @PutMapping("/routes/{id}/disable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Disable a routing rule (keeps the config, pauses forwarding)")
    public ResponseEntity<ApiResponse<RouteResponse>> disableRoute(@PathVariable String id) {
        Route route = routeRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        route.setEnabled(false);
        return ResponseEntity.ok(ApiResponse.ok("Route disabled", toResponse(routeRepository.save(route))));
    }

    @GetMapping("/routes/status")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get all routes with live health status")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> routeStatus(Authentication auth) {
        User user = getUser(auth);
        List<RouteResponse> list = routeRepository.findByUserId(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // ───────────────────── Live Proxy Forwarding ─────────────────────

    /**
     * ANY /proxy/{routeName}/{*path}
     * Forwards the request through the named route to its backend target.
     * Requires authentication so only the owner can invoke their routes.
     */
    @RequestMapping(value = "/proxy/{routeName}/{*path}", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD
    })
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Forward a request through a named route to its backend")
    public ResponseEntity<byte[]> proxy(
            @PathVariable String routeName,
            @PathVariable(required = false) String path,
            HttpServletRequest request,
            Authentication auth) throws IOException {

        User user = getUser(auth);

        Route route = routeRepository.findByUserIdAndName(user.getId(), routeName)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + routeName));

        if (!route.isEnabled()) {
            return ResponseEntity.status(503)
                    .body(("Route '" + routeName + "' is currently disabled.").getBytes());
        }

        String forwardPath = "/" + (path == null ? "" : path);
        String query = request.getQueryString();
        if (query != null) forwardPath += "?" + query;

        // Collect headers
        Map<String, List<String>> headers = new java.util.HashMap<>();
        java.util.Collections.list(request.getHeaderNames()).forEach(h ->
                headers.put(h, java.util.Collections.list(request.getHeaders(h))));

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        ProxyService.ProxyResponse resp = proxyService.forward(route, forwardPath,
                request.getMethod(), headers, body);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(resp.statusCode());
        resp.headers().forEach((k, v) -> {
            String lower = k.toLowerCase();
            if (!lower.equals("transfer-encoding") && !lower.equals("connection")) {
                builder.header(k, v.toArray(new String[0]));
            }
        });
        return builder.body(resp.body());
    }

    // ───────────────────── Helpers ────────────────────────────────────

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private RouteResponse toResponse(Route r) {
        return new RouteResponse(
                r.getId().toString(), r.getName(), r.getHostPattern(),
                r.getTargetHost(), r.getTargetPort(), r.getStripPrefix(),
                r.isEnabled(), r.isHealthy(),
                r.getLastHealthCheck() != null ? r.getLastHealthCheck().toString() : null,
                r.getRequestCount(),
                "http://localhost:8080/proxy/" + r.getName() + "/",
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null
        );
    }

    // ───────────────────── Records ────────────────────────────────────

    public record CreateRouteRequest(
            String name, String hostPattern,
            String targetHost, int targetPort, String stripPrefix
    ) {}

    public record RouteResponse(
            String id, String name, String hostPattern,
            String targetHost, int targetPort, String stripPrefix,
            boolean enabled, boolean healthy,
            String lastHealthCheck, long requestCount,
            String proxyUrl, String createdAt
    ) {}
}
