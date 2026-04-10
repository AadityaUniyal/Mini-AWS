package com.minicloud.api.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StorageService {

    @Value("${minicloud.storage.base-path}")
    private String basePath;

    /**
     * Create directory for a new bucket:
     * {basePath}/{userId}/{bucketName}/
     */
    public void createBucketDirectory(UUID userId, String bucketName) throws IOException {
        Path bucketPath = getBucketPath(userId, bucketName);
        Files.createDirectories(bucketPath);
        log.info("Created bucket directory: {}", bucketPath);
    }

    /**
     * Write an uploaded file to local disk.
     * Returns the absolute path string for DB storage.
     */
    public String writeObject(UUID userId, String bucketName, String objectKey, InputStream inputStream) throws IOException {
        Path objectPath = getObjectPath(userId, bucketName, objectKey);
        Files.createDirectories(objectPath.getParent()); // ensure parent exists
        Files.copy(inputStream, objectPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Written object: {}", objectPath);
        return objectPath.toAbsolutePath().toString();
    }

    /**
     * Open an InputStream from a stored object.
     */
    public InputStream readObject(String localPath) throws IOException {
        return Files.newInputStream(Path.of(localPath));
    }

    /**
     * Delete a single object from disk.
     */
    public void deleteObject(String localPath) {
        try {
            Files.deleteIfExists(Path.of(localPath));
            log.info("Deleted object: {}", localPath);
        } catch (IOException e) {
            log.warn("Could not delete file: {} — {}", localPath, e.getMessage());
        }
    }

    /**
     * Delete the entire bucket directory recursively.
     */
    public void deleteBucketDirectory(UUID userId, String bucketName) {
        Path bucketPath = getBucketPath(userId, bucketName);
        deleteDirectoryRecursively(bucketPath);
    }

    /**
     * List all filenames in a bucket directory.
     */
    public List<String> listObjectKeys(UUID userId, String bucketName) throws IOException {
        Path bucketPath = getBucketPath(userId, bucketName);
        if (!Files.exists(bucketPath)) return List.of();
        try (var stream = Files.list(bucketPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    public long getObjectSize(String localPath) {
        try {
            return Files.size(Path.of(localPath));
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Check if a bucket directory is empty (no files or subdirectories).
     */
    public boolean isBucketEmpty(UUID userId, String bucketName) {
        Path bucketPath = getBucketPath(userId, bucketName);
        if (!Files.exists(bucketPath)) return true;
        try (var stream = Files.list(bucketPath)) {
            return !stream.findAny().isPresent();
        } catch (IOException e) {
            log.warn("Could not check if bucket is empty: {}", bucketPath);
            return false;
        }
    }

    private Path getBucketPath(UUID userId, String bucketName) {
        return Path.of(basePath, userId.toString(), bucketName);
    }

    private Path getObjectPath(UUID userId, String bucketName, String objectKey) {
        // AWS S3 uses '/' to simulate folders.
        // We allow '/' but sanitize other dangerous characters to prevent path traversal.
        String safeKey = objectKey.replaceAll("[:*?\"<>|]", "_");
        
        // Prevent ".." or absolute paths from escaping the bucket directory
        Path bucketPath = getBucketPath(userId, bucketName);
        Path objectPath = bucketPath.resolve(safeKey).normalize();
        
        if (!objectPath.startsWith(bucketPath)) {
            throw new IllegalArgumentException("Invalid object key (path traversal attempt): " + objectKey);
        }
        
        return objectPath;
    }

    private void deleteDirectoryRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir)
                 .sorted((a, b) -> b.compareTo(a)) // reverse order — files before dirs
                 .forEach(path -> {
                     try { Files.deleteIfExists(path); }
                     catch (IOException e) { log.warn("Could not delete: {}", path); }
                 });
        } catch (IOException e) {
            log.error("Failed to delete directory: {}", dir, e);
        }
    }
}
