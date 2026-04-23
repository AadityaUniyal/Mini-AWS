package com.minicloud.api.config;

/**
 * Resolves the startup mode from:
 *   1. CLI argument:      --mode=WEB  or  --mode=DESKTOP
 *   2. Environment var:   MINICLOUD_MODE=WEB  or  MINICLOUD_MODE=DESKTOP
 *   3. Default:           WEB
 */
public class StartupModeResolver {
    
    public static StartupMode resolveMode(String[] args) {
        // Check CLI arguments first
        if (args != null) {
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--mode=DESKTOP") || arg.equalsIgnoreCase("desktop")) {
                    return StartupMode.DESKTOP;
                }
                if (arg.equalsIgnoreCase("--mode=WEB") || arg.equalsIgnoreCase("web")) {
                    return StartupMode.WEB;
                }
            }
        }
        
        // Check environment variable
        String envMode = System.getenv("MINICLOUD_MODE");
        if (envMode != null) {
            try {
                return StartupMode.valueOf(envMode.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Invalid env value, fall through to default
            }
        }
        
        // Default mode is WEB
        return StartupMode.WEB;
    }
}