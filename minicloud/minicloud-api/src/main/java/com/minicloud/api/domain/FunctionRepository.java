package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FunctionRepository extends JpaRepository<Function, UUID> {
    Optional<Function> findByName(String name);
    List<Function> findByUserId(UUID userId);
    List<Function> findByAccountId(String accountId);
    boolean existsByName(String name);
}
