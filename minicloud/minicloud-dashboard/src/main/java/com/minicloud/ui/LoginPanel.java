package com.minicloud.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Redesigned LoginPanel — AWS Management Console login style.
 */
public class LoginPanel extends JPanel {

    private final ApiClient apiClient;
    private final MainFrame mainFrame;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private MiniCloudButton loginButton;
    private JLabel statusLabel;

    public LoginPanel(ApiClient apiClient, MainFrame mainFrame) {
        this.apiClient = apiClient;
        this.mainFrame = mainFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY, 1),
            new EmptyBorder(40, 50, 40, 50)
        ));
        card.setPreferredSize(new Dimension(400, 500));

        // Header
        JLabel logo = new JLabel("☁ MiniCloud");
        logo.setFont(ThemeConstants.getFont(24, Font.BOLD));
        logo.setForeground(ThemeConstants.NAVY);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Sign in to MiniCloud Console");
        subtitleLabel.setFont(ThemeConstants.getFont(16, Font.BOLD));
        subtitleLabel.setForeground(ThemeConstants.TEXT_DARK);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(logo);
        card.add(Box.createVerticalStrut(20));
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(30));

        // Username
        card.add(createLabel("User name"));
        usernameField = new JTextField("admin");
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(15));

        // Password
        card.add(createLabel("Password"));
        passwordField = new JPasswordField("admin123");
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(25));

        // Login button
        loginButton = new MiniCloudButton("Sign in", MiniCloudButton.Type.PRIMARY);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        loginButton.addActionListener(e -> performLogin());
        card.add(loginButton);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(ThemeConstants.RED_TEXT);
        statusLabel.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(Box.createVerticalStrut(15));
        card.add(statusLabel);

        add(card);
    }

    private JLabel createLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(ThemeConstants.getFont(13, Font.BOLD));
        l.setForeground(ThemeConstants.TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Enter username and password");
            return;
        }

        loginButton.setEnabled(false);
        statusLabel.setText("Logging in...");

        new SwingWorker<Void, Void>() {
            private String error = null;

            @Override protected Void doInBackground() {
                try {
                    var body = new java.util.HashMap<String, String>();
                    body.put("username", username);
                    body.put("password", password);

                    JsonNode res = apiClient.post("/auth/login", body);
                    JsonNode data = apiClient.getData(res);

                    if (res.has("success") && !res.get("success").asBoolean()) {
                        error = res.has("message") ? res.get("message").asText() : "Failed";
                    } else {
                        apiClient.setToken(data.get("token").asText());
                        apiClient.setUsername(data.has("username") ? data.get("username").asText() : username);
                        apiClient.setRole(data.has("role") ? data.get("role").asText() : "ADMIN");
                    }
                } catch (Exception e) {
                    error = "Connection failed";
                }
                return null;
            }

            @Override protected void done() {
                loginButton.setEnabled(true);
                if (error != null) statusLabel.setText(error);
                else mainFrame.showDashboard(apiClient.getUsername());
            }
        }.execute();
    }
}
