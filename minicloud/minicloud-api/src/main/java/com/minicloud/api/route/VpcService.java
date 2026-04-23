package com.minicloud.api.route;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VpcService {

    private final VpcRepository vpcRepository;
    private final SubnetRepository subnetRepository;

    @Transactional
    public Vpc createDefaultVpc(String accountId) {
        log.info("Provisioning default VPC for account {}", accountId);

        Vpc vpc = Vpc.builder()
                .name("default")
                .cidrBlock("172.31.0.0/16")
                .accountId(accountId)
                .isDefault(true)
                .state("available")
                .build();

        Vpc savedVpc = vpcRepository.save(vpc);

        // Provision a few default subnets in different simulated AZs
        createSubnet(savedVpc.getId(), "172.31.1.0/24", "us-east-1a", accountId);
        createSubnet(savedVpc.getId(), "172.31.2.0/24", "us-east-1b", accountId);

        return savedVpc;
    }

    private void createSubnet(UUID vpcId, String cidr, String az, String accountId) {
        Subnet subnet = Subnet.builder()
                .name("default-" + az)
                .vpcId(vpcId)
                .cidrBlock(cidr)
                .availabilityZone(az)
                .accountId(accountId)
                .build();
        subnetRepository.save(subnet);
    }
}
