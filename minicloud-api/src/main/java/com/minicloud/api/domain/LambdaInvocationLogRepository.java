package com.minicloud.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LambdaInvocationLogRepository extends JpaRepository<LambdaInvocationLog, UUID> {
    Page<LambdaInvocationLog> findAllByFunctionIdOrderByTimestampDesc(UUID functionId, Pageable pageable);
}
