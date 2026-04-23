package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import javax.swing.SwingWorker;

/**
 * S3 Panel — create buckets, list objects, upload/delete.
 */
@Slf4j
public class S3Panel extends JPanel {

    private DefaultTableModel bucketModel;
    private DefaultTableModel objectModel;
    private JTable bucketTable;
    private JLabel selectedBucketLabel;
    private JTabbedPane detailsTab;


    private static final String[] BUCKET_COLS = {"Bucket Name", "Region", "Created At", "Object Count"};
    private static final String[] OBJECT_COLS = {"Key", "Size", "Last Modified", "Content-Type"};

    public S3Panel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        refreshBuckets();
    }

    private void buildUI() {
        JLabel title = new JLabel("S3 — Simple Storage Service");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        // Buckets section
        JPanel bucketsPanel = new JPanel(new BorderLayout(0, 8));
        bucketsPanel.setOpaque(false);

        JPanel bHeader = new JPanel(new BorderLayout());
        bHeader.setOpaque(false);
        JLabel bTitle = new JLabel("Buckets");
        bTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        bTitle.setForeground(SwingLauncher.AWS_ORANGE);

        JPanel bBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bBtns.setOpaque(false);
        bBtns.add(btn("Create Bucket", SwingLauncher.AWS_ORANGE, this::createBucket));
        bBtns.add(btn("Delete Bucket", SwingLauncher.AWS_RED,    this::deleteBucket));
        bBtns.add(btn("↻",            SwingLauncher.AWS_BLUE,    this::refreshBuckets));
        bHeader.add(bTitle, BorderLayout.WEST);
        bHeader.add(bBtns,  BorderLayout.EAST);

        bucketModel = new DefaultTableModel(BUCKET_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        bucketTable = styledTable(bucketModel);
        bucketTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadObjects();
        });

        JScrollPane bScroll = scrollPane(bucketTable);
        bScroll.setPreferredSize(new Dimension(0, 180));

        bucketsPanel.add(bHeader,  BorderLayout.NORTH);
        bucketsPanel.add(bScroll,  BorderLayout.CENTER);

        // Objects section
        JPanel objectsPanel = new JPanel(new BorderLayout(0, 8));
        objectsPanel.setOpaque(false);

        selectedBucketLabel = new JLabel("Select a bucket to view objects");
        selectedBucketLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        selectedBucketLabel.setForeground(SwingLauncher.AWS_ORANGE);

        JPanel oHeader = new JPanel(new BorderLayout());
        oHeader.setOpaque(false);
        oHeader.add(selectedBucketLabel, BorderLayout.WEST);

        objectModel = new DefaultTableModel(OBJECT_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable objTable = styledTable(objectModel);

        objectsPanel.add(oHeader,              BorderLayout.NORTH);
        objectsPanel.add(scrollPane(objTable),  BorderLayout.CENTER);

        // Details (Bottom of objectsPanel or side)
        detailsTab = new JTabbedPane();
        detailsTab.addTab("Objects",     objectsPanel);
        detailsTab.addTab("Properties",  createS3PropertiesTab());
        detailsTab.addTab("Permissions", createS3PermissionsTab());
        detailsTab.addTab("Metrics",     createS3MetricsTab());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bucketsPanel, detailsTab);
        split.setDividerLocation(240);
        split.setBackground(SwingLauncher.AWS_DARK_BG);
        split.setBorder(null);

        add(title, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private JPanel createS3PropertiesTab() {
        JPanel p = new JPanel(new GridLayout(3, 1, 10, 10));
        p.setBackground(SwingLauncher.AWS_PANEL_BG);
        p.add(new JLabel("Bucket versioning: Suspended"));
        p.add(new JLabel("Server-side encryption: AWS-KMS (SSE-KMS)"));
        p.add(new JLabel("Static website hosting: Disabled"));
        return p;
    }

    private JPanel createS3PermissionsTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBackground(SwingLauncher.AWS_PANEL_BG);
        p.add(new JLabel("Block all public access: On"));
        return p;
    }

    private JPanel createS3MetricsTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBackground(SwingLauncher.AWS_PANEL_BG);
        p.add(new JLabel("Monitoring with CloudWatch (Standard)"));
        return p;
    }


    private void refreshBuckets() {
        if (!ApiClient.isLoggedIn()) return;
        String userId = ApiClient.getSession().getUserId();
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/storage/buckets/user/" + userId);
            }
            @Override protected void done() {
                try {
                    bucketModel.setRowCount(0);
                    JsonNode resp = get();
                    JsonNode data = resp.get("data");
                    if (data != null && data.isArray()) {
                        data.forEach(n -> bucketModel.addRow(new Object[]{
                            n.path("name").asText("—"),
                            n.path("region").asText("us-east-1"),
                            n.path("createdAt").asText("—"),
                            n.path("objectCount").asText("0")
                        }));
                    }
                } catch (Exception e) { log.error("S3 Bucket Refresh Error: {}", e.getMessage()); }
            }
        };
        w.execute();
    }


    private void loadObjects() {
        int row = bucketTable.getSelectedRow();
        if (row < 0) return;
        String bucket = (String) bucketModel.getValueAt(row, 0);
        selectedBucketLabel.setText("Objects in: " + bucket);
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/storage/buckets/" + bucket + "/objects?userId=" + ApiClient.getSession().getUserId());
            }
            @Override protected void done() {
                try {
                    objectModel.setRowCount(0);
                    JsonNode arr = get();
                    if (arr.isArray()) arr.forEach(n -> objectModel.addRow(new Object[]{
                        n.path("key").asText("—"),
                        n.path("size").asText("—"),
                        n.path("lastModified").asText("—"),
                        n.path("contentType").asText("—")
                    }));
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void createBucket() {
        String name = JOptionPane.showInputDialog(this, "Bucket name:", "Create Bucket", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        
        String userId = ApiClient.getSession().getUserId();
        
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/storage/buckets?name=" + name + "&userId=" + userId, null);
                return null;
            }
            @Override protected void done() { refreshBuckets(); }
        };
        w.execute();
    }


    private void deleteBucket() {
        int row = bucketTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a bucket first."); return; }
        String name = (String) bucketModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete bucket '" + name + "'?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.delete("/storage/buckets/" + name + "?userId=" + ApiClient.getSession().getUserId()); return null;
            }
            @Override protected void done() { refreshBuckets(); }
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

    private JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
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
        return t;
    }

    private JScrollPane scrollPane(JTable t) {
        JScrollPane s = new JScrollPane(t);
        s.setBackground(SwingLauncher.AWS_DARK_BG);
        s.getViewport().setBackground(SwingLauncher.AWS_DARK_BG);
        s.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));
        return s;
    }
}
