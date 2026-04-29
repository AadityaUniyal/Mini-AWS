package com.minicloud.api;

import com.minicloud.api.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import javax.swing.*;

/**
 * MiniCloud — Spring Boot Desktop Application.
 *
 * <p>Pure desktop mode with Java Swing UI:</p>
 * <ul>
 *   <li>NO WEB SERVER - No HTTP, no Tomcat, no browser</li>
 *   <li>NO LOCALHOST - Pure desktop Java application</li>
 *   <li>MySQL database connection ONLY (all schema in MySQL Workbench)</li>
 *   <li>Swing UI with integrated console window</li>
 * </ul>
 *
 * <p>Startup sequence:</p>
 * <ol>
 *   <li>Spring Boot initializes all beans, JPA, database connection</li>
 *   <li>MainWindow Swing component is created</li>
 *   <li>Swing window opens on the Event Dispatch Thread</li>
 *   <li>User interacts with desktop UI (no browser needed)</li>
 * </ol>
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@EnableScheduling
@EnableJpaAuditing
public class MiniCloudApiApplication {

    private static final Logger log = LoggerFactory.getLogger(MiniCloudApiApplication.class);

    public static void main(String[] args) {
        log.info("Starting MiniCloud Desktop Application...");

        // Set headless to false to allow Swing GUI
        System.setProperty("java.awt.headless", "false");
        
        // Force UTF-8 encoding for Windows compatibility
        System.setProperty("file.encoding", "UTF-8");

        // Start Spring Boot application (no web server)
        ConfigurableApplicationContext ctx =
            SpringApplication.run(MiniCloudApiApplication.class, args);

        // Launch Swing UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Set FlatLaf dark theme if available
                try {
                    UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
                } catch (Exception e) {
                    log.warn("FlatLaf not available, using system look and feel");
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
                
                // Get MainWindow bean from Spring context and show it
                MainWindow window = ctx.getBean(MainWindow.class);
                window.setVisible(true);
                
                log.info("MiniCloud Desktop UI launched successfully");
                window.log("Application started - Spring Boot context initialized");
                window.log("Database connection established");
                
            } catch (Exception e) {
                log.error("Failed to launch Swing UI", e);
                JOptionPane.showMessageDialog(null,
                    "Failed to launch UI: " + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
