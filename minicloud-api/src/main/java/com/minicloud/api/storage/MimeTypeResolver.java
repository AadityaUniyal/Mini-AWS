package com.minicloud.api.storage;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MimeTypeResolver — Lightweight mapping for MiniCDN static hosting.
 */
@Component
public class MimeTypeResolver {

    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();

    static {
        EXT_TO_MIME.put("html", "text/html; charset=UTF-8");
        EXT_TO_MIME.put("htm",  "text/html; charset=UTF-8");
        EXT_TO_MIME.put("css",  "text/css; charset=UTF-8");
        EXT_TO_MIME.put("js",   "application/javascript; charset=UTF-8");
        EXT_TO_MIME.put("json", "application/json; charset=UTF-8");
        EXT_TO_MIME.put("png",  "image/png");
        EXT_TO_MIME.put("jpg",  "image/jpeg");
        EXT_TO_MIME.put("jpeg", "image/jpeg");
        EXT_TO_MIME.put("gif",  "image/gif");
        EXT_TO_MIME.put("svg",  "image/svg+xml");
        EXT_TO_MIME.put("ico",  "image/x-icon");
        EXT_TO_MIME.put("txt",  "text/plain; charset=UTF-8");
        EXT_TO_MIME.put("pdf",  "application/pdf");
        EXT_TO_MIME.put("zip",  "application/zip");
    }

    public String resolve(String filename) {
        if (filename == null) return "application/octet-stream";
        int dot = filename.lastIndexOf('.');
        if (dot == -1) return "application/octet-stream";
        
        String ext = filename.substring(dot + 1).toLowerCase();
        return EXT_TO_MIME.getOrDefault(ext, "application/octet-stream");
    }
}
