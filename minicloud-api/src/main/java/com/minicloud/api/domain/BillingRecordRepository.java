package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, UUID> {
    List<BillingRecord> findByAccountId(String accountId);
}
