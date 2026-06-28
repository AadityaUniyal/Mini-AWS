package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * EC2 Panel — create, list, start, stop virtual instances.
 */
@Slf4j
public class Ec2Panel extends JPanel {

    private DefaultTableModel tableModel;
    private JTable table;
    private JTabbedPane detailsTabbedPane;
    private final Map<String, JsonNode> instanceData = new HashMap<>();

    private static final String[] COLS = {"Instance ID", "Name", "Type", "Status", "Public IP", "Availability Zone"};

    public Ec2Panel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        refresh();
    }

    private void buildUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 16, 0));

        JLabel title = new JLabel("Instances");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(actionButton("Launch instances", SwingLauncher.AWS_ORANGE, this::launchInstance));
        buttons.add(actionButton("Start instance",   SwingLauncher.AWS_GREEN,  () -> instanceAction("start")));
        buttons.add(actionButton("Stop instance",    SwingLauncher.AWS_RED,    () -> instanceAction("stop")));
        
        header.add(title,   BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);

        // Instance List Table
        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        styleTable(table);
        table.getSelectionModel().addListSelectionListener(e -> updateDetails());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        // Details Pane
        detailsTabbedPane = new JTabbedPane();
        detailsTabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        detailsTabbedPane.addTab("Details",       createDetailsTab());
        detailsTabbedPane.addTab("Security",      createSecurityTab());
        detailsTabbedPane.addTab("Networking",    createNetworkingTab());
        detailsTabbedPane.addTab("Storage",       createStorageTab());
        detailsTabbedPane.addTab("Elastic IPs",   createElasticIpTab());
        detailsTabbedPane.addTab("Monitoring",    createMonitoringTab());
        detailsTabbedPane.addTab("Tags",          createTagsTab());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, detailsTabbedPane);
        split.setDividerLocation(300);
        split.setOpaque(false);
        split.setBorder(null);

        add(header, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private void refresh() {
        if (!ApiClient.isLoggedIn()) return;
        String accountId = ApiClient.getSession().getAccountId();
        
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/compute/instances/account/" + accountId);
            }
            @Override protected void done() {
                try {
                    tableModel.setRowCount(0);
                    instanceData.clear();
                    JsonNode resp = get();
                    JsonNode arr = resp.get("data");
                    if (arr != null && arr.isArray()) {
                        for (JsonNode n : arr) {
                            String id = n.path("id").asText();
                            instanceData.put(id, n);
                            tableModel.addRow(new Object[]{
                                id.substring(0, 8) + "...",
                                n.path("name").asText("—"),
                                n.path("type").asText("—"),
                                n.path("state").asText("—"),
                                n.path("publicIp").asText("—"),
                                "us-east-1a" // Simulation
                            });
                        }
                    }
                } catch (Exception ex) { /* log.error("EC2 Refresh Error: {}", ex.getMessage()); */ }
            }
        };
        w.execute();
    }

    private void launchInstance() {
        JTextField name = new JTextField("my-instance");
        JComboBox<String> type = new JComboBox<>(new String[]{"t2.micro","t2.small","t2.medium","t3.large"});
        
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Name:")); form.add(name);
        form.add(new JLabel("Type:")); form.add(type);

        int res = JOptionPane.showConfirmDialog(this, form, "Launch Instance",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (res == JOptionPane.OK_OPTION) {
            String accountId = ApiClient.getSession().getAccountId();
            String userId = ApiClient.getSession().getUserId();
            
            Map<String, String> params = new HashMap<>();
            params.put("name", name.getText());
            params.put("type", (String) type.getSelectedItem());
            params.put("userId", userId);
            params.put("accountId", accountId);
            params.put("command", "sleep 3600"); // Default simulation command

            SwingWorker<Void, Void> w = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    ApiClient.post("/api/v1/compute/instances/launch?name=" + params.get("name") 
                    + "&type=" + params.get("type")
                    + "&userId=" + params.get("userId")
                    + "&accountId=" + params.get("accountId")
                    + "&command=" + java.net.URLEncoder.encode(params.get("command"), "UTF-8"), null);
                    return null;
                }
                @Override protected void done() { refresh(); }
            };
            w.execute();
        }
    }

    private void instanceAction(String action) {
        int row = table.getSelectedRow();
        if (row < 0) return;
        
        String displayId = (String) tableModel.getValueAt(row, 0);
        // Find real ID from instanceData
        String realId = instanceData.keySet().stream()
                .filter(k -> k.startsWith(displayId.replace("...", "")))
                .findFirst().orElse(null);
        
        if (realId == null) return;
        
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/api/v1/compute/instances/" + realId + "/" + action, null);
                return null;
            }
            @Override protected void done() { refresh(); }
        };
        w.execute();
    }

    // ── High-Fidelity Details ────────────────────────────────────────────────

    private JPanel createDetailsTab() {
        return createBasePanel();
    }

    private JPanel createSecurityTab() {
        return createBasePanel();
    }

    private JPanel createNetworkingTab() {
        return createBasePanel();
    }

    private JPanel createStorageTab() {
        JPanel p = createBasePanel();
        p.setLayout(new BorderLayout());
        JLabel l = new JLabel("Root device: /dev/xvda (8 GiB, EBS)");
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        p.add(l, BorderLayout.NORTH);
        return p;
    }

    private JPanel createTagsTab() {
        JPanel p = createBasePanel();
        p.setLayout(new BorderLayout());
        String[] cols = {"Key", "Value"};
        DefaultTableModel m = new DefaultTableModel(cols, 0);
        m.addRow(new Object[]{"Name", "my-instance"});
        m.addRow(new Object[]{"Environment", "production"});
        JTable t = new JTable(m);
        styleTable(t);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private void updateDetails() {
        int row = table.getSelectedRow();
        if (row < 0) {
            clearDetails();
            return;
        }
        
        String displayId = (String) tableModel.getValueAt(row, 0);
        String realId = instanceData.keySet().stream()
                .filter(k -> k.startsWith(displayId.replace("...", "")))
                .findFirst().orElse(null);
        
        if (realId != null) {
            JsonNode data = instanceData.get(realId);
            populateDetailsTab(data);
            populateNetworkingTab(data);
            populateSecurityTab(data);
        }
    }

    private void clearDetails() {
        ((JPanel)detailsTabbedPane.getComponentAt(0)).removeAll();
        ((JPanel)detailsTabbedPane.getComponentAt(0)).add(new JLabel("Select an instance to view details."));
        detailsTabbedPane.repaint();
    }

    private void populateDetailsTab(JsonNode data) {
        JPanel p = (JPanel) detailsTabbedPane.getComponentAt(0);
        p.removeAll();
        p.setLayout(new GridLayout(4, 2, 10, 10));
        
        p.add(detailItem("Instance ID", data.path("id").asText()));
        p.add(detailItem("Instance state", data.path("state").asText()));
        p.add(detailItem("Instance type", data.path("type").asText()));
        p.add(detailItem("Private IP address", data.path("privateIp").asText("—")));
        p.add(detailItem("Public IP address", data.path("publicIp").asText("—")));
        p.add(detailItem("Monitoring", "Disabled (standard)"));
        p.add(detailItem("Launched at", data.path("launchedAt").asText("—")));
        
        p.revalidate();
        p.repaint();
    }

    private void populateNetworkingTab(JsonNode data) {
        JPanel p = (JPanel) detailsTabbedPane.getComponentAt(2);
        p.removeAll();
        p.setLayout(new GridLayout(3, 2, 10, 10));
        
        p.add(detailItem("VPC ID", "vpc-def12345 (default)"));
        p.add(detailItem("Subnet ID", data.path("subnetId").asText("subnet-0a1b2c3d")));
        p.add(detailItem("Network interface ID", "eni-0987654321"));
        
        p.revalidate();
        p.repaint();
    }

    private void populateSecurityTab(JsonNode data) {
        JPanel p = (JPanel) detailsTabbedPane.getComponentAt(1);
        p.removeAll();
        p.setLayout(new BorderLayout());
        
        JLabel sgLabel = new JLabel("Security Group: " + data.path("securityGroupName").asText("default"));
        sgLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        p.add(sgLabel, BorderLayout.NORTH);
        
        String[] cols = {"Type", "Protocol", "Port range", "Source"};
        DefaultTableModel m = new DefaultTableModel(cols, 0);
        m.addRow(new Object[]{"HTTP", "TCP", "80", "0.0.0.0/0"});
        m.addRow(new Object[]{"SSH", "TCP", "22", "0.0.0.0/0"});
        m.addRow(new Object[]{"Custom TCP", "TCP", "8080", "172.31.0.0/16"});
        
        JTable t = new JTable(m);
        styleTable(t);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        
        p.revalidate();
        p.repaint();
    }

    private JPanel detailItem(String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(SwingLauncher.AWS_TEXT_DIM);
        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        v.setForeground(Color.WHITE);
        p.add(l, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private JPanel createBasePanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBackground(SwingLauncher.AWS_PANEL_BG);
        p.setBorder(new EmptyBorder(15, 15, 15, 15));
        return p;
    }

    private JPanel createElasticIpTab() {
        JPanel p = createBasePanel();
        p.setLayout(new BorderLayout());
        JLabel l = new JLabel("Attached Elastic IP: None");
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        p.add(l, BorderLayout.NORTH);
        
        JButton allocBtn = actionButton("Allocate Elastic IP", SwingLauncher.AWS_ORANGE, () -> {
             JOptionPane.showMessageDialog(this, "Success: Allocated 52.14.99.2 (us-east-1)");
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bp.setOpaque(false);
        bp.add(allocBtn);
        p.add(bp, BorderLayout.CENTER);
        return p;
    }

    private JPanel createMonitoringTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBackground(SwingLauncher.AWS_PANEL_BG);
        p.setBorder(new EmptyBorder(15, 15, 15, 15));
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JButton actionButton(String text, Color bg, Runnable action) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(bg.equals(SwingLauncher.AWS_ORANGE) ? Color.BLACK : Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void styleTable(JTable t) {
        t.setBackground(SwingLauncher.AWS_DARK_BG);
        t.setForeground(SwingLauncher.AWS_TEXT);
        t.setSelectionBackground(SwingLauncher.AWS_NAVY);
        t.setSelectionForeground(Color.WHITE);
        t.setGridColor(SwingLauncher.AWS_BORDER);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setRowHeight(28);
        t.getTableHeader().setBackground(SwingLauncher.AWS_NAVY);
        t.getTableHeader().setForeground(SwingLauncher.AWS_ORANGE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setShowGrid(true);
        t.setFillsViewportHeight(true);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, "Error: " + msg, "API Error", JOptionPane.ERROR_MESSAGE);
    }
}
