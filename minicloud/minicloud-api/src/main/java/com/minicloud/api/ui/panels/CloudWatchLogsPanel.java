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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class CloudWatchLogsPanel extends JPanel {

    private JTable streamTable;
    private DefaultTableModel streamModel;
    private JTextArea logViewer;
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public CloudWatchLogsPanel() {
        setLayout(new BorderLayout());
        setBackground(SwingLauncher.AWS_DARK_BG);
        buildUI();
        refreshStreams();
    }

    private void buildUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SwingLauncher.AWS_NAVY);
        header.setBorder(new EmptyBorder(10, 20, 10, 20));
        
        JLabel title = new JLabel("CloudWatch Logs");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.add(title, BorderLayout.WEST);
        
        JButton refreshBtn = new JButton("↻ Refresh");
        refreshBtn.addActionListener(e -> refreshStreams());
        header.add(refreshBtn, BorderLayout.EAST);
        
        add(header, BorderLayout.NORTH);

        // Content - Split Pane
        streamModel = new DefaultTableModel(new String[]{"Log Group", "Log Stream", "Last Event"}, 0);
        streamTable = new JTable(streamModel);
        streamTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadEvents();
        });
        
        JScrollPane streamScroll = new JScrollPane(streamTable);
        streamScroll.setPreferredSize(new Dimension(400, 0));

        logViewer = new JTextArea();
        logViewer.setBackground(Color.BLACK);
        logViewer.setForeground(new Color(0, 255, 0)); // Classic green log
        logViewer.setFont(new Font("Consolas", Font.PLAIN, 12));
        logViewer.setEditable(false);
        logViewer.setMargin(new Insets(10, 10, 10, 10));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, streamScroll, new JScrollPane(logViewer));
        split.setDividerLocation(450);
        
        add(split, BorderLayout.CENTER);
    }

    private void refreshStreams() {
        if (!ApiClient.isLoggedIn()) return;
        String accountId = ApiClient.getSession().getAccountId();
        
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/logs/streams/" + accountId);
            }
            @Override protected void done() {
                try {
                    JsonNode data = get().get("data");
                    streamModel.setRowCount(0);
                    for (JsonNode s : data) {
                        streamModel.addRow(new Object[]{
                            s.get("logGroupName").asText(),
                            s.get("logStreamName").asText(),
                            s.get("lastEventAt").asText(),
                            s.get("id").asText() // Hidden Column usually but we can store it
                        });
                    }
                } catch (Exception e) { log.error("Failed to fetch streams: {}", e.getMessage()); }
            }
        };
        worker.execute();
    }

    private void loadEvents() {
        int row = streamTable.getSelectedRow();
        if (row < 0) return;
        
        // This is a bit hacky - we need the ID. Let's say we refresh again to get it OR store in a map.
        // For now let's assume we find it by name or similar. 
        // Better: Find the stream ID from a hidden data map.
        String accountId = ApiClient.getSession().getAccountId();
        
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                // We'll search by row for stream ID.
                JsonNode streams = ApiClient.get("/api/logs/streams/" + accountId).get("data");
                String streamId = streams.get(row).get("id").asText();
                return ApiClient.get("/api/logs/events/" + streamId);
            }
            @Override protected void done() {
                try {
                    JsonNode events = get().get("data");
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode e : events) {
                        long ts = e.get("timestamp").asLong();
                        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                        sb.append("[").append(dt.format(DF)).append("] ").append(e.get("message").asText()).append("\n");
                    }
                    logViewer.setText(sb.toString());
                    logViewer.setCaretPosition(sb.length());
                } catch (Exception e) { log.error("Failed to fetch events: {}", e.getMessage()); }
            }
        };
        worker.execute();
    }
}
