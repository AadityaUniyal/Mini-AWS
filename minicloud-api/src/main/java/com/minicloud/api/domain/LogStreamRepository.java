package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LogStreamRepository extends JpaRepository<LogStream, UUID> {
    Optional<LogStream> findByLogGroupNameAndLogStreamName(String logGroupName, String logStreamName);
    List<LogStream> findByAccountId(String accountId);
}
