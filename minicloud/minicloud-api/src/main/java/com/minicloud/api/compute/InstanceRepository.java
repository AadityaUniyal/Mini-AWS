package com.minicloud.api.compute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstanceRepository extends JpaRepository<Instance, UUID> {
    List<Instance> findAllByUserId(UUID userId);
    Optional<Instance> findByIdAndUserId(UUID id, UUID userId);
    List<Instance> findAllByStateNot(InstanceState state);
    long countByUserId(UUID userId);
}
