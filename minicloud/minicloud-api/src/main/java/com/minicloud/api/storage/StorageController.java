package com.minicloud.api.storage;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.BucketResponse;
import com.minicloud.api.dto.ObjectResponse;
import com.minicloud.api.domain.Bucket;
import com.minicloud.api.domain.StorageObject;
import com.minicloud.api.domain.BucketRepository;
import com.minicloud.api.domain.ObjectRepository;
import com.minicloud.api.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
@Tag(name = "S3 Storage", description = "Buckets and Object storage operations")
public class StorageController {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;
    private final StorageService storageService;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.domain.UserRepository userRepository;

    @GetMapping("/buckets/{name}/objects")
    @Operation(summary = "List all objects in a bucket")
    public ResponseEntity<ApiResponse<List<ObjectResponse>>> listObjects(
            @PathVariable String name,
            @RequestParam UUID userId) {

        Bucket bucket = bucketRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Bucket not found: " + name));

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
            @RequestParam UUID userId) {

        final String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        Bucket bucket = bucketRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Bucket not found: " + name));

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey)
                .orElseThrow(() -> new RuntimeException("Object not found: " + normalizedKey));

        storageService.deleteObject(obj.getLocalPath());
        objectRepository.delete(obj);

        String username = userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString());
        auditService.recordSuccess(username, "S3", "DeleteObject", name + "/" + normalizedKey);

        return ResponseEntity.ok(ApiResponse.ok("Object deleted", normalizedKey));
    }

    @PostMapping("/buckets")
    @Operation(summary = "Create a new storage bucket")
    public ResponseEntity<ApiResponse<BucketResponse>> createBucket(
            @RequestParam String name,
            @RequestParam UUID userId,
            @RequestParam(required = false, defaultValue = "0") Integer retentionDays) throws IOException {

        if (bucketRepository.existsByUserIdAndName(userId, name)) {
            throw new IllegalArgumentException("Bucket already exists: " + name);
        }

        storageService.createBucketDirectory(userId, name);

        Bucket bucket = Bucket.builder()
                .name(name)
                .userId(userId)
                .retentionDays(retentionDays)
                .build();
        Bucket saved = bucketRepository.save(bucket);
        
        String username = userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString());
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
            @RequestParam UUID userId) {

        Bucket bucket = bucketRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Bucket not found"));

        if (!storageService.isBucketEmpty(userId, name)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Bucket is not empty"));
        }

        objectRepository.deleteAllByBucketId(bucket.getId());
        bucketRepository.delete(bucket);
        storageService.deleteBucketDirectory(userId, name);
        
        String username = userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString());
        auditService.recordSuccess(username, "S3", "DeleteBucket", name);
        
        return ResponseEntity.ok(ApiResponse.ok("Bucket deleted", name));
    }

    @PostMapping("/buckets/{name}/upload")
    @Transactional
    public ResponseEntity<ApiResponse<ObjectResponse>> uploadObject(
            @PathVariable String name,
            @RequestParam UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Map<String, String> metadata) throws IOException {

        Bucket bucket = bucketRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Bucket not found"));

        String objectKey = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        byte[] content = storageService.readAllBytes(file.getInputStream());
        String localPath = storageService.writeObject(userId, name, objectKey, new java.io.ByteArrayInputStream(content));

        objectRepository.findByBucketIdAndObjectKey(bucket.getId(), objectKey)
                .ifPresent(objectRepository::delete);

        StorageObject obj = StorageObject.builder()
                .bucketId(bucket.getId())
                .objectKey(objectKey)
                .sizeBytes(file.getSize())
                .contentType(file.getContentType())
                .content(content)
                .localPath(localPath)
                .metadata(metadata)
                .build();

        StorageObject saved = objectRepository.save(obj);
        
        String username = userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString());
        auditService.recordSuccess(username, "S3", "PutObject", name + "/" + objectKey);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("File uploaded", toObjectResponse(saved, name)));
    }

    @GetMapping("/buckets/{name}/{*key}")
    public ResponseEntity<InputStreamResource> downloadObject(
            @PathVariable String name,
            @PathVariable String key,
            @RequestParam UUID userId) throws IOException {

        final String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        Bucket bucket = bucketRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Bucket not found"));

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey)
                .orElseThrow(() -> new RuntimeException("Object not found"));

        InputStream stream = obj.getContent() != null 
                ? storageService.readObject(obj.getContent())
                : storageService.readObjectFromDisk(obj.getLocalPath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + Path.of(key).getFileName().toString() + "\"")
                .contentType(MediaType.parseMediaType(obj.getContentType()))
                .contentLength(obj.getSizeBytes())
                .body(new InputStreamResource(stream));
    }

    private BucketResponse toBucketResponse(Bucket b, long count, long size) {
        return BucketResponse.builder()
                .id(b.getId().toString())
                .name(b.getName())
                .ownerId(b.getUserId().toString())
                .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
                .objectCount(count)
                .totalSizeBytes(size)
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
