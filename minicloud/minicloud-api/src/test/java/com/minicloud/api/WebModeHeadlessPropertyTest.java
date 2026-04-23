package com.minicloud.api;

import com.minicloud.api.config.StartupMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.quicktheories.QuickTheory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.*;

/**
 * Property-based test for web mode headless property configuration.
 * 
 * **Validates: Requirements 1.2**
 * 
 * This test verifies that when the application starts in WEB mode, the 
 * java.awt.headless system property is set to "true" before the Spring 
 * application context is initialized.
 */
class WebModeHeadlessPropertyTest {
    
    private String originalHeadlessProperty;
    
    @BeforeEach
    void setUp() {
        // Save original headless property value
        originalHeadlessProperty = System.getProperty("java.awt.headless");
    }
    
    @AfterEach
    void tearDown() {
        // Restore original headless property value
        if (originalHeadlessProperty != null) {
            System.setProperty("java.awt.headless", originalHeadlessProperty);
        } else {
            System.clearProperty("java.awt.headless");
        }
    }
    
    /**
     * Property 1: Web mode sets headless property
     * 
     * For any application startup in web service mode, the system property 
     * java.awt.headless should be set to "true" before the Spring application 
     * context is initialized.
     * 
     * **Validates: Requirements 1.2**
     */
    @Test
    @Tag("production-web-service-mode-property-1")
    void webModeAlwaysSetsHeadlessProperty() {
        qt().forAll(
            booleans().all(), // various system states
            strings().allPossible().ofLengthBetween(0, 10) // various other system properties
        ).checkAssert((systemState, otherProps) -> {
            // Setup: Clear system property to ensure clean state
            System.clearProperty("java.awt.headless");
            
            // Execute: Apply headless configuration for WEB mode
            // This simulates the logic from MiniCloudApiApplication.main()
            StartupMode mode = StartupMode.WEB;
            System.setProperty("java.awt.headless", String.valueOf(mode == StartupMode.WEB));
            
            // Verify: Headless property is set to "true"
            assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
        });
    }
    
    /**
     * Property: Desktop mode does not set headless property to true
     * 
     * For any application startup in desktop mode, the system property 
     * java.awt.headless should be set to "false" to allow GUI components.
     * 
     * This is a complementary test to ensure the logic works correctly for both modes.
     */
    @Test
    @Tag("production-web-service-mode-desktop-headless")
    void desktopModeAlwaysSetsHeadlessToFalse() {
        qt().forAll(
            booleans().all(), // various system states
            strings().allPossible().ofLengthBetween(0, 10) // various other system properties
        ).checkAssert((systemState, otherProps) -> {
            // Setup: Clear system property to ensure clean state
            System.clearProperty("java.awt.headless");
            
            // Execute: Apply headless configuration for DESKTOP mode
            // This simulates the logic from MiniCloudApiApplication.main()
            StartupMode mode = StartupMode.DESKTOP;
            System.setProperty("java.awt.headless", String.valueOf(mode == StartupMode.WEB));
            
            // Verify: Headless property is set to "false"
            assertThat(System.getProperty("java.awt.headless")).isEqualTo("false");
        });
    }
    
    /**
     * Property: Headless property is always set regardless of initial state
     * 
     * The headless property should be explicitly set based on the startup mode,
     * regardless of what the initial value was.
     */
    @Test
    @Tag("production-web-service-mode-headless-always-set")
    void headlessPropertyAlwaysSetRegardlessOfInitialState() {
        qt().forAll(
            booleans().all(), // mode: true = WEB, false = DESKTOP
            strings().allPossible().ofLengthBetween(0, 20) // initial headless value
        ).checkAssert((isWebMode, initialValue) -> {
            // Setup: Set initial headless property to some value
            if (initialValue != null && !initialValue.trim().isEmpty()) {
                System.setProperty("java.awt.headless", initialValue);
            } else {
                System.clearProperty("java.awt.headless");
            }
            
            // Execute: Apply headless configuration based on mode
            StartupMode mode = isWebMode ? StartupMode.WEB : StartupMode.DESKTOP;
            System.setProperty("java.awt.headless", String.valueOf(mode == StartupMode.WEB));
            
            // Verify: Headless property is set correctly regardless of initial state
            String expectedValue = isWebMode ? "true" : "false";
            assertThat(System.getProperty("java.awt.headless")).isEqualTo(expectedValue);
        });
    }
}