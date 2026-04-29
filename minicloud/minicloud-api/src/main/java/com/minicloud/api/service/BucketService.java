package com.minicloud.api.service;

import com.minicloud.api.domain.Bucket;
import com.minicloud.api.domain.BucketRepository;
import com.minicloud.api.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing S3-like buckets.
 * All methods are transactional for proper database session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BucketService {

    private final BucketRepository bucketRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<Bucket> getAllBuckets() {
        log.info("[BucketService] Loading all buckets from database");
        List<Bucket> buckets = bucketRepository.findAll();
        log.info("[BucketService] Found {} buckets", buckets.size());
        return buckets;
    }

    @Transactional(readOnly = true)
    public List<Bucket> getBucketsByAccountId(String accountId) {
        log.info("[BucketService] Loading buckets for account: {}", accountId);
        return bucketRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public Bucket getBucketById(UUID id) {
        log.info("[BucketService] Loading bucket by ID: {}", id);
        return bucketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bucket not found: " + id));
    }

    @Transactional
    public Bucket createBucket(String name, UUID ownerId, String accountId, String region) {
        log.info("[BucketService] Creating bucket: {} for account: {}", name, accountId);
        
        if (bucketRepository.existsByName(name)) {
            throw new RuntimeException("Bucket already exists: " + name);
        }

        Bucket bucket = Bucket.builder()
                .name(name)
                .userId(ownerId)
                .accountId(accountId)
                .region(region != null ? region : "us-east-1")
                .publicRead(false)
                .websiteEnabled(false)
                .build();

        Bucket saved = bucketRepository.save(bucket);
        
        // Create physical directory
        try {
            storageService.createBucketDirectory(ownerId, name);
        } catch (Exception e) {
            log.error("[BucketService] Failed to create bucket directory", e);
        }
        
        log.info("[BucketService] Bucket created successfully: {}", saved.getId());
        return saved;
    }

    @Transactional
    public void deleteBucket(UUID bucketId) {
        log.info("[BucketService] Deleting bucket: {}", bucketId);
        
        Bucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketId));

        // Delete physical directory
        try {
            storageService.deleteBucketDirectory(bucket.getUserId(), bucket.getName());
        } catch (Exception e) {
            log.error("[BucketService] Failed to delete bucket directory", e);
        }

        bucketRepository.delete(bucket);
        log.info("[BucketService] Bucket deleted successfully: {}", bucketId);
    }

    @Transactional
    public Bucket updateBucket(UUID bucketId, Boolean publicRead, Boolean websiteEnabled) {
        log.info("[BucketService] Updating bucket: {}", bucketId);
        
        Bucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketId));

        if (publicRead != null) {
            bucket.setPublicRead(publicRead);
        }
        if (websiteEnabled != null) {
            bucket.setWebsiteEnabled(websiteEnabled);
        }

        Bucket updated = bucketRepository.save(bucket);
        log.info("[BucketService] Bucket updated successfully: {}", bucketId);
        return updated;
    }
}
