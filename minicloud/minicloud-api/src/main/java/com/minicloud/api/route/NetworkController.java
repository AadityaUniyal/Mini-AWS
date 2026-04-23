package com.minicloud.api.route;

import com.minicloud.api.domain.Vpc;
import com.minicloud.api.domain.VpcRepository;
import com.minicloud.api.domain.Subnet;
import com.minicloud.api.domain.SubnetRepository;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vpc")
@Tag(name = "VPC", description = "VPC and Subnet Management")
@RequiredArgsConstructor
public class NetworkController {

    private final VpcRepository vpcRepository;
    private final SubnetRepository subnetRepository;

    @GetMapping("/{accountId}")
    @Operation(summary = "Get all VPCs for an account")
    public ResponseEntity<ApiResponse<List<Vpc>>> getVpcs(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.ok("VPCs retrieved", vpcRepository.findByAccountId(accountId)));
    }

    @GetMapping("/{accountId}/subnets")
    @Operation(summary = "Get all subnets for an account")
    public ResponseEntity<ApiResponse<List<Subnet>>> getSubnets(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.ok("Subnets retrieved", subnetRepository.findByAccountId(accountId)));
    }

    @GetMapping("/vpc/{vpcId}/subnets")
    @Operation(summary = "Get all subnets for a specific VPC")
    public ResponseEntity<ApiResponse<List<Subnet>>> getSubnetsByVpc(@PathVariable UUID vpcId) {
        return ResponseEntity.ok(ApiResponse.ok("Subnets retrieved", subnetRepository.findByVpcId(vpcId)));
    }
}
