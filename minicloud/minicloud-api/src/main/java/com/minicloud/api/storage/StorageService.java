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

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Path.of(basePath));
            // Also ensure the legacy s3 dir exists for backward compat
            Files.createDirectories(Path.of("./minicloud-data/s3"));
            log.info("Storage base path ready: {}", Path.of(basePath).toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create base storage directory: {}", e.getMessage());
        }
    }

    public void createBucketDirectory(UUID userId, String bucketName) throws IOException {
        Path path = Path.of(basePath, userId.toString(), bucketName);
        Files.createDirectories(path);
        log.info("Created bucket directory: {}", path);
    }

    public void deleteBucketDirectory(UUID userId, String bucketName) {
        Path path = Path.of(basePath, userId.toString(), bucketName);
        deleteRecursive(path.toFile());
        log.info("Deleted bucket directory: {}", path);
    }

    public String writeObject(UUID userId, String bucketName, String objectKey, InputStream content) throws IOException {
        Path dir = Path.of(basePath, userId.toString(), bucketName);
        Files.createDirectories(dir);

        Path filePath = dir.resolve(objectKey);
        // Ensure parent directories for nested keys (e.g. folder/file.txt)
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }

        Files.copy(content, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Wrote object: {}", filePath);
        return filePath.toString();
    }

    public InputStream readObjectFromDisk(String localPath) throws FileNotFoundException {
        return new FileInputStream(localPath);
    }

    public InputStream readObject(byte[] content) {
        return new ByteArrayInputStream(content);
    }

    public void deleteObject(String localPath) {
        if (localPath != null) {
            try {
                Files.deleteIfExists(Path.of(localPath));
            } catch (IOException e) {
                log.warn("Failed to delete local file: {}", localPath);
            }
        }
    }

    public boolean isBucketEmpty(UUID userId, String bucketName) {
        Path path = Path.of(basePath, userId.toString(), bucketName);
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
