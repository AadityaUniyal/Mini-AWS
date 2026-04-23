package com.minicloud.api;

import com.minicloud.api.config.StartupMode;
import com.minicloud.api.config.StartupModeResolver;
import com.minicloud.api.ui.SwingLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * MiniCloud — Spring Boot Application with dual-mode startup.
 *
 * <p>Supports two startup modes:</p>
 * <ul>
 *   <li><b>WEB</b> (default): Pure web service mode - runs as HTTP REST API without desktop UI</li>
 *   <li><b>DESKTOP</b>: Desktop UI mode - launches Swing interface with embedded web service</li>
 * </ul>
 *
 * <p>Startup sequence (WEB mode):</p>
 * <ol>
 *   <li>Resolve startup mode from CLI args or environment variables</li>
 *   <li>Set java.awt.headless property to true</li>
 *   <li>Spring Boot initializes all beans, JPA, security, REST endpoints</li>
 *   <li>Log startup URLs (H2 Console, Swagger UI, Health Check)</li>
 * </ol>
 *
 * <p>Startup sequence (DESKTOP mode):</p>
 * <ol>
 *   <li>Resolve startup mode from CLI args or environment variables</li>
 *   <li>Set java.awt.headless property to false</li>
 *   <li>Spring Boot initializes all beans, JPA, security, REST endpoints</li>
 *   <li>SwingLauncher is invoked with the ApplicationContext</li>
 *   <li>Swing shows a splash screen while the backend warms up</li>
 *   <li>Login dialog appears → main AWS-styled dashboard window opens</li>
 * </ol>
 *
 * <p>Validates Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 10.6</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class MiniCloudApiApplication {

    private static final Logger log = LoggerFactory.getLogger(MiniCloudApiApplication.class);

    public static void main(String[] args) {
        // Resolve startup mode from CLI args or environment variables
        StartupMode mode = StartupModeResolver.resolveMode(args);
        log.info("Starting MiniCloud in {} mode", mode);

        // Set headless property based on resolved mode
        // WEB mode: headless=true (no GUI components)
        // DESKTOP mode: headless=false (Swing windows can open)
        System.setProperty("java.awt.headless", String.valueOf(mode == StartupMode.WEB));

        // Start Spring Boot application
        ConfigurableApplicationContext ctx =
            SpringApplication.run(MiniCloudApiApplication.class, args);

        // Launch UI if in desktop mode, otherwise log web service URLs
        if (mode == StartupMode.DESKTOP) {
            SwingLauncher.launch(ctx);
        } else {
            int port = getServerPort(ctx);
            log.info("MiniCloud web service started successfully");
            log.info("H2 Console: http://localhost:{}/h2-console", port);
            log.info("Swagger UI: http://localhost:{}/swagger-ui.html", port);
            log.info("Health Check: http://localhost:{}/actuator/health", port);
        }
    }

    /**
     * Retrieves the server port from the application context.
     * 
     * <p>Defaults to 8080 if not configured.</p>
     * 
     * @param ctx The Spring application context
     * @return The configured server port, or 8080 if not set
     */
    private static int getServerPort(ApplicationContext ctx) {
        return ctx.getEnvironment().getProperty("server.port", Integer.class, 8080);
    }
}
