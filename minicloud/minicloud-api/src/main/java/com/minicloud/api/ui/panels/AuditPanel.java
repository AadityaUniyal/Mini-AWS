package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.SwingWorker;

/**
 * CloudTrail Audit Panel — live stream of all API events.
 */
public class AuditPanel extends JPanel {

    private DefaultTableModel tableModel;
    private JLabel eventCountLabel;
    private Timer autoRefresh;

    private static final String[] COLS = {"Time", "User", "Action", "Resource", "Status", "IP"};

    public AuditPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        startAutoRefresh();
    }

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRow.setOpaque(false);
        JLabel title = new JLabel("CloudTrail — Event History");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        eventCountLabel = new JLabel("0 events");
        eventCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        eventCountLabel.setForeground(SwingLauncher.AWS_TEXT_DIM);
        titleRow.add(title);
        titleRow.add(eventCountLabel);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(btn("↻ Refresh Now", SwingLauncher.AWS_BLUE,   this::refresh));
        btns.add(btn("Clear View",    new Color(0x6B, 0x72, 0x80), () -> tableModel.setRowCount(0)));

        header.add(titleRow, BorderLayout.WEST);
        header.add(btns,     BorderLayout.EAST);

        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        styleTable(table);

        // Color status column
        table.getColumnModel().getColumn(4).setCellRenderer((t, val, sel, foc, row, col) -> {
            JLabel l = new JLabel(val == null ? "" : val.toString());
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            l.setForeground("SUCCESS".equalsIgnoreCase(l.getText()) ? SwingLauncher.AWS_GREEN :
                            "FAILED".equalsIgnoreCase(l.getText())  ? SwingLauncher.AWS_RED   :
                            SwingLauncher.AWS_TEXT);
            l.setBackground(sel ? SwingLauncher.AWS_NAVY : SwingLauncher.AWS_DARK_BG);
            l.setOpaque(true);
            return l;
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(SwingLauncher.AWS_DARK_BG);
        scroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    private void refresh() {
        if (!ApiClient.isLoggedIn()) return;
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/monitoring/audit?limit=100");
            }
            @Override protected void done() {
                try {
                    tableModel.setRowCount(0);
                    JsonNode resp = get();
                    JsonNode arr = resp.has("data") ? resp.get("data") : resp;
                    if (arr != null && arr.isArray()) {
                    arr.forEach(n -> tableModel.addRow(new Object[]{
                            formatTime(n.path("timestamp").asText("—")),
                            n.path("username").asText(n.path("userId").asText("system")),
                            n.path("action").asText("—"),
                            n.path("service").asText("") + " " + n.path("resource").asText(""),
                            n.path("status").asText("—"),
                            "127.0.0.1"
                        }));
                        eventCountLabel.setText(arr.size() + " events");
                    }
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private String formatTime(String iso) {
        if (iso.length() > 19) return iso.substring(0, 19).replace("T", " ");
        return iso;
    }

    private void startAutoRefresh() {
        autoRefresh = new Timer(true);
        autoRefresh.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { SwingUtilities.invokeLater(AuditPanel.this::refresh); }
        }, 10000, 10000);
    }

    private JButton btn(String text, Color bg, Runnable action) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
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
        t.setFont(new Font("Consolas", Font.PLAIN, 12));
        t.setRowHeight(26);
        t.getTableHeader().setBackground(SwingLauncher.AWS_NAVY);
        t.getTableHeader().setForeground(SwingLauncher.AWS_ORANGE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        t.getColumnModel().getColumn(0).setPreferredWidth(140);
        t.getColumnModel().getColumn(1).setPreferredWidth(100);
        t.getColumnModel().getColumn(2).setPreferredWidth(150);
        t.setFillsViewportHeight(true);
    }
}
