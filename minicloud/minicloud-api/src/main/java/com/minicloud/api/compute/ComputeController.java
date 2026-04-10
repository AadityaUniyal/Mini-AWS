package com.minicloud.api.compute;

import com.minicloud.api.iam.*;
import com.minicloud.core.dto.ApiResponse;
import com.minicloud.core.dto.InstanceRequest;
import com.minicloud.core.dto.InstanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ec2")
@RequiredArgsConstructor
@Tag(name = "MiniEC2", description = "Compute instances — real OS processes (AWS EC2 equivalent)")
@SecurityRequirement(name = "BearerAuth")
public class ComputeController {

    private final ComputeService computeService;
    private final UserRepository userRepository;
    private final PolicyEvaluator policyEvaluator;

    @PostMapping("/instances")
    @Operation(summary = "Launch a new compute instance")
    public ResponseEntity<ApiResponse<InstanceResponse>> launchInstance(
            @RequestBody InstanceRequest request,
            Authentication auth) throws IOException {
        User user = getUser(auth);
        checkPermission(user, "ec2:RunInstances", "*");
        InstanceResponse response = computeService.launchInstance(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Instance launched", response));
    }

    @GetMapping("/instances")
    @Operation(summary = "List all instances for current user")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> listInstances(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "ec2:DescribeInstances", "*");
        return ResponseEntity.ok(ApiResponse.ok(computeService.listInstances(auth.getName())));
    }

    @GetMapping("/instances/{id}")
    @Operation(summary = "Get instance detail")
    public ResponseEntity<ApiResponse<InstanceResponse>> getInstance(
            @PathVariable UUID id,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "ec2:DescribeInstances", "mc:ec2:" + id);
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstance(id, auth.getName())));
    }

    @PostMapping("/instances/{id}/stop")
    @Operation(summary = "Stop a running instance")
    public ResponseEntity<ApiResponse<InstanceResponse>> stopInstance(
            @PathVariable UUID id,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "ec2:StopInstances", "mc:ec2:" + id);
        return ResponseEntity.ok(ApiResponse.ok("Instance stopped", computeService.stopInstance(id, auth.getName())));
    }

    public ResponseEntity<ApiResponse<InstanceResponse>> startInstance(
            @PathVariable UUID id,
            Authentication auth) throws IOException {
        User user = getUser(auth);
        checkPermission(user, "ec2:StartInstances", "mc:ec2:" + id);
        return ResponseEntity.ok(ApiResponse.ok("Instance started", computeService.startInstance(id, auth.getName())));
    }

    @DeleteMapping("/instances/{id}")
    @Operation(summary = "Terminate an instance")
    public ResponseEntity<ApiResponse<InstanceResponse>> terminateInstance(
            @PathVariable UUID id,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "ec2:TerminateInstances", "mc:ec2:" + id);
        return ResponseEntity.ok(ApiResponse.ok("Instance terminated", computeService.terminateInstance(id, auth.getName())));
    }

    @GetMapping("/instances/{id}/console")
    @Operation(summary = "Get instance console output (last 500 lines)")
    public ResponseEntity<ApiResponse<List<String>>> getConsoleOutput(
            @PathVariable UUID id,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "ec2:GetConsoleOutput", "mc:ec2:" + id);
        return ResponseEntity.ok(ApiResponse.ok(computeService.getConsoleOutput(id, auth.getName())));
    }

    private void checkPermission(User user, String action, String resource) {
        if (!policyEvaluator.isAuthorized(user.getPolicies(), action, resource)) {
            log.warn("Access denied: user {} action {} resource {}", user.getUsername(), action, resource);
            throw new AccessDeniedException("Unauthorized: " + action + " on " + resource);
        }
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
