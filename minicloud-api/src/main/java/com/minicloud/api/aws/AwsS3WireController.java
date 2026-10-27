package com.minicloud.api.aws;

import com.minicloud.api.audit.AuditService;
import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.Bucket;
import com.minicloud.api.domain.BucketRepository;
import com.minicloud.api.domain.ObjectRepository;
import com.minicloud.api.domain.StorageObject;
import com.minicloud.api.storage.MimeTypeResolver;
import com.minicloud.api.storage.S3TriggerDispatcher;
import com.minicloud.api.storage.StorageService;
import com.minicloud.api.storage.event.S3ObjectCreatedEvent;
import com.minicloud.api.storage.event.S3ObjectDeletedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 Wire-Protocol Adapter.
 * Provides AWS CLI and AWS SDK drop-in compatibility using standard S3 REST endpoints and XML schemas.
 * Example usage:
 *   aws --endpoint-url http://localhost:8080/s3 s3 ls
 *   aws --endpoint-url http://localhost:8080/s3 s3 mb s3://my-bucket
 *   aws --endpoint-url http://localhost:8080/s3 s3 cp file.txt s3://my-bucket/
 */
@Slf4j
@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
@Tag(name = "AWS S3 Wire Protocol", description = "Standard AWS S3 REST API & XML compatible endpoints for AWS CLI / SDKs")
public class AwsS3WireController {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final S3TriggerDispatcher s3TriggerDispatcher;
    private final ApplicationEventPublisher eventPublisher;
    private final MimeTypeResolver mimeTypeResolver;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * List all buckets in AWS S3 XML format.
     */
    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "AWS S3 ListAllMyBuckets wire protocol")
    public ResponseEntity<String> listAllMyBuckets() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId() != null ? principal.getAccountId() : "123456789012";
        String username = principal.getUsername() != null ? principal.getUsername() : "admin";

        List<Bucket> buckets = principal.getAccountId() != null
                ? bucketRepository.findByAccountId(principal.getAccountId())
                : bucketRepository.findByUserId(principal.getUserId());

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n");
        xml.append("  <Owner>\n");
        xml.append("    <ID>").append(escapeXml(accountId)).append("</ID>\n");
        xml.append("    <DisplayName>").append(escapeXml(username)).append("</DisplayName>\n");
        xml.append("  </Owner>\n");
        xml.append("  <Buckets>\n");

        for (Bucket b : buckets) {
            String createdIso = b.getCreatedAt() != null
                    ? b.getCreatedAt().toInstant(ZoneOffset.UTC).toString()
                    : ISO_FORMATTER.format(java.time.Instant.now());
            xml.append("    <Bucket>\n");
            xml.append("      <Name>").append(escapeXml(b.getName())).append("</Name>\n");
            xml.append("      <CreationDate>").append(createdIso).append("</CreationDate>\n");
            xml.append("    </Bucket>\n");
        }

        xml.append("  </Buckets>\n");
        xml.append("</ListAllMyBucketsResult>");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    /**
     * Create bucket (PUT /s3/{bucketName}).
     */
    @PutMapping("/{bucketName}")
    @Operation(summary = "AWS S3 CreateBucket wire protocol")
    public ResponseEntity<Void> createBucket(@PathVariable String bucketName) {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId() != null ? principal.getAccountId() : "123456789012";
        UUID userId = principal.getUserId() != null ? principal.getUserId() : UUID.fromString("00000000-0000-0000-0000-000000000001");

        if (bucketRepository.findByName(bucketName).isEmpty()) {
            try {
                storageService.createBucketDirectory(accountId, bucketName);
            } catch (Exception e) {
                log.warn("Failed to create bucket dir: {}", e.getMessage());
            }

            Bucket bucket = Bucket.builder()
                    .name(bucketName)
                    .userId(userId)
                    .accountId(accountId)
                    .retentionDays(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            bucketRepository.save(bucket);
            auditService.recordSuccess(principal.getUsername(), "S3", "CreateBucket", bucketName);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Location", "/" + bucketName);
        return new ResponseEntity<>(headers, HttpStatus.OK);
    }

    /**
     * Head bucket (HEAD /s3/{bucketName}).
     */
    @RequestMapping(value = "/{bucketName}", method = RequestMethod.HEAD)
    @Operation(summary = "AWS S3 HeadBucket wire protocol")
    public ResponseEntity<Void> headBucket(@PathVariable String bucketName) {
        if (bucketRepository.findByName(bucketName).isPresent()) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Delete bucket (DELETE /s3/{bucketName}).
     */
    @DeleteMapping("/{bucketName}")
    @Transactional
    @Operation(summary = "AWS S3 DeleteBucket wire protocol")
    public ResponseEntity<Void> deleteBucket(@PathVariable String bucketName) {
        Bucket bucket = bucketRepository.findByName(bucketName)
                .orElse(null);
        if (bucket != null) {
            bucketRepository.delete(bucket);
            storageService.deleteBucketDirectory(bucket.getAccountId() != null ? bucket.getAccountId() : "default", bucketName);
            auditService.recordSuccess(SecurityUtils.getAuthenticatedUsername(), "S3", "DeleteBucket", bucketName);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * List objects in bucket (GET /s3/{bucketName}).
     */
    @GetMapping(value = "/{bucketName}", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "AWS S3 ListObjects wire protocol")
    public ResponseEntity<String> listObjects(
            @PathVariable String bucketName,
            @RequestParam(required = false, defaultValue = "") String prefix,
            @RequestParam(name = "max-keys", required = false, defaultValue = "1000") int maxKeys) {

        Bucket bucket = bucketRepository.findByName(bucketName)
                .orElse(null);

        if (bucket == null) {
            String errorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<Error><Code>NoSuchBucket</Code><Message>The specified bucket does not exist</Message><BucketName>"
                    + escapeXml(bucketName) + "</BucketName></Error>";
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(errorXml);
        }

        List<StorageObject> objects = objectRepository.findAllByBucketId(bucket.getId());
        if (!prefix.isEmpty()) {
            objects = objects.stream().filter(o -> o.getObjectKey().startsWith(prefix)).toList();
        }
        if (objects.size() > maxKeys) {
            objects = objects.subList(0, maxKeys);
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n");
        xml.append("  <Name>").append(escapeXml(bucketName)).append("</Name>\n");
        xml.append("  <Prefix>").append(escapeXml(prefix)).append("</Prefix>\n");
        xml.append("  <KeyCount>").append(objects.size()).append("</KeyCount>\n");
        xml.append("  <MaxKeys>").append(maxKeys).append("</MaxKeys>\n");
        xml.append("  <IsTruncated>false</IsTruncated>\n");

        for (StorageObject obj : objects) {
            String isoDate = obj.getLastModified() != null
                    ? obj.getLastModified().toInstant(ZoneOffset.UTC).toString()
                    : ISO_FORMATTER.format(java.time.Instant.now());
            String etag = obj.getEtag() != null ? obj.getEtag() : "d41d8cd98f00b204e9800998ecf8427e";
            if (!etag.startsWith("\"")) {
                etag = "\"" + etag + "\"";
            }

            xml.append("  <Contents>\n");
            xml.append("    <Key>").append(escapeXml(obj.getObjectKey())).append("</Key>\n");
            xml.append("    <LastModified>").append(isoDate).append("</LastModified>\n");
            xml.append("    <ETag>").append(escapeXml(etag)).append("</ETag>\n");
            xml.append("    <Size>").append(obj.getSizeBytes()).append("</Size>\n");
            xml.append("    <StorageClass>STANDARD</StorageClass>\n");
            xml.append("  </Contents>\n");
        }

        xml.append("</ListBucketResult>");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    /**
     * Put object into bucket (PUT /s3/{bucketName}/{key}).
     */
    @PutMapping("/{bucketName}/{*objectKey}")
    @Transactional
    @Operation(summary = "AWS S3 PutObject wire protocol")
    public ResponseEntity<Void> putObject(
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            HttpServletRequest request) {

        final String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId() != null ? principal.getAccountId() : "123456789012";
        UUID userId = principal.getUserId() != null ? principal.getUserId() : UUID.fromString("00000000-0000-0000-0000-000000000001");

        Bucket bucket = bucketRepository.findByName(bucketName)
                .orElseGet(() -> {
                    try {
                        storageService.createBucketDirectory(accountId, bucketName);
                    } catch (Exception ignored) {}
                    Bucket b = Bucket.builder()
                            .name(bucketName)
                            .userId(userId)
                            .accountId(accountId)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return bucketRepository.save(b);
                });

        byte[] payloadBytes;
        try {
            payloadBytes = request.getInputStream().readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read payload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        String md5Hex = DigestUtils.md5DigestAsHex(payloadBytes);
        String contentType = request.getContentType() != null
                ? request.getContentType()
                : mimeTypeResolver.resolve(normalizedKey);

        String localPath;
        try {
            localPath = storageService.writeObject(userId, bucketName, normalizedKey, new ByteArrayInputStream(payloadBytes));
        } catch (Exception e) {
            log.error("Failed to write object: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey)
                .orElse(StorageObject.builder()
                        .bucketId(bucket.getId())
                        .objectKey(normalizedKey)
                        .build());

        obj.setSizeBytes(payloadBytes.length);
        obj.setContentType(contentType);
        obj.setEtag(md5Hex);
        obj.setLocalPath(localPath);
        obj.setLastModified(LocalDateTime.now());
        objectRepository.save(obj);

        // Update bucket metadata
        bucket.setTotalSizeBytes(bucket.getTotalSizeBytes() + payloadBytes.length);
        bucket.setObjectCount(bucket.getObjectCount() + 1);
        bucket.setLastAccessed(LocalDateTime.now());
        bucketRepository.save(bucket);

        auditService.recordSuccess(principal.getUsername(), "S3", "PutObject", bucketName + "/" + normalizedKey);

        // Trigger S3 Lambda events
        try {
            s3TriggerDispatcher.dispatchUploadEvent(bucketName, normalizedKey, payloadBytes.length, md5Hex, userId, accountId);
        } catch (Exception ex) {
            log.warn("S3 trigger dispatch failed: {}", ex.getMessage());
        }
        eventPublisher.publishEvent(new S3ObjectCreatedEvent(this, bucketName, normalizedKey, (long) payloadBytes.length, md5Hex, userId, accountId));

        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", "\"" + md5Hex + "\"");
        return new ResponseEntity<>(headers, HttpStatus.OK);
    }

    /**
     * Get object from bucket (GET /s3/{bucketName}/{key}).
     */
    @GetMapping("/{bucketName}/{*objectKey}")
    @Operation(summary = "AWS S3 GetObject wire protocol with RFC 7233 byte-range partial content streaming")
    public ResponseEntity<InputStreamResource> getObject(
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        final String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        Bucket bucket = bucketRepository.findByName(bucketName).orElse(null);
        if (bucket == null) {
            return ResponseEntity.notFound().build();
        }

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey).orElse(null);
        if (obj == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            long totalSize = obj.getSizeBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", "\"" + obj.getEtag() + "\"");
            headers.set("Accept-Ranges", "bytes");
            headers.setContentType(MediaType.parseMediaType(obj.getContentType() != null ? obj.getContentType() : "application/octet-stream"));

            // RFC 7233 Range streaming (e.g. bytes=0-499 or bytes=500-)
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String rangeSpec = rangeHeader.substring(6).trim();
                long start = 0;
                long end = totalSize > 0 ? totalSize - 1 : 0;

                int dashIdx = rangeSpec.indexOf('-');
                if (dashIdx != -1) {
                    String startStr = rangeSpec.substring(0, dashIdx).trim();
                    String endStr = rangeSpec.substring(dashIdx + 1).trim();
                    if (!startStr.isEmpty()) {
                        start = Long.parseLong(startStr);
                    }
                    if (!endStr.isEmpty()) {
                        end = Long.parseLong(endStr);
                    }
                }

                start = Math.max(0, Math.min(start, totalSize > 0 ? totalSize - 1 : 0));
                end = Math.max(start, Math.min(end, totalSize > 0 ? totalSize - 1 : 0));
                long rangeLength = totalSize > 0 ? (end - start + 1) : 0;

                InputStream rangeStream = storageService.openObjectRangeStream(obj.getLocalPath(), start, rangeLength);
                headers.setContentLength(rangeLength);
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
                return new ResponseEntity<>(new InputStreamResource(rangeStream), headers, HttpStatus.PARTIAL_CONTENT);
            }

            // Full object streaming
            InputStream stream = storageService.openObjectStream(obj.getLocalPath());
            headers.setContentLength(totalSize);
            return new ResponseEntity<>(new InputStreamResource(stream), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Failed to open object stream: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Head object in bucket (HEAD /s3/{bucketName}/{key}).
     */
    @RequestMapping(value = "/{bucketName}/{*objectKey}", method = RequestMethod.HEAD)
    @Operation(summary = "AWS S3 HeadObject wire protocol")
    public ResponseEntity<Void> headObject(
            @PathVariable String bucketName,
            @PathVariable String objectKey) {

        final String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        Bucket bucket = bucketRepository.findByName(bucketName).orElse(null);
        if (bucket == null) return ResponseEntity.notFound().build();

        StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey).orElse(null);
        if (obj == null) return ResponseEntity.notFound().build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", "\"" + obj.getEtag() + "\"");
        headers.setContentLength(obj.getSizeBytes());
        headers.setContentType(MediaType.parseMediaType(obj.getContentType() != null ? obj.getContentType() : "application/octet-stream"));
        return new ResponseEntity<>(headers, HttpStatus.OK);
    }

    /**
     * Delete object from bucket (DELETE /s3/{bucketName}/{key}).
     */
    @DeleteMapping("/{bucketName}/{*objectKey}")
    @Transactional
    @Operation(summary = "AWS S3 DeleteObject wire protocol")
    public ResponseEntity<Void> deleteObject(
            @PathVariable String bucketName,
            @PathVariable String objectKey) {

        final String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        Bucket bucket = bucketRepository.findByName(bucketName).orElse(null);
        if (bucket != null) {
            StorageObject obj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), normalizedKey).orElse(null);
            if (obj != null) {
                storageService.deleteObject(obj.getLocalPath());
                objectRepository.delete(obj);
                bucket.setTotalSizeBytes(Math.max(0L, bucket.getTotalSizeBytes() - obj.getSizeBytes()));
                bucket.setObjectCount(Math.max(0L, bucket.getObjectCount() - 1));
                bucketRepository.save(bucket);
                eventPublisher.publishEvent(new S3ObjectDeletedEvent(this, bucketName, normalizedKey, bucket.getUserId(), bucket.getAccountId()));
                auditService.recordSuccess(SecurityUtils.getAuthenticatedUsername(), "S3", "DeleteObject", bucketName + "/" + normalizedKey);
            }
        }
        return ResponseEntity.noContent().build();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
