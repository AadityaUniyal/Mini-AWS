package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import javax.swing.SwingWorker;

@Slf4j
public class Route53Panel extends JPanel {

    private JTable zoneTable;
    private DefaultTableModel zoneModel;
    private JTable recordTable;
    private DefaultTableModel recordModel;

    public Route53Panel() {
        setLayout(new BorderLayout(0, 16));
        setBackground(SwingLauncher.AWS_DARK_BG);
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        refreshZones();
    }

    private void buildUI() {
        JLabel title = new JLabel("Route 53 — Scalable DNS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        add(title, BorderLayout.NORTH);

        // Split Pane: Upper Zones, Lower Records
        zoneModel = new DefaultTableModel(new String[]{"Hosted Zone Name", "Comment", "ID"}, 0);
        zoneTable = new JTable(zoneModel);
        zoneTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadRecords();
        });

        recordModel = new DefaultTableModel(new String[]{"Record Name", "Type", "Value", "TTL"}, 0);
        recordTable = new JTable(recordModel);

        JPanel zonePanel = createResourcePanel("Hosted Zones", zoneTable, this::createZone, this::refreshZones);
        JPanel recordPanel = createResourcePanel("Record Sets", recordTable, this::createRecord, this::loadRecords);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, zonePanel, recordPanel);
        split.setDividerLocation(300);
        split.setBackground(SwingLauncher.AWS_DARK_BG);
        add(split, BorderLayout.CENTER);
    }

    private JPanel createResourcePanel(String title, JTable table, Runnable onAdd, Runnable onRefresh) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(new Font("Segoe UI", Font.BOLD, 15));
        l.setForeground(SwingLauncher.AWS_ORANGE);
        header.add(l, BorderLayout.WEST);
        
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton addBtn = new JButton("Create");
        addBtn.addActionListener(e -> onAdd.run());
        JButton refBtn = new JButton("↻");
        refBtn.addActionListener(e -> onRefresh.run());
        btns.add(addBtn);
        btns.add(refBtn);
        header.add(btns, BorderLayout.EAST);
        
        p.add(header, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshZones() {
        if (!ApiClient.isLoggedIn()) return;
        String accountId = ApiClient.getSession().getAccountId();
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/route53/zones/" + accountId);
            }
            @Override protected void done() {
                try {
                    JsonNode data = get().get("data");
                    zoneModel.setRowCount(0);
                    for (JsonNode z : data) {
                        zoneModel.addRow(new Object[]{z.get("name").asText(), z.get("comment").asText(), z.get("id").asText()});
                    }
                } catch (Exception e) { log.error("Failed to load zones: {}", e.getMessage()); }
            }
        };
        worker.execute();
    }

    private void loadRecords() {
        int row = zoneTable.getSelectedRow();
        if (row < 0) return;
        String zoneId = (String) zoneModel.getValueAt(row, 2);
        
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/route53/records/" + zoneId);
            }
            @Override protected void done() {
                try {
                    JsonNode data = get().get("data");
                    recordModel.setRowCount(0);
                    for (JsonNode r : data) {
                        recordModel.addRow(new Object[]{r.get("name").asText(), r.get("type").asText(), r.get("value").asText(), r.get("ttl").asText()});
                    }
                } catch (Exception e) { log.error("Failed to load records: {}", e.getMessage()); }
            }
        };
        worker.execute();
    }

    private void createZone() {
        String name = JOptionPane.showInputDialog(this, "Domain Name (e.g. example.com):");
        if (name == null || name.isBlank()) return;
        
        String accountId = ApiClient.getSession().getAccountId();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/api/v1/route53/zones", java.util.Map.of("name", name, "accountId", accountId, "comment", "Created via console"));
                return null;
            }
            @Override protected void done() { refreshZones(); }
        };
        worker.execute();
    }

    private void createRecord() {
        int row = zoneTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a Hosted Zone first."); return; }
        String zoneId = (String) zoneModel.getValueAt(row, 2);
        
        JTextField nameField = new JTextField();
        JTextField typeField = new JTextField("A");
        JTextField valField = new JTextField();
        JTextField ttlField = new JTextField("300");
        
        Object[] message = { "Name (subdomain):", nameField, "Type (A/CNAME):", typeField, "Value (IP/Domain):", valField, "TTL:", ttlField };
        int opt = JOptionPane.showConfirmDialog(this, message, "Create Record Set", JOptionPane.OK_CANCEL_OPTION);
        
        if (opt == JOptionPane.OK_OPTION) {
            String accountId = ApiClient.getSession().getAccountId();
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    ApiClient.post("/api/v1/route53/records", java.util.Map.of(
                        "zoneId", zoneId, "name", nameField.getText(), "type", typeField.getText(),
                        "value", valField.getText(), "ttl", ttlField.getText(), "accountId", accountId
                    ));
                    return null;
                }
                @Override protected void done() { loadRecords(); }
            };
            worker.execute();
        }
    }
}
