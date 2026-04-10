package com.minicloud.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.components.MiniCloudTable;
import com.minicloud.ui.components.StatusBadge;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import com.minicloud.ui.dialog.CreateUserDialog;

/**
 * Redesigned IAMPanel — AWS style users list + detail panel.
 */
public class IAMPanel extends JPanel {

    private final ApiClient apiClient;
    private DefaultTableModel tableModel;
    private MiniCloudTable userTable;
    private JPanel detailPanel;
    private JLabel detUsername, detRole, detCreated;

    public IAMPanel(ApiClient apiClient) {
        this.apiClient = apiClient;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeConstants.BG_LIGHT);
        setBorder(new EmptyBorder(20, 30, 20, 30));

        // ── Header ───────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel breadcrumbs = new JLabel("IAM > Users");
        breadcrumbs.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        breadcrumbs.setForeground(ThemeConstants.TEXT_MUTED);
        header.add(breadcrumbs, BorderLayout.NORTH);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("Users");
        title.setFont(ThemeConstants.getFont(20, Font.BOLD));
        titlePanel.add(title);
        header.add(titlePanel, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);
        MiniCloudButton createUserBtn = new MiniCloudButton("Create user", MiniCloudButton.Type.PRIMARY);
        createUserBtn.addActionListener(e -> showCreateUserDialog());
        toolbar.add(createUserBtn);
        
        toolbar.add(new MiniCloudButton("Delete",      MiniCloudButton.Type.OUTLINE));
        
        MiniCloudButton refreshBtn = new MiniCloudButton("↻", MiniCloudButton.Type.OUTLINE);
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);
        header.add(toolbar, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);

        // ── Main Content (Split) ──────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setDividerLocation(400);
        split.setBorder(null);

        tableModel = new DefaultTableModel(new String[]{"Username", "Role", "Access keys", "Created", "Last login"}, 0);
        userTable = new MiniCloudTable();
        userTable.setModel(tableModel);

        userTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                String role = v != null ? v.toString() : "READONLY";
                StatusBadge.ColorTheme theme = switch (role) {
                    case "ADMIN"     -> StatusBadge.ColorTheme.BLUE;
                    case "DEVELOPER" -> StatusBadge.ColorTheme.GREEN;
                    case "READONLY"  -> StatusBadge.ColorTheme.GRAY;
                    default          -> StatusBadge.ColorTheme.GRAY;
                };
                return new StatusBadge(role, theme);
            }
        });

        userTable.getSelectionModel().addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting()) updateDetail();
        });

        JScrollPane scroll = new JScrollPane(userTable);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY));
        split.setTopComponent(scroll);

        // Detail
        detailPanel = createDetailPanel();
        split.setBottomComponent(detailPanel);

        add(split, BorderLayout.CENTER);
    }

    private JPanel createDetailPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(20, 20, 20, 20));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(ThemeConstants.getFont(12, Font.BOLD));

        JPanel summary = new JPanel(new GridLayout(3, 2, 10, 10));
        summary.setBackground(Color.WHITE);
        summary.add(createLabel("User name:")); detUsername = createValue("—"); summary.add(detUsername);
        summary.add(createLabel("Role:"));      detRole = createValue("—");     summary.add(detRole);
        summary.add(createLabel("Creation:"));  detCreated = createValue("—");  summary.add(detCreated);

        tabs.addTab("Summary", summary);
        tabs.addTab("Permissions", new JLabel("  Full Access: EC2, S3, IAM, CloudWatch"));
        
        JPanel keyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        keyPanel.setBackground(Color.WHITE);
        MiniCloudButton createKeyBtn = new MiniCloudButton("Create Access Key", MiniCloudButton.Type.PRIMARY);
        createKeyBtn.addActionListener(e -> generateKey());
        keyPanel.add(createKeyBtn);
        tabs.addTab("Access Keys", keyPanel);

        p.add(tabs, BorderLayout.CENTER);
        return p;
    }

    private void showCreateUserDialog() {
        CreateUserDialog dialog = new CreateUserDialog((Frame) SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String uname = dialog.getNewUsername();
            String pass = dialog.getNewPassword();
            String role = dialog.getRole();

            new SwingWorker<JsonNode, Void>() {
                @Override protected JsonNode doInBackground() throws Exception {
                    Map<String, String> body = new HashMap<>();
                    body.put("username", uname);
                    body.put("password", pass);
                    body.put("role", role);
                    return apiClient.post("/auth/register", body);
                }
                @Override protected void done() {
                    try {
                        JsonNode res = get();
                        if (res.has("success") && res.get("success").asBoolean()) {
                            showCreatedSuccess(uname, pass, role);
                            refresh();
                        } else {
                            String error = res.has("message") ? res.get("message").asText() : "Unknown error";
                            JOptionPane.showMessageDialog(IAMPanel.this, "Failed: " + error);
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(IAMPanel.this, "Error communicating with server");
                    }
                }
            }.execute();
        }
    }

    private void showCreatedSuccess(String uname, String pass, String role) {
        JDialog dialog = new JDialog((Frame)null, "User Created", true);
        dialog.setLayout(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(25, 30, 25, 30));
        p.setBackground(Color.WHITE);

        JLabel title = new JLabel("Success: User Created");
        title.setFont(ThemeConstants.getFont(16, Font.BOLD));
        title.setForeground(ThemeConstants.GREEN_TEXT);
        p.add(title);
        p.add(Box.createVerticalStrut(20));

        p.add(new JLabel("A new IAM user has been created with the following credentials:"));
        p.add(Box.createVerticalStrut(15));
        p.add(new JLabel("Username: " + uname));
        p.add(new JLabel("Password: " + pass));
        p.add(new JLabel("Role: " + role));
        p.add(Box.createVerticalStrut(25));

        MiniCloudButton downloadBtn = new MiniCloudButton("Download .csv", MiniCloudButton.Type.OUTLINE);
        downloadBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(uname + "_credentials.csv"));
            if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                    fw.write("User Name,Password,Role,Console Login Link\n");
                    fw.write(uname + "," + pass + "," + role + ",http://localhost:8080/mini-console\n");
                    JOptionPane.showMessageDialog(dialog, "Credentials saved.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Failed to save file.");
                }
            }
        });
        p.add(downloadBtn);
        p.add(Box.createVerticalStrut(10));

        MiniCloudButton closeBtn = new MiniCloudButton("Close", MiniCloudButton.Type.PRIMARY);
        closeBtn.addActionListener(e -> dialog.dispose());
        p.add(closeBtn);

        dialog.add(p);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void generateKey() {
        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return apiClient.post("/iam/access-keys", new java.util.HashMap<>());
            }
            @Override protected void done() {
                try {
                    JsonNode res = get();
                    JsonNode data = apiClient.getData(res);
                    if (data != null && data.has("secretKey")) {
                        showSecretDialog(data.get("keyId").asText(), data.get("secretKey").asText());
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(IAMPanel.this, "Failed to generate key");
                }
            }
        }.execute();
    }

    private void showSecretDialog(String keyId, String secret) {
        JDialog dialog = new JDialog((Frame)null, "Access Key Created", true);
        dialog.setLayout(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(25, 30, 25, 30));
        p.setBackground(Color.WHITE);

        JLabel title = new JLabel("Success: Access Key Created");
        title.setFont(ThemeConstants.getFont(16, Font.BOLD));
        title.setForeground(ThemeConstants.GREEN_TEXT);
        p.add(title);
        p.add(Box.createVerticalStrut(15));

        p.add(new JLabel("Store your secret key in a safe place. It won't be shown again."));
        p.add(Box.createVerticalStrut(20));

        p.add(createLabel("Access Key ID:"));
        JTextField idF = new JTextField(keyId); idF.setEditable(false);
        p.add(idF);

        p.add(Box.createVerticalStrut(10));
        p.add(createLabel("Secret Access Key:"));
        JTextField secF = new JTextField(secret); secF.setEditable(false);
        p.add(secF);

        p.add(Box.createVerticalStrut(25));
        MiniCloudButton closeBtn = new MiniCloudButton("Close", MiniCloudButton.Type.PRIMARY);
        closeBtn.addActionListener(e -> dialog.dispose());
        p.add(closeBtn);

        dialog.add(p);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JLabel createLabel(String t) { JLabel l = new JLabel(t); l.setForeground(ThemeConstants.TEXT_MUTED); return l; }
    private JLabel createValue(String t) { JLabel l = new JLabel(t); l.setForeground(ThemeConstants.TEXT_DARK); return l; }

    public void refresh() {
        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return apiClient.getData(apiClient.get("/iam/users"));
            }
            @Override protected void done() {
                try {
                    JsonNode res = get();
                    tableModel.setRowCount(0);
                    if (res.isArray()) {
                        for (JsonNode u : res) {
                            tableModel.addRow(new Object[]{
                                u.get("username").asText(),
                                u.has("role") ? u.get("role").asText() : "ADMIN",
                                "1 key",
                                u.has("createdAt") ? u.get("createdAt").asText().substring(0, 10) : "—",
                                "Today"
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void updateDetail() {
        int r = userTable.getSelectedRow();
        if (r < 0) return;
        detUsername.setText(tableModel.getValueAt(r, 0).toString());
        detRole.setText(tableModel.getValueAt(r, 1).toString());
        detCreated.setText(tableModel.getValueAt(r, 3).toString());
    }
}
