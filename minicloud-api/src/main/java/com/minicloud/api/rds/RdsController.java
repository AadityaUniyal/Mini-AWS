package com.minicloud.api.rds;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.RdsResponse;
import com.minicloud.api.domain.RdsInstance;
import com.minicloud.api.domain.RdsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rds/instances")
@RequiredArgsConstructor
@Tag(name = "MiniRDS", description = "Relational database as a service management")
public class RdsController {

    private final RdsService rdsService;
    private final RdsRepository rdsRepository;

    @GetMapping
    @Operation(summary = "List all RDS instances (admin view)")
    public ResponseEntity<ApiResponse<List<RdsResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(rdsService.listAll()));
    }

    @PostMapping
    @Operation(summary = "Create a new RDS instance (JSON body)")
    public ResponseEntity<ApiResponse<RdsResponse>> create(@RequestBody java.util.Map<String, Object> req) {
        String name = req.getOrDefault("name", "db-" + System.currentTimeMillis()).toString();
        String dbName = req.getOrDefault("dbName", name).toString();
        String masterUsername = req.getOrDefault("masterUsername", "admin").toString();
        String masterPassword = req.getOrDefault("masterPassword", "password").toString();
        UUID userId = req.containsKey("userId") ? UUID.fromString(req.get("userId").toString()) : null;
        RdsResponse response = rdsService.launchInstance(userId, name, dbName, masterUsername, masterPassword, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Database created", response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<RdsResponse>>> listInstances(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(rdsService.listInstances(userId)));
    }

    @PostMapping("/launch")
    @Operation(summary = "Launch a new RDS instance")
    public ResponseEntity<ApiResponse<RdsResponse>> launchInstance(
            @RequestParam UUID userId,
            @RequestParam String name,
            @RequestParam String dbName,
            @RequestParam String masterUsername,
            @RequestParam String masterPassword,
            @RequestParam(required = false) UUID securityGroupId) {
        
        RdsResponse response = rdsService.launchInstance(userId, name, dbName, masterUsername, masterPassword, securityGroupId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Database instance launch initiated", response));
    }

    @PostMapping("/{idOrName}/start")
    public ResponseEntity<ApiResponse<RdsResponse>> startInstance(@PathVariable String idOrName) {
        UUID id = resolveId(idOrName);
        return ResponseEntity.ok(ApiResponse.ok("Database starting", rdsService.startInstance(id)));
    }

    @PostMapping("/{idOrName}/stop")
    public ResponseEntity<ApiResponse<RdsResponse>> stopInstance(@PathVariable String idOrName) {
        UUID id = resolveId(idOrName);
        return ResponseEntity.ok(ApiResponse.ok("Database stopping", rdsService.stopInstance(id)));
    }

    @DeleteMapping("/{idOrName}")
    public ResponseEntity<ApiResponse<String>> terminateInstance(@PathVariable String idOrName) {
        UUID id = resolveId(idOrName);
        rdsService.terminateInstance(id);
        return ResponseEntity.ok(ApiResponse.ok("Database terminated", id.toString()));
    }

    private UUID resolveId(String idOrName) {
        try {
            return UUID.fromString(idOrName);
        } catch (IllegalArgumentException e) {
            RdsInstance instance = rdsRepository.findByName(idOrName)
                    .orElseThrow(() -> new RuntimeException("RDS Database not found: " + idOrName));
            return instance.getId();
        }
    }
}
