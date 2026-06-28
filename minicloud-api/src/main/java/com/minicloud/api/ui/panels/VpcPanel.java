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
public class VpcPanel extends JPanel {

    private JTable vpcTable;
    private DefaultTableModel tableModel;

    public VpcPanel() {
        setLayout(new BorderLayout());
        setBackground(SwingLauncher.AWS_PANEL_BG);
        buildUI();
        refresh();
    }

    private void buildUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(new EmptyBorder(15, 25, 15, 25));

        JLabel title = new JLabel("VPCs");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        JButton createBtn = new JButton("Create VPC");
        createBtn.setBackground(SwingLauncher.AWS_ORANGE);
        actions.add(createBtn);
        header.add(actions, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Table
        String[] cols = {"Name", "VPC ID", "State", "IPv4 CIDR", "Owner ID", "Default VPC"};
        tableModel = new DefaultTableModel(cols, 0);
        vpcTable = new JTable(tableModel);
        vpcTable.setRowHeight(35);
        vpcTable.setShowGrid(false);
        vpcTable.setIntercellSpacing(new Dimension(0, 0));
        vpcTable.setBackground(Color.WHITE);
        
        JScrollPane scroll = new JScrollPane(vpcTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    private void refresh() {
        if (!ApiClient.isLoggedIn()) return;
        
        String accountId = ApiClient.getSession().getAccountId();
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/vpc/" + accountId);
            }
            @Override protected void done() {
                try {
                    JsonNode resp = get();
                    JsonNode data = resp.get("data");
                    tableModel.setRowCount(0);
                    for (JsonNode v : data) {
                        tableModel.addRow(new Object[]{
                            v.get("name").asText(),
                            v.get("id").asText().substring(0, 8) + "...",
                            v.get("state").asText(),
                            v.get("cidrBlock").asText(),
                            v.get("accountId").asText(),
                            v.get("isDefault").asBoolean() ? "Yes" : "No"
                        });
                    }
                } catch (Exception e) {
                    log.error("Failed to refresh VPCs: {}", e.getMessage());
                }
            }
        };
        worker.execute();
    }
}
