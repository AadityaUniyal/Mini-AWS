package com.minicloud.api.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    @Value("${minicloud.storage.base-path:./minicloud-data/s3}")
    private String basePath;

    private Path storageRoot;

    @PostConstruct
    public void init() {
        try {
            storageRoot = Path.of(basePath).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
            Files.createDirectories(Path.of("./minicloud-data/s3").toAbsolutePath().normalize());
            log.info("Storage base path ready: {}", storageRoot);
        } catch (IOException e) {
            log.error("Failed to create base storage directory: {}", e.getMessage());
        }
    }

    private Path resolveSafePath(String... components) {
        Path resolved = storageRoot;
        for (String c : components) {
            resolved = resolved.resolve(c);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new SecurityException("Access denied: Path traversal attempted (" + resolved + ")");
        }
        return resolved;
    }

    public void createBucketDirectory(String accountId, String bucketName) throws IOException {
        Path path = resolveSafePath(accountId != null ? accountId : "default", bucketName);
        Files.createDirectories(path);
        log.info("Created bucket directory: {}", path);
    }

    public void createBucketDirectory(UUID userId, String bucketName) throws IOException {
        Path path = resolveSafePath(userId != null ? userId.toString() : "default", bucketName);
        Files.createDirectories(path);
        log.info("Created bucket directory: {}", path);
    }

    public void deleteBucketDirectory(UUID userId, String bucketName) {
        Path path = resolveSafePath(userId != null ? userId.toString() : "default", bucketName);
        deleteRecursive(path.toFile());
        log.info("Deleted bucket directory: {}", path);
    }

    public void deleteBucketDirectory(String accountId, String bucketName) {
        Path path = resolveSafePath(accountId != null ? accountId : "default", bucketName);
        deleteRecursive(path.toFile());
        log.info("Deleted bucket directory: {}", path);
    }

    public String writeObject(UUID userId, String bucketName, String objectKey, InputStream content) throws IOException {
        String owner = userId != null ? userId.toString() : "default";
        Path dir = resolveSafePath(owner, bucketName);
        Files.createDirectories(dir);

        Path filePath = dir.resolve(objectKey).normalize();
        if (!filePath.startsWith(dir)) {
            throw new SecurityException("Invalid object key: path traversal detected");
        }
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }

        Files.copy(content, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Wrote object: {}", filePath);
        return filePath.toString();
    }

    public byte[] readObjectFromDisk(String localPath) throws IOException {
        Path path = Path.of(localPath).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot)) {
            throw new SecurityException("Invalid path: access outside storage root");
        }
        try (InputStream is = new FileInputStream(path.toFile())) {
            return is.readAllBytes();
        }
    }

    public InputStream openObjectStream(String localPath) throws IOException {
        Path path = Path.of(localPath).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot)) {
            throw new SecurityException("Invalid path: access outside storage root");
        }
        return new BufferedInputStream(new FileInputStream(path.toFile()));
    }

    public InputStream readObject(byte[] content) {
        return new ByteArrayInputStream(content);
    }

    public void deleteObject(String localPath) {
        if (localPath != null) {
            try {
                Path path = Path.of(localPath).toAbsolutePath().normalize();
                if (path.startsWith(storageRoot)) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException e) {
                log.warn("Failed to delete local file: {}", localPath);
            }
        }
    }

    public boolean isBucketEmpty(UUID userId, String bucketName) {
        Path path = resolveSafePath(userId != null ? userId.toString() : "default", bucketName);
        File dir = path.toFile();
        if (!dir.exists()) return true;
        String[] children = dir.list();
        return children == null || children.length == 0;
    }

    public boolean isBucketEmpty(String accountId, String bucketName) {
        Path path = resolveSafePath(accountId != null ? accountId : "default", bucketName);
        File dir = path.toFile();
        if (!dir.exists()) return true;
        String[] children = dir.list();
        return children == null || children.length == 0;
    }

    public byte[] readAllBytes(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }

    private void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
