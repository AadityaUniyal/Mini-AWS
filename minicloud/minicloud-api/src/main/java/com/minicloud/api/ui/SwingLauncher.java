package com.minicloud.api.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import org.springframework.context.ApplicationContext;

import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.LoginDialog;
import com.minicloud.api.ui.MainWindow;

import javax.swing.*;
import java.awt.*;

/**
 * Launched by MiniCloudApiApplication after Spring Boot finishes starting.
 * Sets up FlatLaf theming and opens the main application window.
 */
public class SwingLauncher {

    // AWS dark palette constants
    public static final Color AWS_NAVY       = new Color(0x23, 0x2F, 0x3E);
    public static final Color AWS_ORANGE     = new Color(0xFF, 0x99, 0x00);
    public static final Color AWS_DARK_BG    = new Color(0x16, 0x1E, 0x2D);
    public static final Color AWS_PANEL_BG   = new Color(0x1A, 0x24, 0x2F);
    public static final Color AWS_BORDER     = new Color(0x31, 0x3D, 0x4E);
    public static final Color AWS_TEXT       = new Color(0xD1, 0xD5, 0xDA);
    public static final Color AWS_TEXT_DIM   = new Color(0x8B, 0x94, 0x9E);
    public static final Color AWS_GREEN      = new Color(0x1E, 0xBE, 0x61);
    public static final Color AWS_RED        = new Color(0xE5, 0x53, 0x4B);
    public static final Color AWS_BLUE       = new Color(0x3B, 0x82, 0xF6);

    public static void launch(ApplicationContext ctx) {
        try {
            // 1. Setup Theme (Main thread)
            FlatDarkLaf.setup();
            setupUIManager();

            // 2. Wait for Backend (Main thread - blocking here is okay)
            showSplashAndWaitForBackend();

            // 3. Launch UI (EDT)
            SwingUtilities.invokeLater(() -> {
                try {
                    LoginDialog login = new LoginDialog(null);
                    login.setVisible(true);
                    if (!login.isAuthenticated()) {
                        System.exit(0);
                    }

                    MainWindow window = new MainWindow();
                    window.setVisible(true);
                    window.refreshAll();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupUIManager() {
        UIManager.put("Panel.background",              AWS_PANEL_BG);
        UIManager.put("ScrollPane.background",         AWS_PANEL_BG);
        UIManager.put("Table.background",              AWS_DARK_BG);
        UIManager.put("Table.foreground",              AWS_TEXT);
        UIManager.put("Table.gridColor",               AWS_BORDER);
        UIManager.put("Table.selectionBackground",     AWS_NAVY);
        UIManager.put("Table.selectionForeground",     Color.WHITE);
        UIManager.put("TableHeader.background",        AWS_NAVY);
        UIManager.put("TableHeader.foreground",        AWS_ORANGE);
        UIManager.put("Button.background",             AWS_ORANGE);
        UIManager.put("Button.foreground",             Color.BLACK);
        UIManager.put("TextField.background",          AWS_DARK_BG);
        UIManager.put("TextField.foreground",          AWS_TEXT);
        UIManager.put("PasswordField.background",      AWS_DARK_BG);
        UIManager.put("PasswordField.foreground",      AWS_TEXT);
        UIManager.put("Label.foreground",              AWS_TEXT);
        UIManager.put("TabbedPane.background",         AWS_NAVY);
        UIManager.put("TabbedPane.selectedBackground", AWS_PANEL_BG);
        UIManager.put("List.background",               AWS_DARK_BG);
        UIManager.put("ComboBox.background",           AWS_DARK_BG);
        UIManager.put("ComboBox.foreground",           AWS_TEXT);
    }

    private static void showSplashAndWaitForBackend() {
        JWindow splash = new JWindow();
        JPanel p = new JPanel(new BorderLayout(16, 16));
        p.setBackground(AWS_NAVY);
        p.setBorder(BorderFactory.createLineBorder(AWS_ORANGE, 2));

        JLabel logo = new JLabel("☁  MiniCloud", SwingConstants.CENTER);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 28));
        logo.setForeground(AWS_ORANGE);
        logo.setBorder(BorderFactory.createEmptyBorder(30, 40, 8, 40));

        JLabel status = new JLabel("Starting Spring Boot backend...", SwingConstants.CENTER);
        status.setForeground(AWS_TEXT_DIM);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setForeground(AWS_ORANGE);
        bar.setBackground(AWS_DARK_BG);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 30, 20, 30));

        p.add(logo,   BorderLayout.NORTH);
        p.add(status, BorderLayout.CENTER);
        p.add(bar,    BorderLayout.SOUTH);

        splash.setContentPane(p);
        splash.setSize(380, 160);
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        // Poll for backend readiness
        int attempts = 0;
        while (!ApiClient.isServerUp() && attempts < 40) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            attempts++;
        }

        splash.dispose();
    }
}
