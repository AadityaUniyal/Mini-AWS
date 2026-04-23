package com.minicloud.api.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility using JJWT 0.12.x API.
 *
 * Key JJWT 0.12 changes from 0.11:
 *  - Jwts.parserBuilder()     → Jwts.parser()
 *  - .setSigningKey()         → .verifyWith(SecretKey)
 *  - .parseClaimsJws()        → .parseSignedClaims()
 *  - .getBody()               → .getPayload()
 *  - Jwts.builder().setClaims → .claims(map)
 *  - .setSubject()            → .subject()
 *  - .setIssuedAt()           → .issuedAt()
 *  - .setExpiration()         → .expiration()
 *  - .signWith(key, alg)      → .signWith(key)  ← alg inferred from key type
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${minicloud.jwt.secret}")
    private String secret;

    @Value("${minicloud.jwt.expiry-ms}")
    private long expiryMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String role, String userId, String accountId, boolean rootUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("accountId", accountId);
        claims.put("rootUser", rootUser);

        return Jwts.builder()
                .claims(claims)                                           // 0.12.x: claims(map)
                .subject(username)                                        // 0.12.x: subject()
                .issuedAt(new Date())                                     // 0.12.x: issuedAt()
                .expiration(new Date(System.currentTimeMillis() + expiryMs)) // 0.12.x: expiration()
                .signWith(getSigningKey())                                 // 0.12.x: alg inferred (HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()                                                  // 0.12.x: parser()
                    .verifyWith(getSigningKey())                           // 0.12.x: verifyWith(SecretKey)
                    .build()
                    .parseSignedClaims(token);                            // 0.12.x: parseSignedClaims()
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT invalid: {}", e.getMessage());
        }
        return false;
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return (String) getClaims(token).get("role");
    }

    public String extractUserId(String token) {
        return (String) getClaims(token).get("userId");
    }

    public String extractAccountId(String token) {
        return (String) getClaims(token).get("accountId");
    }

    public boolean extractIsRoot(String token) {
        Object isRoot = getClaims(token).get("rootUser");
        return isRoot != null && (boolean) isRoot;
    }

    public long getExpiryMs() {
        return expiryMs;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()                                               // 0.12.x: parser()
                .verifyWith(getSigningKey())                               // 0.12.x: verifyWith(SecretKey)
                .build()
                .parseSignedClaims(token)                                 // 0.12.x: parseSignedClaims()
                .getPayload();                                            // 0.12.x: getPayload() (was getBody())
    }
}
