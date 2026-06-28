package com.minicloud.api.storage;

import com.minicloud.api.domain.*;
import com.minicloud.api.storage.MimeTypeResolver;
import com.minicloud.api.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/site")
@RequiredArgsConstructor
@Tag(name = "MiniCDN", description = "Static website hosting from S3 buckets")
public class WebsiteController {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;
    private final StorageService storageService;
    private final MimeTypeResolver mimeTypeResolver;

    @PutMapping("/{bucketName}/config")
    @Operation(summary = "Enable or update static website config on a bucket")
    public ResponseEntity<String> configureWebsite(
            @PathVariable String bucketName,
            @RequestParam(defaultValue = "index.html") String indexDoc,
            @RequestParam(defaultValue = "error.html") String errorDoc,
            @RequestParam(defaultValue = "false") boolean spaMode,
            @RequestParam(required = false) UUID userId) {

        Optional<Bucket> opt = (userId != null)
                ? bucketRepository.findByUserIdAndName(userId, bucketName)
                : bucketRepository.findByName(bucketName);

        Bucket bucket = opt.orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketName));

        bucket.setWebsiteEnabled(true);
        bucket.setIndexDocument(indexDoc);
        bucket.setErrorDocument(errorDoc);
        bucket.setSpaMode(spaMode);
        bucketRepository.save(bucket);

        return ResponseEntity.ok("Website enabled!");
    }

    @GetMapping(value = {"/{bucketName}", "/{bucketName}/", "/{bucketName}/{*path}"})
    @Operation(summary = "Serve a static file from a website-enabled bucket (public)")
    public ResponseEntity<byte[]> serveFile(
            @PathVariable String bucketName,
            @PathVariable(required = false) String path) {

        Bucket bucket = bucketRepository.findByName(bucketName).orElse(null);
        if (bucket == null || !bucket.isWebsiteEnabled()) {
            return errorPage("404 — Not Found", HttpStatus.NOT_FOUND);
        }

        String requestedKey = (path == null || path.isBlank() || path.equals("/"))
                ? bucket.getIndexDocument()
                : (path.startsWith("/") ? path.substring(1) : path);

        if (requestedKey.endsWith("/")) requestedKey += bucket.getIndexDocument();

        Optional<StorageObject> objOpt = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), requestedKey);
        
        if (objOpt.isEmpty() && bucket.isSpaMode()) {
            objOpt = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), bucket.getIndexDocument());
        }

        if (objOpt.isEmpty()) {
            Optional<StorageObject> errObj = objectRepository.findByBucketIdAndObjectKey(bucket.getId(), bucket.getErrorDocument());
            if (errObj.isPresent()) return serveObject(errObj.get(), HttpStatus.NOT_FOUND);
            return errorPage("404 — File not found", HttpStatus.NOT_FOUND);
        }

        return serveObject(objOpt.get(), HttpStatus.OK);
    }

    private ResponseEntity<byte[]> serveObject(StorageObject obj, HttpStatus status) {
        try {
            byte[] data = obj.getContent() != null ? obj.getContent() 
                    : storageService.readAllBytes(storageService.readObjectFromDisk(obj.getLocalPath()));
            
            return ResponseEntity.status(status)
                    .header("Content-Type", mimeTypeResolver.resolve(obj.getObjectKey()))
                    .body(data);
        } catch (Exception e) {
            return errorPage("500 — Internal Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<byte[]> errorPage(String message, HttpStatus status) {
        String html = "<html><body><h1>" + status.value() + "</h1><p>" + message + "</p></body></html>";
        return ResponseEntity.status(status).header("Content-Type", "text/html").body(html.getBytes());
    }
}
