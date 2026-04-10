package com.minicloud.api.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {
    Optional<Policy> findByName(String name);
    boolean existsByName(String name);
}
