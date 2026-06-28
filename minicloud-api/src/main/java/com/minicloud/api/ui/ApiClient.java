package com.minicloud.api.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Central HTTP client for all REST calls from the Swing UI.
 * Manages JWT token state and constructs authenticated requests.
 */
@Slf4j
public class ApiClient {

    private static final String BASE_URL = "http://localhost:8080";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static UserSession session = null;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── Auth ──────────────────────────────────────────────────────────────────

    public static boolean login(String type, String identifier, String username, String password) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("loginType", type);
            if ("ROOT".equals(type)) {
                body.put("email", identifier);
            } else {
                body.put("accountId", identifier);
                body.put("username", username);
            }
            body.put("password", password);

            String json = mapper.writeValueAsString(body);
            String response = postRaw("/api/v1/auth/login", json);
            
            JsonNode root = mapper.readTree(response);
            JsonNode data = root.get("data");
            
            session = new UserSession();
            session.setToken(data.get("token").asText());
            session.setUserId(data.get("userId") != null ? data.get("userId").asText() : "");
            session.setUsername(data.get("username").asText());
            session.setAccountId(data.get("accountId").asText());
            session.setRole(data.get("role").asText());
            session.setRootUser(data.get("rootUser").asBoolean());
            
            return true;
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return false;
        }
    }

    public static String register(String accountName, String email, String password) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("username", accountName);
            body.put("email", email);
            body.put("password", password);

            String json = mapper.writeValueAsString(body);
            String response = postRaw("/api/v1/auth/register", json);
            
            JsonNode root = mapper.readTree(response);
            JsonNode data = root.get("data");
            if (data != null && data.has("accountId")) return data.get("accountId").asText();
            if (data != null && data.isTextual()) return data.asText();
            return data != null ? data.toString() : null;
        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage());
            return null;
        }
    }

    public static void logout() {
        session = null;
    }

    public static boolean isLoggedIn() {
        return session != null && session.getToken() != null;
    }

    // ── Generic HTTP helpers ──────────────────────────────────────────────────

    public static JsonNode get(String path) throws Exception {
        HttpRequest req = authBuilder(path).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    public static JsonNode post(String path, Object body) throws Exception {
        return post(path, body, null);
    }

    public static JsonNode post(String path, Object body, Map<String, String> queryParams) throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "";
        
        StringBuilder url = new StringBuilder(BASE_URL + path);
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
        }

        HttpRequest req = authBuilder(url.toString().replace(BASE_URL, ""))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    private static String postRaw(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public static JsonNode delete(String path) throws Exception {
        HttpRequest req = authBuilder(path).DELETE().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.body() == null || resp.body().isBlank()) return mapper.createObjectNode();
        return mapper.readTree(resp.body());
    }

    public static JsonNode put(String path, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpRequest req = authBuilder(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    private static HttpRequest.Builder authBuilder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10));
        if (session != null && session.getToken() != null) b.header("Authorization", "Bearer " + session.getToken());
        return b;
    }

    public static boolean isServerUp() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/actuator/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static UserSession getSession() {
        return session;
    }

    // ── Session State ─────────────────────────────────────────────────────────

    @Data
    public static class UserSession {
        private String token;
        private String userId;
        private String username;
        private String accountId;
        private String role;
        private boolean isRootUser;
    }
}
