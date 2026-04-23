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
import java.text.DecimalFormat;

@Slf4j
public class BillingPanel extends JPanel {

    private JLabel totalCostLabel;
    private JTable usageTable;
    private DefaultTableModel tableModel;
    private final DecimalFormat df = new DecimalFormat("$#,##0.00");

    public BillingPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE); // AWS Billing style is light
        buildUI();
        refreshData();
    }

    private void buildUI() {
        // Left Navigation (Simulated)
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(242, 243, 243));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(new EmptyBorder(20, 15, 20, 15));
        
        sidebar.add(createSidebarLink("AWS Billing Home", true));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(createSidebarLink("Bills", false));
        sidebar.add(createSidebarLink("Payments", false));
        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(createHeader("Cost analysis"));
        sidebar.add(createSidebarLink("Cost Explorer", false));
        sidebar.add(createSidebarLink("Budgets", false));
        
        add(sidebar, BorderLayout.WEST);

        // Main Content
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Top Widgets
        JPanel widgets = new JPanel(new GridLayout(1, 3, 20, 0));
        widgets.setOpaque(false);
        widgets.add(createCostWidget());
        widgets.add(createServiceWidget());
        widgets.add(createAnomalyWidget());

        // Bottom Table
        JPanel tablePanel = new JPanel(new BorderLayout(0, 10));
        tablePanel.setOpaque(false);
        tablePanel.setBorder(new EmptyBorder(30, 0, 0, 0));
        
        JLabel tableTitle = new JLabel("Month-to-date top services by cost");
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        tablePanel.add(tableTitle, BorderLayout.NORTH);

        String[] cols = {"Service", "Resource", "Usage Type", "Usage", "Cost"};
        tableModel = new DefaultTableModel(cols, 0);
        usageTable = new JTable(tableModel);
        usageTable.setRowHeight(35);
        usageTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        usageTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        tablePanel.add(new JScrollPane(usageTable), BorderLayout.CENTER);

        content.add(widgets, BorderLayout.NORTH);
        content.add(tablePanel, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private JPanel createCostWidget() {
        JPanel card = createCard("AWS Summary");
        JLabel lbl = new JLabel("Month-to-date cost (Estimated)");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(Color.GRAY);
        
        totalCostLabel = new JLabel("$0.00");
        totalCostLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        totalCostLabel.setForeground(new Color(33, 33, 33));

        card.add(lbl);
        card.add(Box.createVerticalStrut(5));
        card.add(totalCostLabel);
        return card;
    }

    private JPanel createServiceWidget() {
        JPanel card = createCard("Top Service");
        JLabel lbl = new JLabel("Highest spending service");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(Color.GRAY);
        
        JLabel svcLabel = new JLabel("EC2");
        svcLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        
        card.add(lbl);
        card.add(Box.createVerticalStrut(10));
        card.add(svcLabel);
        return card;
    }

    private JPanel createAnomalyWidget() {
        JPanel card = createCard("Cost Monitor");
        JLabel lbl = new JLabel("Budgets & Anomalies");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(Color.GRAY);
        
        JLabel okLabel = new JLabel("✓ 0 Alerts");
        okLabel.setForeground(new Color(46, 125, 50));
        okLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        
        card.add(lbl);
        card.add(Box.createVerticalStrut(10));
        card.add(okLabel);
        return card;
    }

    private JPanel createCard(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                new EmptyBorder(20, 20, 20, 20)));
        
        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 14));
        p.add(t);
        p.add(Box.createVerticalStrut(15));
        return p;
    }

    private void refreshData() {
        if (!ApiClient.isLoggedIn()) return;
        
        String accountId = ApiClient.getSession().getAccountId();
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/billing/summary/" + accountId);
            }
            @Override protected void done() {
                try {
                    JsonNode resp = get();
                    JsonNode data = resp.get("data");
                    
                    double total = data.get("monthToDateEstimate").asDouble();
                    totalCostLabel.setText(df.format(total));
                    
                    tableModel.setRowCount(0);
                    JsonNode records = data.get("usageRecords");
                    for (JsonNode r : records) {
                        tableModel.addRow(new Object[]{
                            r.get("service").asText(),
                            r.get("resourceName").asText(),
                            r.get("unitType").asText(),
                            r.get("usageQuantity").asDouble(),
                            df.format(r.get("totalCost").asDouble())
                        });
                    }
                } catch (Exception e) {
                    log.error("Failed to load billing data: {}", e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private JLabel createHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(Color.GRAY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel createSidebarLink(String text, boolean active) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, 13));
        l.setForeground(active ? new Color(0, 103, 184) : Color.DARK_GRAY);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
