package com.minicloud.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Central HTTP communication class.
 * Wraps Java 11+ HttpClient.
 * Stores JWT token in memory after login.
 * Auto-attaches "Authorization: Bearer <token>" header.
 */
public class ApiClient {

    private static final String BASE_URL = "http://localhost:8080";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private String token;
    private String username;
    private String role;

    // ─────────────────────────── Token ───────────────────────────

    public void setToken(String token) { this.token = token; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(String role) { this.role = role; }
    public String getToken()    { return token; }
    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public boolean isLoggedIn() { return token != null && !token.isEmpty(); }
    public void clearToken()    { token = null; username = null; role = null; }

    // ─────────────────────────── HTTP Methods ───────────────────────────

    public JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .GET()
                .build();
        return execute(request);
    }

    public JsonNode post(String path, Object body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = buildRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request);
    }

    public JsonNode postForm(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return execute(request);
    }

    public JsonNode delete(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .DELETE()
                .build();
        return execute(request);
    }

    /**
     * Multipart file upload.
     */
    public String upload(String path, java.io.File file) throws IOException, InterruptedException {
        String boundary = "MiniCloud-" + System.currentTimeMillis();
        byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());

        String contentType = java.net.URLConnection.guessContentTypeFromName(file.getName());
        if (contentType == null) contentType = "application/octet-stream";

        byte[] header = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Type: " + contentType + "\r\n\r\n").getBytes();
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] body = new byte[header.length + fileBytes.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(fileBytes, 0, body, header.length, fileBytes.length);
        System.arraycopy(footer, 0, body, header.length + fileBytes.length, footer.length);

        HttpRequest request = buildRequest(path)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    /**
     * Download file to raw bytes.
     */
    public byte[] download(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    /**
     * Legacy helper to download directly to a file.
     */
    public void downloadFile(String path, java.io.File destination) throws IOException, InterruptedException {
        java.nio.file.Files.write(destination.toPath(), download(path));
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private HttpRequest.Builder buildRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(TIMEOUT);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private JsonNode execute(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    /**
     * Helper: extract the "data" node from an ApiResponse wrapper.
     */
    public JsonNode getData(JsonNode response) {
        if (response.has("data")) return response.get("data");
        return response;
    }
}
