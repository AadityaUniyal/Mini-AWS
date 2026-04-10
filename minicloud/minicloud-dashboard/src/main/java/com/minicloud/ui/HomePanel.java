package com.minicloud.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.components.SectionCard;
import com.minicloud.ui.components.MiniCloudTable;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Vector;

/**
 * Redesigned HomePanel — AWS Console Home style.
 */
public class HomePanel extends JPanel {

    private final ApiClient apiClient;
    private final MainFrame mainFrame;

    private JLabel greetingLabel;
    private SectionCard ec2Card, s3Card, iamCard, cpuCard;
    private DefaultTableModel activityModel;

    public HomePanel(ApiClient apiClient, MainFrame mainFrame) {
        this.apiClient = apiClient;
        this.mainFrame = mainFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeConstants.BG_LIGHT);
        setBorder(new EmptyBorder(30, 40, 30, 40));

        // ── Header ────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel title = new JLabel("Dashboard");
        title.setFont(ThemeConstants.getFont(20, Font.BOLD));
        title.setForeground(ThemeConstants.TEXT_DARK);

        greetingLabel = new JLabel("Welcome back, admin");
        greetingLabel.setFont(ThemeConstants.getFont(13, Font.PLAIN));
        greetingLabel.setForeground(ThemeConstants.TEXT_MUTED);

        titleBlock.add(title);
        titleBlock.add(greetingLabel);
        header.add(titleBlock, BorderLayout.WEST);

        MiniCloudButton refreshBtn = new MiniCloudButton("↻ Refresh", MiniCloudButton.Type.OUTLINE);
        refreshBtn.setPreferredSize(new Dimension(100, 32));
        refreshBtn.addActionListener(e -> refresh());
        header.add(refreshBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Main Content Area (Scrollable) ─────────────────────
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        // 1. Metric Cards Row
        JPanel cardsRow = new JPanel(new GridLayout(1, 4, 16, 0));
        cardsRow.setOpaque(false);
        cardsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        cardsRow.setBorder(new EmptyBorder(25, 0, 25, 0));

        ec2Card = new SectionCard("EC2 Instances");
        s3Card  = new SectionCard("S3 Buckets");
        iamCard = new SectionCard("IAM Users");
        cpuCard = new SectionCard("CPU Usage");

        cardsRow.add(ec2Card);
        cardsRow.add(s3Card);
        cardsRow.add(iamCard);
        cardsRow.add(cpuCard);
        content.add(cardsRow);

        // 2. Quick Actions
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionsPanel.setOpaque(false);
        actionsPanel.setBorder(new EmptyBorder(0, 0, 25, 0));
        
        actionsPanel.add(new MiniCloudButton("Launch Instance", MiniCloudButton.Type.PRIMARY));
        actionsPanel.add(new MiniCloudButton("Create Bucket",    MiniCloudButton.Type.PRIMARY));
        actionsPanel.add(new MiniCloudButton("Add User",         MiniCloudButton.Type.PRIMARY));
        actionsPanel.add(new MiniCloudButton("View Logs",        MiniCloudButton.Type.OUTLINE));
        content.add(actionsPanel);

        // 3. Recent Activity Table
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBackground(Color.WHITE);
        tableContainer.setBorder(BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY, 1, true));

        JLabel tableTitle = new JLabel("Recent Activity");
        tableTitle.setFont(ThemeConstants.getFont(15, Font.BOLD));
        tableTitle.setBorder(new EmptyBorder(16, 16, 10, 16));
        tableContainer.add(tableTitle, BorderLayout.NORTH);

        String[] cols = {"Timestamp", "Service", "Resource", "Action", "Status"};
        activityModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        MiniCloudTable activityTable = new MiniCloudTable();
        activityTable.setModel(activityModel);
        
        JScrollPane scroll = new JScrollPane(activityTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        tableContainer.add(scroll, BorderLayout.CENTER);
        
        content.add(tableContainer);

        add(content, BorderLayout.CENTER);
    }

    public void refresh() {
        greetingLabel.setText("Welcome back, " + apiClient.getUsername() + "  |  Region: local-dev-1");
        
        new SwingWorker<Void, Void>() {
            private JsonNode metrics;
            private int ec2Count, s3Count, iamCount;

            @Override
            protected Void doInBackground() throws Exception {
                metrics = apiClient.getData(apiClient.get("/cloudwatch/system"));
                try { ec2Count = apiClient.getData(apiClient.get("/ec2/instances")).size(); } catch (Exception e) { ec2Count = 0; }
                try { s3Count  = apiClient.getData(apiClient.get("/s3/buckets")).size();   } catch (Exception e) { s3Count = 0; }
                try { iamCount  = apiClient.getData(apiClient.get("/iam/users")).size();    } catch (Exception e) { iamCount = 0; }
                return null;
            }

            @Override
            protected void done() {
                ec2Card.setValue(String.valueOf(ec2Count));
                s3Card.setValue(String.valueOf(s3Count));
                iamCard.setValue(String.valueOf(iamCount));
                
                if (metrics != null && metrics.has("cpuPercent")) {
                    cpuCard.setValue(String.format("%.1f %%", metrics.get("cpuPercent").asDouble()));
                } else {
                    cpuCard.setValue("—");
                }

                // Mock some recent activity if table is empty
                if (activityModel.getRowCount() == 0) {
                    addActivity("Recently", "IAM", "admin", "Login", "Success");
                }
            }
        }.execute();
    }

    private void addActivity(String time, String service, String res, String action, String status) {
        activityModel.insertRow(0, new Object[]{time, service, res, action, status});
    }
}
