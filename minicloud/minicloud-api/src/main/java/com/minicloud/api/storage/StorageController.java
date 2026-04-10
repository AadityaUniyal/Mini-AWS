package com.minicloud.api.storage;

import com.minicloud.api.exception.ResourceNotFoundException;
import com.minicloud.api.iam.*;
import com.minicloud.core.dto.ApiResponse;
import com.minicloud.core.dto.BucketResponse;
import com.minicloud.core.dto.ObjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
@Tag(name = "MiniS3", description = "Object storage — buckets and files (AWS S3 equivalent)")
@SecurityRequirement(name = "BearerAuth")
public class StorageController {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final PolicyEvaluator policyEvaluator;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ─────────────────────────── BUCKETS ───────────────────────────

    @PostMapping("/buckets")
    @Operation(summary = "Create a new storage bucket")
    public ResponseEntity<ApiResponse<BucketResponse>> createBucket(
            @RequestParam String name,
            Authentication auth) throws IOException {

        User user = getUser(auth);
        checkPermission(user, "s3:CreateBucket", "mc:s3:" + name);

        if (bucketRepository.existsByUserIdAndName(user.getId(), name)) {
            throw new IllegalArgumentException("Bucket already exists: " + name);
        }

        storageService.createBucketDirectory(user.getId(), name);

        Bucket bucket = Bucket.builder()
                .name(name)
                .userId(user.getId())
                .build();
        Bucket saved = bucketRepository.save(bucket);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bucket created", toBucketResponse(saved, 0)));
    }

    @GetMapping("/buckets")
    @Operation(summary = "List all buckets for current user")
    public ResponseEntity<ApiResponse<List<BucketResponse>>> listBuckets(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "s3:ListAllMyBuckets", "*");
        List<BucketResponse> buckets = bucketRepository.findAllByUserId(user.getId()).stream()
                .map(b -> toBucketResponse(b, objectRepository.countByBucketId(b.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(buckets));
    }

    @DeleteMapping("/buckets/{name}")
    @Transactional
    @Operation(summary = "Delete a bucket (must be empty per AWS spec)")
    public ResponseEntity<ApiResponse<String>> deleteBucket(
            @PathVariable String name,
            Authentication auth) {

        User user = getUser(auth);
        checkPermission(user, "s3:DeleteBucket", "mc:s3:" + name);
        Bucket bucket = getBucketOrThrow(user, name);

        // AWS Parity: Bucket must be empty before deleting
        if (!storageService.isBucketEmpty(user.getId(), name)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Bucket '" + name + "' is not empty. Please delete all objects first."));
        }

        // Delete metadata
        objectRepository.deleteAllByBucketId(bucket.getId());
        bucketRepository.delete(bucket);

        // Delete directory
        storageService.deleteBucketDirectory(user.getId(), name);

        return ResponseEntity.ok(ApiResponse.ok("Bucket deleted", name));
    }

    // ─────────────────────────── OBJECTS ───────────────────────────

    @PostMapping("/buckets/{name}/upload")
    @Operation(summary = "Upload a file to a bucket with optional metadata")
    public ResponseEntity<ApiResponse<ObjectResponse>> uploadObject(
            @PathVariable String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) java.util.Map<String, String> metadata,
            Authentication auth) throws IOException {

        User user = getUser(auth);
        checkPermission(user, "s3:PutObject", "mc:s3:" + name + "/*");
        Bucket bucket = getBucketOrThrow(user, name);

        String objectKey = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String localPath = storageService.writeObject(user.getId(), name, objectKey, file.getInputStream());

        // Remove existing object with same key if it exists
        objectRepository.findByBucketIdAndObjectKey(bucket.getId(), objectKey)
                .ifPresent(objectRepository::delete);

        StorageObject obj = StorageObject.builder()
                .bucketId(bucket.getId())
                .objectKey(objectKey)
                .sizeBytes(file.getSize())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .localPath(localPath)
                .metadata(metadata != null ? metadata : new java.util.HashMap<>())
                .build();

        StorageObject saved = objectRepository.save(obj);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("File uploaded", toObjectResponse(saved, name)));
    }

    @GetMapping("/buckets/{name}/list")
    @Operation(summary = "List objects (supports folder simulation via prefix)")
    public ResponseEntity<ApiResponse<List<ObjectResponse>>> listObjects(
            @PathVariable String name,
            @RequestParam(required = false) String prefix,
            Authentication auth) {

        User user = getUser(auth);
        checkPermission(user, "s3:ListBucket", "mc:s3:" + name);
        Bucket bucket = getBucketOrThrow(user, name);

        List<ObjectResponse> objects = objectRepository.findAllByBucketId(bucket.getId()).stream()
                .filter(o -> prefix == null || o.getObjectKey().startsWith(prefix))
                .map(o -> toObjectResponse(o, name))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(objects));
    }

    @GetMapping("/buckets/{name}/{**key}")
    @Operation(summary = "Download a file from a bucket")
    public ResponseEntity<InputStreamResource> downloadObject(
            @PathVariable String name,
            @PathVariable String key,
            Authentication auth) throws IOException {

        User user = getUser(auth);
        checkPermission(user, "s3:GetObject", "mc:s3:" + name + "/" + key);
        Bucket bucket = getBucketOrThrow(user, name);

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), key)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found: " + key));

        InputStream stream = storageService.readObject(obj.getLocalPath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
                .contentType(MediaType.parseMediaType(obj.getContentType()))
                .contentLength(obj.getSizeBytes())
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/buckets/{name}/{**key}")
    @Operation(summary = "Delete an object from a bucket")
    public ResponseEntity<ApiResponse<String>> deleteObject(
            @PathVariable String name,
            @PathVariable String key,
            Authentication auth) {

        User user = getUser(auth);
        checkPermission(user, "s3:DeleteObject", "mc:s3:" + name + "/" + key);
        Bucket bucket = getBucketOrThrow(user, name);

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), key)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found: " + key));

        storageService.deleteObject(obj.getLocalPath());
        objectRepository.delete(obj);

        return ResponseEntity.ok(ApiResponse.ok("Object deleted", key));
    }

    // ─────────────────────────── HELPERS ───────────────────────────

    private void checkPermission(User user, String action, String resource) {
        if (!policyEvaluator.isAuthorized(user.getPolicies(), action, resource)) {
            log.warn("Access denied for user '{}' attempting action '{}' on resource '{}'", user.getUsername(), action, resource);
            throw new AccessDeniedException("User is not authorized to perform " + action + " on resource " + resource);
        }
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Bucket getBucketOrThrow(User user, String name) {
        return bucketRepository.findByUserIdAndName(user.getId(), name)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + name));
    }

    private BucketResponse toBucketResponse(Bucket bucket, long count) {
        return BucketResponse.builder()
                .id(bucket.getId().toString())
                .name(bucket.getName())
                .userId(bucket.getUserId().toString())
                .objectCount(count)
                .createdAt(bucket.getCreatedAt() != null ? bucket.getCreatedAt().format(FMT) : "")
                .build();
    }

    private ObjectResponse toObjectResponse(StorageObject obj, String bucketName) {
        return ObjectResponse.builder()
                .id(obj.getId().toString())
                .bucketName(bucketName)
                .objectKey(obj.getObjectKey())
                .sizeBytes(obj.getSizeBytes())
                .contentType(obj.getContentType())
                .metadata(obj.getMetadata())
                .createdAt(obj.getCreatedAt() != null ? obj.getCreatedAt().format(FMT) : "")
                .build();
    }
}
