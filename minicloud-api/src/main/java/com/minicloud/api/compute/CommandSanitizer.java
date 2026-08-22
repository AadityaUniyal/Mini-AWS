package com.minicloud.api.compute;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Pattern;

/**
 * CommandSanitizer — Utility to clean and sanitize commands to prevent remote code injection attacks.
 */
@Slf4j
public class CommandSanitizer {

    // Pattern to catch shell injection characters: ;, &, |, \n, \r, `, $, >, <, %, ^
    private static final Pattern INJECTION_PATTERN = Pattern.compile("[;\\&|\\`$><\\\\%\\^\\n\\r\\t]");

    /**
     * Checks if the command is safe to execute.
     */
    public static String sanitize(String command) {
        if (command == null) {
            return "";
        }
        
        String trimmed = command.trim();
        
        if (INJECTION_PATTERN.matcher(trimmed).find()) {
            log.warn("Blocked potential command injection payload: {}", trimmed);
            throw new IllegalArgumentException("Command contains prohibited shell characters (;, &, |, `, $, newlines)");
        }
        
        return trimmed;
    }
}
