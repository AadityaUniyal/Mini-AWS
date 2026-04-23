package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import javax.swing.SwingWorker;

/**
 * IAM Panel — manage users, roles, and access keys.
 */
public class IamPanel extends JPanel {

    private DefaultTableModel userModel;
    private JTable userTable;
    private JTextArea policyArea;
    private String selectedUser;

    private static final String[] COLS = {"Username", "Email", "Role", "Created At", "Active"};

    public IamPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("IAM — Identity & Access Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(btn("Create User",      SwingLauncher.AWS_ORANGE, this::createUser));
        btns.add(btn("Delete User",      SwingLauncher.AWS_RED,    this::deleteUser));
        btns.add(btn("Generate Key",     SwingLauncher.AWS_GREEN,  this::generateKey));
        btns.add(btn("↻ Refresh",        SwingLauncher.AWS_BLUE,   this::refresh));

        header.add(title, BorderLayout.WEST);
        header.add(btns,  BorderLayout.EAST);

        userModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        userTable = new JTable(userModel);
        styleTable(userTable);

        JScrollPane scroll = new JScrollPane(userTable);
        scroll.getViewport().setBackground(SwingLauncher.AWS_DARK_BG);
        scroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        // Policy Editor Area
        JPanel policyPanel = new JPanel(new BorderLayout());
        policyPanel.setBackground(SwingLauncher.AWS_DARK_BG);
        
        JLabel policyTitle = new JLabel("Permissions (JSON)");
        policyTitle.setBorder(new EmptyBorder(10, 10, 5, 10));
        policyPanel.add(policyTitle, BorderLayout.NORTH);

        policyArea = new JTextArea();
        policyArea.setBackground(new Color(0x1E, 0x1E, 0x1E));
        policyArea.setForeground(new Color(0x9C, 0xDC, 0xFE)); // Blueish code font
        policyArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        policyArea.setCaretColor(Color.WHITE);
        
        JButton saveBtn = btn("Save Policy", SwingLauncher.AWS_ORANGE, this::savePolicy);
        saveBtn.setPreferredSize(new Dimension(120, 30));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        btnPanel.add(saveBtn);
        policyPanel.add(btnPanel, BorderLayout.SOUTH);

        policyPanel.add(new JScrollPane(policyArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, policyPanel);
        split.setDividerLocation(300);
        split.setBackground(SwingLauncher.AWS_DARK_BG);
        split.setBorder(null);

        userTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadPolicy();
        });

        add(header, BorderLayout.NORTH);
        add(split,  BorderLayout.CENTER);
    }

    private void loadPolicy() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            policyArea.setText("");
            return;
        }
        selectedUser = (String) userModel.getValueAt(row, 0);
        
        // Fetch detailed user info including policy
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/iam/users/" + selectedUser);
            }
            @Override protected void done() {
                try {
                    JsonNode user = get();
                    String policy = user.path("inlinePolicy").asText("{ \n  \"Version\": \"2012-10-17\", \n  \"Statement\": [] \n}");
                    policyArea.setText(policy);
                } catch (Exception ex) { policyArea.setText("// Error loading policy"); }
            }
        };
        w.execute();
    }

    private void savePolicy() {
        if (selectedUser == null) return;
        String json = policyArea.getText();
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/api/iam/users/" + selectedUser + "/policy", Map.of("document", json));
                return null;
            }
            @Override protected void done() {
                JOptionPane.showMessageDialog(IamPanel.this, "Policy saved successfully.");
            }
        };
        w.execute();
    }

    private JPanel infoCard(String icon, String main, String sub) {
        JPanel p = new JPanel(new GridLayout(3, 1, 4, 4));
        p.setBackground(SwingLauncher.AWS_PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER),
            new EmptyBorder(12, 16, 12, 16)));
        JLabel i = new JLabel(icon);  i.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        JLabel m = new JLabel(main);  m.setForeground(Color.WHITE); m.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JLabel s = new JLabel(sub);   s.setForeground(SwingLauncher.AWS_TEXT_DIM); s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        p.add(i); p.add(m); p.add(s);
        return p;
    }

    private void refresh() {
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/iam/users");
            }
            @Override protected void done() {
                try {
                    userModel.setRowCount(0);
                    JsonNode arr = get();
                    JsonNode data = arr.has("data") ? arr.get("data") : arr;
                    if (data != null && data.isArray()) data.forEach(n -> userModel.addRow(new Object[]{
                        n.path("username").asText("—"),
                        n.path("email").asText("—"),
                        n.path("role").asText("—"),
                        n.path("createdAt").asText("—"),
                        "✓"
                    }));
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void createUser() {
        JTextField user  = new JTextField();
        JTextField email = new JTextField();
        JPasswordField pass = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"USER","ADMIN"});

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Username:")); form.add(user);
        form.add(new JLabel("Email:"));    form.add(email);
        form.add(new JLabel("Password:")); form.add(pass);
        form.add(new JLabel("Role:"));     form.add(role);

        int res = JOptionPane.showConfirmDialog(this, form, "Create IAM User",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/auth/register", Map.of(
                    "username", user.getText(),
                    "email", email.getText(),
                    "password", new String(pass.getPassword()),
                    "role", role.getSelectedItem()
                ));
                return null;
            }
            @Override protected void done() { refresh(); }
        };
        w.execute();
    }

    private void deleteUser() {
        int row = userTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        String name = (String) userModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete user '" + name + "'?",
            "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.delete("/api/iam/users/by-username/" + name); return null;
            }
            @Override protected void done() { refresh(); }
        };
        w.execute();
    }

    private void generateKey() {
        int row = userTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        String name = (String) userModel.getValueAt(row, 0);
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.post("/api/iam/users/" + name + "/access-keys", null);
            }
            @Override protected void done() {
                try {
                    JsonNode r = get();
                    JOptionPane.showMessageDialog(IamPanel.this,
                        "Access Key ID:  " + r.path("keyId").asText() + "\n" +
                        "Secret Key:     " + r.path("secretKey").asText() + "\n\n" +
                        "⚠  Save these credentials now. You won't see the secret again.",
                        "Access Key Created", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        };
        w.execute();
    }

    private JButton btn(String text, Color bg, Runnable action) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(bg.equals(SwingLauncher.AWS_ORANGE) ? Color.BLACK : Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    private void styleTable(JTable t) {
        t.setBackground(SwingLauncher.AWS_DARK_BG);
        t.setForeground(SwingLauncher.AWS_TEXT);
        t.setSelectionBackground(SwingLauncher.AWS_NAVY);
        t.setGridColor(SwingLauncher.AWS_BORDER);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setRowHeight(28);
        t.getTableHeader().setBackground(SwingLauncher.AWS_NAVY);
        t.getTableHeader().setForeground(SwingLauncher.AWS_ORANGE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setFillsViewportHeight(true);
    }
}
