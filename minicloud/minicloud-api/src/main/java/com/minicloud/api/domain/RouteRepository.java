package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID> {
    @Query("SELECT r FROM Route r WHERE r.domainOrPath = :value")
    Optional<Route> findByDomainOrPath(@Param("value") String value);
    Optional<Route> findByUserIdAndName(UUID userId, String name);
    List<Route> findByUserId(UUID userId);
    List<Route> findAllByEnabledTrue();
}
