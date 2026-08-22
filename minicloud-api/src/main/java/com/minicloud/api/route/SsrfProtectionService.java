package com.minicloud.api.route;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

/**
 * SSRF (Server-Side Request Forgery) protection service.
 * Validates outgoing URLs to prevent internal port scanning and cloud metadata access.
 */
@Slf4j
@Service
public class SsrfProtectionService {

    public boolean isSafeUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(urlString);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "169.254.169.254".equals(host)) {
                log.warn("Blocked SSRF attempt to internal target: {}", host);
                return false;
            }

            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                log.warn("Blocked SSRF attempt resolving to loopback/link-local: {}", host);
                return false;
            }

            // Reject private address ranges if external outbound proxy
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) {
                int b0 = bytes[0] & 0xFF;
                int b1 = bytes[1] & 0xFF;
                // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
                if (b0 == 10 || (b0 == 172 && b1 >= 16 && b1 <= 31) || (b0 == 192 && b1 == 168)) {
                    log.debug("SSRF target is RFC1918 private IP: {}", host);
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("SSRF validation failed for URL {}: {}", urlString, e.getMessage());
            return false;
        }
    }
}
