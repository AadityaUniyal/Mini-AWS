package com.minicloud.api.route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface HostedZoneRepository extends JpaRepository<HostedZone, UUID> {
    List<HostedZone> findByAccountId(String accountId);
}
