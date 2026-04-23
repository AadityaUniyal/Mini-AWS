package com.minicloud.api.compute;

import com.minicloud.api.compute.ComputeService;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.InstanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/compute/instances")
@RequiredArgsConstructor
@Tag(name = "EC2 Compute", description = "Virtual servers management")
public class ComputeController {

    private final ComputeService computeService;

    @GetMapping
    @Operation(summary = "List all active instances")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getActiveInstances()));
    }

    @PostMapping("/launch")
    @Operation(summary = "Launch a new virtual instance")
    public ResponseEntity<ApiResponse<InstanceResponse>> launch(
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam UUID userId,
            @RequestParam String accountId,
            @RequestParam(required = false) UUID subnetId,
            @RequestParam(required = false) UUID securityGroupId,
            @RequestParam(required = false) String command) {
        InstanceResponse instance = computeService.launchInstance(userId, accountId, name, type, subnetId, securityGroupId, command);
        return ResponseEntity.ok(ApiResponse.ok("Instance launching", instance));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "List all instances for an account")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> list(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstancesForAccount(accountId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InstanceResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstance(id)));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<InstanceResponse>> stop(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Stopped", computeService.stopInstance(id)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<InstanceResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Started", computeService.startInstance(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> terminate(@PathVariable UUID id) {
        computeService.terminateInstance(id);
        return ResponseEntity.ok(ApiResponse.ok("Terminated", id.toString()));
    }
}
