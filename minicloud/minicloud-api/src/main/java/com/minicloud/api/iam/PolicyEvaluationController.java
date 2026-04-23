package com.minicloud.api.iam;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.domain.*;
import com.minicloud.api.iam.PolicyEvaluator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoint for other microservices to evaluate IAM policies.
 * Called by the API Gateway or other services to check authorization.
 */
@RestController
@RequestMapping("/iam/evaluate")
@RequiredArgsConstructor
@Tag(name = "Policy Evaluation", description = "Internal policy evaluation endpoint for service-to-service calls")
@SecurityRequirement(name = "BearerAuth")
public class PolicyEvaluationController {

    private final PolicyEvaluator policyEvaluator;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Evaluate whether a user is authorized for an action on a resource")
    public ResponseEntity<ApiResponse<Boolean>> evaluate(
            @RequestParam String username,
            @RequestParam String action,
            @RequestParam String resource) {

        User user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.ok(ApiResponse.ok("User not found", false));
        }

        boolean authorized = policyEvaluator.isAuthorized(user, action, resource);
        return ResponseEntity.ok(ApiResponse.ok(authorized));
    }
}

