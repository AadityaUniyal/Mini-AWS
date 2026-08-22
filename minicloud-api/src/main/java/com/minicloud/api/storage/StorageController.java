package com.minicloud.api.storage;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.Bucket;
import com.minicloud.api.domain.BucketRepository;
import com.minicloud.api.domain.ObjectRepository;
import com.minicloud.api.domain.StorageObject;
import com.minicloud.api.domain.Task;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.BucketResponse;
import com.minicloud.api.dto.ObjectResponse;
import com.minicloud.api.lambda.CreateTriggerRequest;
import com.minicloud.api.lambda.TriggerResponse;
import com.minicloud.api.service.TaskService;
import com.minicloud.api.storage.event.S3ObjectCreatedEvent;
import com.minicloud.api.storage.event.S3ObjectDeletedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "S3 Storage", description = "Buckets and Object storage operations")
@SecurityRequirement(name = "BearerAuth")
public class StorageController {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;
    private final StorageService storageService;
    private final com.minicloud.api.audit.AuditService auditService;
    private final TaskService taskService;
    private final S3TriggerService s3TriggerService;
    private final S3TriggerDispatcher s3TriggerDispatcher;
    private final ApplicationEventPublisher eventPublisher;

    private final ExecutorService asyncStorageExecutor = Executors.newFixedThreadPool(8);

    @GetMapping("/buckets")
    @Operation(summary = "List all buckets for current authenticated account")
    public ResponseEntity<ApiResponse<List<BucketResponse>>> listCurrentBuckets() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        List<Bucket> buckets = principal.getAccountId() != null
                ? bucketRepository.findByAccountId(principal.getAccountId())
                : bucketRepository.findByUserId(principal.getUserId());

        List<BucketResponse> responses = buckets.stream()
                .map(b -> toBucketResponse(b,
                        objectRepository.countByBucketId(b.getId()),
                        objectRepository.sumSizeByBucketId(b.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @GetMapping("/buckets/{name}/objects")
    @Operation(summary = "List all objects in a bucket")
    public ResponseEntity<ApiResponse<List<ObjectResponse>>> listObjects(
            @PathVariable String name,
            @RequestParam(required = false) UUID userId) {

        Bucket bucket = resolveBucket(name, userId);

        List<ObjectResponse> objects = objectRepository.findAllByBucketId(bucket.getId())
                .stream()
                .map(obj -> toObjectResponse(obj, name))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(objects));
    }

    @DeleteMapping("/buckets/{name}/objects/{*key}")
    @Transactional
    @Operation(summary = "Delete an object from a bucket")
    public ResponseEntity<ApiResponse<String>> deleteObject(
            @PathVariable String name,
            @PathVariable String key,
            @RequestParam(required = false) UUID userId) {

        final String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        Bucket bucket = resolveBucket(name, userId);

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey)
                .orElseThrow(() -> new RuntimeException("Object not found: " + normalizedKey));

        storageService.deleteObject(obj.getLocalPath());
        objectRepository.delete(obj);

        // Update bucket usage counts
        bucket.setTotalSizeBytes(Math.max(0, bucket.getTotalSizeBytes() - obj.getSizeBytes()));
        bucket.setObjectCount(Math.max(0, bucket.getObjectCount() - 1));
        bucket.setLastAccessed(LocalDateTime.now());
        bucketRepository.save(bucket);

        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "S3", "DeleteObject", name + "/" + normalizedKey);

        eventPublisher.publishEvent(new S3ObjectDeletedEvent(this, name, normalizedKey, bucket.getUserId(), bucket.getAccountId()));

        return ResponseEntity.ok(ApiResponse.ok("Object deleted", normalizedKey));
    }

    @PostMapping("/buckets")
    @Operation(summary = "Create a new storage bucket")
    public ResponseEntity<ApiResponse<BucketResponse>> createBucket(
            @RequestParam String name,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false, defaultValue = "0") Integer retentionDays) throws IOException {

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        UUID effectiveUserId = principal.getUserId() != null ? principal.getUserId() : userId;
        String effectiveAccountId = principal.getAccountId();

        if (bucketRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Bucket already exists: " + name);
        }

        storageService.createBucketDirectory(effectiveAccountId != null ? effectiveAccountId : effectiveUserId.toString(), name);

        Bucket bucket = Bucket.builder()
                .name(name)
                .userId(effectiveUserId)
                .accountId(effectiveAccountId)
                .retentionDays(retentionDays)
                .createdAt(LocalDateTime.now())
                .build();
        Bucket saved = bucketRepository.save(bucket);
        
        String username = principal.getUsername();
        auditService.recordSuccess(username, "S3", "CreateBucket", name);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bucket created", toBucketResponse(saved, 0, 0)));
    }

    @GetMapping("/buckets/user/{userId}")
    @Operation(summary = "List all buckets for a user")
    public ResponseEntity<ApiResponse<List<BucketResponse>>> listBuckets(@PathVariable UUID userId) {
        List<BucketResponse> buckets = bucketRepository.findByUserId(userId).stream()
                .map(b -> toBucketResponse(b, 
                        objectRepository.countByBucketId(b.getId()),
                        objectRepository.sumSizeByBucketId(b.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(buckets));
    }

    @DeleteMapping("/buckets/{name}")
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteBucket(
            @PathVariable String name,
            @RequestParam(required = false) UUID userId) {

        Bucket bucket = resolveBucket(name, userId);

        if (!storageService.isBucketEmpty(bucket.getAccountId() != null ? bucket.getAccountId() : bucket.getUserId().toString(), name)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Bucket is not empty"));
        }

        objectRepository.deleteAllByBucketId(bucket.getId());
        bucketRepository.delete(bucket);
        storageService.deleteBucketDirectory(bucket.getAccountId() != null ? bucket.getAccountId() : bucket.getUserId().toString(), name);
        
        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "S3", "DeleteBucket", name);
        
        return ResponseEntity.ok(ApiResponse.ok("Bucket deleted", name));
    }

    @PostMapping(value = "/buckets/{name}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Task>> uploadObject(
            @PathVariable String name,
            @RequestParam(required = false) UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Map<String, String> metadata) throws IOException {

        Bucket bucket = resolveBucket(name, userId);

        String objectKey = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        byte[] content = file.getBytes();
        String contentType = file.getContentType();
        long size = file.getSize();

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        UUID effectiveUserId = principal.getUserId() != null ? principal.getUserId() : bucket.getUserId();

        Task task = taskService.createTask("S3_UPLOAD", "Uploading file " + objectKey + " to bucket " + name, effectiveUserId, bucket.getAccountId());

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                taskService.updateProgress(task.getId(), 20, "RUNNING", null);
                
                String localPath = storageService.writeObject(bucket.getUserId(), name, objectKey, new java.io.ByteArrayInputStream(content));

                taskService.updateProgress(task.getId(), 80, "RUNNING", null);

                objectRepository.findByBucketIdAndObjectKey(bucket.getId(), objectKey)
                        .ifPresent(objectRepository::delete);

                StorageObject obj = StorageObject.builder()
                        .bucketId(bucket.getId())
                        .objectKey(objectKey)
                        .sizeBytes(size)
                        .contentType(contentType)
                        .content(content)
                        .localPath(localPath)
                        .metadata(metadata != null ? metadata : Map.of())
                        .lastModified(LocalDateTime.now())
                        .build();
                objectRepository.save(obj);

                // Update bucket usage
                bucket.setTotalSizeBytes(bucket.getTotalSizeBytes() + size);
                bucket.setObjectCount(bucket.getObjectCount() + 1);
                bucket.setLastAccessed(LocalDateTime.now());
                bucketRepository.save(bucket);

                String username = principal.getUsername();
                auditService.recordSuccess(username, "S3", "PutObject", name + "/" + objectKey);

                taskService.updateProgress(task.getId(), 100, "COMPLETED", null);

                String eTag = org.springframework.util.DigestUtils.md5DigestAsHex(content);
                s3TriggerDispatcher.dispatchUploadEvent(name, objectKey, size, eTag, effectiveUserId, bucket.getAccountId());
                eventPublisher.publishEvent(new S3ObjectCreatedEvent(this, name, objectKey, size, eTag, effectiveUserId, bucket.getAccountId()));
            } catch (Exception e) {
                log.error("Asynchronous upload failed", e);
                taskService.updateProgress(task.getId(), 100, "FAILED", e.getMessage());
            }
        }, asyncStorageExecutor);

        taskService.registerActiveFuture(task.getId(), future);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Upload sequence initiated", task));
    }

    @GetMapping("/buckets/{name}/objects/{*key}")
    @Operation(summary = "Download an object from a bucket (streaming)")
    public ResponseEntity<InputStreamResource> downloadObject(
            @PathVariable String name,
            @PathVariable String key,
            @RequestParam(required = false) UUID userId) throws IOException {

        final String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        Bucket bucket = resolveBucket(name, userId);

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey)
                .orElseThrow(() -> new RuntimeException("Object not found: " + normalizedKey));

        InputStream inputStream;
        if (obj.getLocalPath() != null && new java.io.File(obj.getLocalPath()).exists()) {
            inputStream = storageService.openObjectStream(obj.getLocalPath());
        } else if (obj.getContent() != null) {
            inputStream = storageService.readObject(obj.getContent());
        } else {
            throw new RuntimeException("Object payload missing on disk and database");
        }

        String mediaTypeStr = obj.getContentType() != null ? obj.getContentType() : "application/octet-stream";
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mediaTypeStr);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + obj.getObjectKey() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(obj.getSizeBytes()))
                .body(new InputStreamResource(inputStream));
    }

    // ── S3 Lambda Triggers ───────────────────────────────────────────────────

    @PostMapping("/triggers")
    @Operation(summary = "Create an S3 event notification trigger pointing to a Lambda function")
    public ResponseEntity<ApiResponse<TriggerResponse>> createTrigger(@RequestBody CreateTriggerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Trigger created", s3TriggerService.createTrigger(request)));
    }

    @GetMapping("/triggers/{id}")
    @Operation(summary = "Get an S3 Lambda trigger by ID")
    public ResponseEntity<ApiResponse<TriggerResponse>> getTriggerById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Trigger retrieved", s3TriggerService.toResponse(s3TriggerService.getTrigger(id))));
    }

    @GetMapping("/triggers")
    @Operation(summary = "List triggers with optional bucketName query parameter")
    public ResponseEntity<ApiResponse<List<TriggerResponse>>> listTriggersWithParam(
            @RequestParam(required = false) String bucketName) {
        return ResponseEntity.ok(ApiResponse.ok(s3TriggerService.listTriggers(bucketName)));
    }

    @GetMapping("/buckets/{bucketName}/triggers")
    @Operation(summary = "List all event triggers configured for a bucket")
    public ResponseEntity<ApiResponse<List<TriggerResponse>>> listTriggers(@PathVariable String bucketName) {
        return ResponseEntity.ok(ApiResponse.ok(s3TriggerService.listTriggers(bucketName)));
    }

    @DeleteMapping("/triggers/{id}")
    @Operation(summary = "Delete an S3 Lambda trigger")
    public ResponseEntity<Void> deleteTrigger(@PathVariable UUID id) {
        s3TriggerService.deleteTrigger(id);
        return ResponseEntity.noContent().build();
    }

    private Bucket resolveBucket(String name, UUID userId) {
        Bucket bucket = bucketRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Bucket not found: " + name));

        try {
            UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
            if (principal.getAccountId() != null && bucket.getAccountId() != null) {
                SecurityUtils.validateAccountOwnership(bucket.getAccountId());
            } else if (userId != null && bucket.getUserId() != null && !bucket.getUserId().equals(userId)) {
                SecurityUtils.validateAccountOwnership(bucket.getAccountId());
            }
        } catch (Exception ignored) {
            // Fallback for public buckets or unauthenticated reads if enabled
            if (!bucket.isPublicRead()) {
                throw ignored;
            }
        }
        return bucket;
    }

    private BucketResponse toBucketResponse(Bucket bucket, long objectCount, long totalSize) {
        return BucketResponse.builder()
                .id(bucket.getId().toString())
                .name(bucket.getName())
                .userId(bucket.getUserId() != null ? bucket.getUserId().toString() : null)
                .accountId(bucket.getAccountId())
                .objectCount(objectCount)
                .totalSizeBytes(totalSize)
                .retentionDays(bucket.getRetentionDays())
                .createdAt(bucket.getCreatedAt() != null ? bucket.getCreatedAt().toString() : null)
                .build();
    }

    private ObjectResponse toObjectResponse(StorageObject obj, String bucketName) {
        return ObjectResponse.builder()
                .id(obj.getId().toString())
                .bucketName(bucketName)
                .objectKey(obj.getObjectKey())
                .sizeBytes(obj.getSizeBytes())
                .contentType(obj.getContentType())
                .lastModified(obj.getLastModified() != null ? obj.getLastModified().toString() : null)
                .createdAt(obj.getCreatedAt() != null ? obj.getCreatedAt().toString() : null)
                .metadata(obj.getMetadata())
                .build();
    }
}
