package com.minicloud.api.route;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.NetworkAcl;
import com.minicloud.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/networking/nacls")
@RequiredArgsConstructor
@Tag(name = "VPC Network ACLs", description = "Stateless subnet-level firewall access control lists")
@SecurityRequirement(name = "BearerAuth")
public class NetworkAclController {

    private final NetworkAclService naclService;

    @GetMapping
    @Operation(summary = "List all Network ACLs for authenticated account")
    public ResponseEntity<ApiResponse<List<NetworkAcl>>> listNacls() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(naclService.listAclsForAccount(principal.getAccountId())));
    }

    @PostMapping
    @Operation(summary = "Create a new Network ACL")
    public ResponseEntity<ApiResponse<NetworkAcl>> createNacl(@Valid @RequestBody CreateNaclRequest request) {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        NetworkAcl created = naclService.createAcl(request, principal.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Network ACL created", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Network ACL details by ID")
    public ResponseEntity<ApiResponse<NetworkAcl>> getNacl(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(naclService.getAcl(id)));
    }

    @PostMapping("/{id}/rules")
    @Operation(summary = "Add a numbered rule to a Network ACL")
    public ResponseEntity<ApiResponse<NetworkAcl>> addRule(
            @PathVariable UUID id,
            @Valid @RequestBody AddNaclRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Rule added", naclService.addRule(id, request)));
    }

    @PostMapping("/{id}/evaluate")
    @Operation(summary = "Evaluate a simulated packet against Network ACL rules")
    public ResponseEntity<ApiResponse<NaclEvaluationResponse>> evaluate(
            @PathVariable UUID id,
            @Valid @RequestBody NaclEvaluationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(naclService.evaluatePacket(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom Network ACL")
    public ResponseEntity<ApiResponse<String>> deleteNacl(@PathVariable UUID id) {
        naclService.deleteAcl(id);
        return ResponseEntity.ok(ApiResponse.ok("Network ACL deleted", id.toString()));
    }
}
