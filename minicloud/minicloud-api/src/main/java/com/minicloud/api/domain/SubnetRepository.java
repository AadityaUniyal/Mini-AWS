package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubnetRepository extends JpaRepository<Subnet, UUID> {
    List<Subnet> findByVpcId(UUID vpcId);
    List<Subnet> findByAccountId(String accountId);
}
