package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NetworkAclRepository extends JpaRepository<NetworkAcl, UUID> {
    List<NetworkAcl> findByAccountId(String accountId);
    List<NetworkAcl> findByVpcId(UUID vpcId);
    Optional<NetworkAcl> findBySubnetId(UUID subnetId);
}
