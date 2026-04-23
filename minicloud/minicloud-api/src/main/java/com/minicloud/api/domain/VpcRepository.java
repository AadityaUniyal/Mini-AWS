package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface VpcRepository extends JpaRepository<Vpc, UUID> {
    List<Vpc> findByAccountId(String accountId);
}
