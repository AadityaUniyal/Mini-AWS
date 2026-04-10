package com.minicloud.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.components.MiniCloudTable;
import com.minicloud.ui.components.StatusBadge;
import com.minicloud.ui.dialog.LaunchInstanceDialog;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Redesigned EC2Panel — AWS style table + detail panel.
 */
public class EC2Panel extends JPanel {

    private final ApiClient apiClient;
    private DefaultTableModel tableModel;
    private MiniCloudTable instanceTable;
    private JLabel breadcrumbLabel;
    private JPanel detailPanel;
    private JTabbedPane detailTabs;
    
    // Detail components
    private JLabel detId, detState, detType, detLaunch, detPid, detUptime;
    private JTextArea detLogs;

    private JCheckBox hideTerminatedCheck;
    private Timer pollTimer;

    public EC2Panel(ApiClient apiClient) {
        this.apiClient = apiClient;
        initUI();
        startPolling();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeConstants.BG_LIGHT);
        setBorder(new EmptyBorder(20, 30, 20, 30));

        // ── Header ───────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        breadcrumbLabel = new JLabel("EC2 > Instances");
        breadcrumbLabel.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        breadcrumbLabel.setForeground(ThemeConstants.TEXT_MUTED);
        header.add(breadcrumbLabel, BorderLayout.NORTH);

        JLabel title = new JLabel("Instances");
        title.setFont(ThemeConstants.getFont(20, Font.BOLD));
        titleBlock(header, title);

        // Buttons & Filter
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);
        toolbar.add(new MiniCloudButton("Launch Instance", MiniCloudButton.Type.PRIMARY));
        toolbar.add(new MiniCloudButton("Instance State ▾", MiniCloudButton.Type.OUTLINE));
        
        hideTerminatedCheck = new JCheckBox("Hide terminated");
        hideTerminatedCheck.setOpaque(false);
        hideTerminatedCheck.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        hideTerminatedCheck.addActionListener(e -> refresh());
        toolbar.add(hideTerminatedCheck);
        
        MiniCloudButton refreshBtn = new MiniCloudButton("↻", MiniCloudButton.Type.OUTLINE);
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);
        
        header.add(toolbar, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // ── Main Split Pane ──────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(350);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Top: Table
        tableModel = new DefaultTableModel(new String[]{"Instance ID", "Name", "Type", "State", "PID", "Uptime", "Launched"}, 0);
        instanceTable = new MiniCloudTable();
        instanceTable.setModel(tableModel);
        
        // Custom State Renderer
        instanceTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                String state = v != null ? v.toString() : "UNKNOWN";
                StatusBadge.ColorTheme theme;
                switch (state) {
                    case "RUNNING" -> theme = StatusBadge.ColorTheme.GREEN;
                    case "PENDING" -> theme = StatusBadge.ColorTheme.ORANGE;
                    case "STOPPED" -> theme = StatusBadge.ColorTheme.GRAY;
                    case "TERMINATED" -> theme = StatusBadge.ColorTheme.RED;
                    default -> theme = StatusBadge.ColorTheme.GRAY;
                }
                return new StatusBadge(state, theme);
            }
        });

        instanceTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDetailPanel();
        });

        JScrollPane tableScroll = new JScrollPane(instanceTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY));
        splitPane.setTopComponent(tableScroll);

        // Bottom: Detail Panel
        detailPanel = createDetailPanel();
        splitPane.setBottomComponent(detailPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private void startPolling() {
        pollTimer = new Timer(3000, e -> refresh());
        pollTimer.start();
    }

    public void stopPolling() {
        if (pollTimer != null) pollTimer.stop();
    }

    private void titleBlock(JPanel header, JLabel title) {
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        header.add(titlePanel, BorderLayout.CENTER);
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeConstants.BORDER_GRAY));

        detailTabs = new JTabbedPane();
        detailTabs.setFont(ThemeConstants.getFont(12, Font.BOLD));

        // Tab 1: Details
        JPanel details = new JPanel(new GridBagLayout());
        details.setBackground(Color.WHITE);
        details.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 20);

        detId = addDetailField(details, "Instance ID:", 0, gbc);
        detState = addDetailField(details, "Instance state:", 1, gbc);
        detType = addDetailField(details, "Instance type:", 2, gbc);
        detLaunch = addDetailField(details, "Launch time:", 3, gbc);
        detPid = addDetailField(details, "PID:", 4, gbc);
        detUptime = addDetailField(details, "Uptime:", 5, gbc);

        detailTabs.addTab("Details", details);

        // Tab 2: Logs
        detLogs = new JTextArea();
        detLogs.setEditable(false);
        detLogs.setFont(new Font("Monospaced", Font.PLAIN, 12));
        detailTabs.addTab("Logs", new JScrollPane(detLogs));

        panel.add(detailTabs, BorderLayout.CENTER);
        return panel;
    }

    private JLabel addDetailField(JPanel p, String label, int row, GridBagConstraints gbc) {
        gbc.gridy = row;
        gbc.gridx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(ThemeConstants.TEXT_MUTED);
        p.add(l, gbc);

        gbc.gridx = 1;
        JLabel v = new JLabel("—");
        v.setForeground(ThemeConstants.TEXT_DARK);
        p.add(v, gbc);
        return v;
    }

    public void refresh() {
        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return apiClient.getData(apiClient.get("/ec2/instances"));
            }
            @Override protected void done() {
                try {
                    JsonNode res = get();
                    // Save selection
                    int selectedRow = instanceTable.getSelectedRow();
                    String selectedId = null;
                    if (selectedRow >= 0) selectedId = tableModel.getValueAt(selectedRow, 0).toString();

                    tableModel.setRowCount(0);
                    int total = 0;
                    if (res.isArray()) {
                        for (JsonNode i : res) {
                            String state = i.has("state") ? i.get("state").asText() : "UNKNOWN";
                            if (hideTerminatedCheck.isSelected() && "TERMINATED".equals(state)) continue;
                            
                            tableModel.addRow(new Object[]{
                                i.get("id").asText().substring(0, 8),
                                i.has("name") ? i.get("name").asText() : "unnamed",
                                i.has("type") ? i.get("type").asText() : "MICRO",
                                state,
                                i.has("pid") && !i.get("pid").isNull() ? i.get("pid").asInt() : "—",
                                i.has("uptimeSeconds") ? i.get("uptimeSeconds").asLong() : 0,
                                i.has("launchedAt") ? i.get("launchedAt").asText().substring(0, 10) : "—"
                            });
                            total++;
                        }
                    }
                    
                    // Restore selection
                    if (selectedId != null) {
                        for (int r = 0; r < tableModel.getRowCount(); r++) {
                            if (tableModel.getValueAt(r, 0).equals(selectedId)) {
                                instanceTable.setRowSelectionInterval(r, r);
                                break;
                            }
                        }
                    }
                    
                    breadcrumbLabel.setText("EC2 > Instances (" + total + ")");
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void updateDetailPanel() {
        int row = instanceTable.getSelectedRow();
        if (row < 0) return;
        
        detId.setText(tableModel.getValueAt(row, 0).toString());
        detState.setText(tableModel.getValueAt(row, 3).toString());
        detType.setText(tableModel.getValueAt(row, 2).toString());
        detPid.setText(tableModel.getValueAt(row, 4).toString());
        detUptime.setText(tableModel.getValueAt(row, 5).toString());
        detLogs.setText("Retrieving logs for " + detId.getText() + "...");
    }
}
