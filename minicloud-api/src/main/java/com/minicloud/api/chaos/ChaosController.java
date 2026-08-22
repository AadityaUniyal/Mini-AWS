package com.minicloud.api.chaos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * ChaosController — REST API for Chaos Monkey experiments and resilience testing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chaos")
@RequiredArgsConstructor
@Tag(name = "Chaos Engineering", description = "Chaos Monkey injection and automated self-healing resilience")
@SecurityRequirement(name = "BearerAuth")
public class ChaosController {

    private final ChaosService chaosService;
    private final ObjectMapper objectMapper;

    @PostMapping("/terminate-random-instance")
    @Operation(summary = "Terminate a random running instance in an ASG/fleet and trigger self-healing recovery")
    public ResponseEntity<ApiResponse<ChaosResultDTO>> terminateRandomInstance(
            @Parameter(description = "Optional Auto Scaling Group ID or name")
            @RequestParam(required = false) String autoScalingGroupId,
            @Parameter(description = "Optional Account ID to filter instances")
            @RequestParam(required = false) String accountId,
            @Parameter(description = "Optional Group name to filter instances")
            @RequestParam(required = false) String groupName,
            HttpServletRequest request) {

        String effectiveAsgId = autoScalingGroupId;
        String effectiveAccountId = accountId;
        String effectiveGroupName = groupName;

        try {
            UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
            if (principal.getAccountId() != null) {
                effectiveAccountId = principal.getAccountId();
            }
        } catch (Exception ignored) {}

        try {
            if (request != null) {
                String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                if (body != null && !body.isBlank() && (body.trim().startsWith("{") || body.trim().startsWith("["))) {
                    ChaosRequestDTO requestBody = objectMapper.readValue(body, ChaosRequestDTO.class);
                    if (requestBody != null) {
                        if (effectiveAsgId == null) {
                            effectiveAsgId = requestBody.getAutoScalingGroupId();
                        }
                        if (effectiveAccountId == null) {
                            effectiveAccountId = requestBody.getAccountId();
                        }
                        if (effectiveGroupName == null) {
                            effectiveGroupName = requestBody.getGroupName();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse chaos request body: {}", e.getMessage());
        }

        SecurityUtils.validateAccountOwnership(effectiveAccountId);

        ChaosResultDTO result = chaosService.terminateRandomInstanceAndHeal(effectiveAsgId, effectiveAccountId, effectiveGroupName);
        return ResponseEntity.ok(ApiResponse.ok("Chaos experiment executed", result));
    }
}
