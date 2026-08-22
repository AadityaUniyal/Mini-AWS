package com.minicloud.api;

import com.minicloud.api.config.StartupMode;
import com.minicloud.api.config.StartupModeResolver;
import com.minicloud.api.ui.SwingLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * MiniCloud — Spring Boot Cloud Platform Application.
 *
 * Runs both a REST API (embedded Tomcat) and a Swing desktop UI.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
public class MiniCloudApiApplication {

    private static final Logger log = LoggerFactory.getLogger(MiniCloudApiApplication.class);

    public static void main(String[] args) {
        log.info("Starting MiniCloud Cloud Platform...");

        // Resolve startup mode
        StartupMode mode = StartupModeResolver.resolveMode(args);
        
        boolean isHeadless = Boolean.getBoolean("java.awt.headless") || java.awt.GraphicsEnvironment.isHeadless();
        if (isHeadless && mode == StartupMode.DESKTOP) {
            log.warn("Headless environment detected. Automatically falling back from DESKTOP mode to WEB mode.");
            mode = StartupMode.WEB;
        }
        
        // Set headless conditionally based on mode (required for headless property tests)
        System.setProperty("java.awt.headless", String.valueOf(mode == StartupMode.WEB));
        
        // Force UTF-8 encoding for Windows compatibility
        System.setProperty("file.encoding", "UTF-8");

        // Start Spring Boot application (with embedded Tomcat web server)
        ConfigurableApplicationContext ctx =
            SpringApplication.run(MiniCloudApiApplication.class, args);

        // Run the Swing UI Launcher ONLY in DESKTOP mode
        if (mode == StartupMode.DESKTOP) {
            SwingLauncher.launch(ctx);
        } else {
            log.info("MiniCloud running in production WEB mode (GUI disabled).");
        }
    }
}
