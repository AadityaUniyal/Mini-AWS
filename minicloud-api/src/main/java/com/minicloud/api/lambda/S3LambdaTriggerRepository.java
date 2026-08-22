package com.minicloud.api.lambda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface S3LambdaTriggerRepository extends JpaRepository<S3LambdaTrigger, UUID> {

    List<S3LambdaTrigger> findByBucketName(String bucketName);

    List<S3LambdaTrigger> findByBucketNameAndEnabledTrue(String bucketName);

    List<S3LambdaTrigger> findByFunctionName(String functionName);

    List<S3LambdaTrigger> findByUserId(UUID userId);

    List<S3LambdaTrigger> findByAccountId(String accountId);

    Optional<S3LambdaTrigger> findByBucketNameAndFunctionName(String bucketName, String functionName);

    void deleteByBucketName(String bucketName);

    void deleteByFunctionName(String functionName);
}
