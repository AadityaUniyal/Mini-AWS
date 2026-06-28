package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Slf4j
public class TaskPanel extends JPanel {

    private DefaultTableModel taskModel;
    private JTable taskTable;
    private WebSocket webSocket;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] TASK_COLS = {"Task ID", "Type", "Description", "Status", "Progress", "Start Time", "End Time"};

    public TaskPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        loadTasks();
        connectWebSocket();
    }

    private void buildUI() {
        JLabel title = new JLabel("Task Manager — Background Job Tracking");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(title, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btn("Cancel Selected", SwingLauncher.AWS_RED, this::cancelSelectedTask));
        btnPanel.add(btn("Refresh", SwingLauncher.AWS_BLUE, this::loadTasks));
        headerPanel.add(btnPanel, BorderLayout.EAST);

        taskModel = new DefaultTableModel(TASK_COLS, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        taskTable = new JTable(taskModel);
        taskTable.setBackground(SwingLauncher.AWS_DARK_BG);
        taskTable.setForeground(SwingLauncher.AWS_TEXT);
        taskTable.setSelectionBackground(SwingLauncher.AWS_NAVY);
        taskTable.setGridColor(SwingLauncher.AWS_BORDER);
        taskTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        taskTable.setRowHeight(28);
        taskTable.getTableHeader().setBackground(SwingLauncher.AWS_NAVY);
        taskTable.getTableHeader().setForeground(SwingLauncher.AWS_ORANGE);
        taskTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        taskTable.setFillsViewportHeight(true);

        JScrollPane scroll = new JScrollPane(taskTable);
        scroll.setBackground(SwingLauncher.AWS_DARK_BG);
        scroll.getViewport().setBackground(SwingLauncher.AWS_DARK_BG);
        scroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        add(headerPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private void loadTasks() {
        if (!ApiClient.isLoggedIn()) return;
        String userId = ApiClient.getSession().getUserId();
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override
            protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/tasks/user/" + userId);
            }
            @Override
            protected void done() {
                try {
                    taskModel.setRowCount(0);
                    JsonNode resp = get();
                    JsonNode data = resp.get("data");
                    if (data != null && data.isArray()) {
                        data.forEach(n -> taskModel.addRow(new Object[]{
                                n.path("id").asText("—"),
                                n.path("type").asText("—"),
                                n.path("description").asText("—"),
                                n.path("status").asText("—"),
                                n.path("progress").asInt(0) + "%",
                                n.path("startTime").asText("—"),
                                n.path("endTime").asText("—")
                        }));
                    }
                } catch (Exception e) {
                    log.error("Task Refresh Error: {}", e.getMessage());
                }
            }
        };
        w.execute();
    }

    private void cancelSelectedTask() {
        int row = taskTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a running task to cancel.");
            return;
        }
        String id = (String) taskModel.getValueAt(row, 0);
        String status = (String) taskModel.getValueAt(row, 3);
        if (!"RUNNING".equalsIgnoreCase(status) && !"PENDING".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Only PENDING or RUNNING tasks can be cancelled.");
            return;
        }

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                ApiClient.post("/api/v1/tasks/" + id + "/cancel", null);
                return null;
            }
            @Override
            protected void done() {
                loadTasks();
            }
        };
        w.execute();
    }

    private void connectWebSocket() {
        HttpClient client = HttpClient.newHttpClient();
        CompletableFutureListener listener = new CompletableFutureListener();
        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8080/ws-events/tasks"), listener)
                .thenAccept(ws -> this.webSocket = ws);
    }

    private class CompletableFutureListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("Task WebSocket connected");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                SwingUtilities.invokeLater(() -> handleRealtimeTaskMessage(payload));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("Task WebSocket error: {}", error.getMessage());
        }
    }

    private void handleRealtimeTaskMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String id = node.path("id").asText();
            String status = node.path("status").asText();
            int progress = node.path("progress").asInt();
            String endTime = node.path("endTime").asText("—");

            // Look for existing row in table and update
            boolean found = false;
            for (int i = 0; i < taskModel.getRowCount(); i++) {
                if (id.equals(taskModel.getValueAt(i, 0))) {
                    taskModel.setValueAt(status, i, 3);
                    taskModel.setValueAt(progress + "%", i, 4);
                    taskModel.setValueAt(endTime, i, 6);
                    found = true;
                    break;
                }
            }
            if (!found) {
                loadTasks(); // reload list if new task
            }
        } catch (Exception e) {
            log.error("Failed to parse realtime update", e);
        }
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
}
