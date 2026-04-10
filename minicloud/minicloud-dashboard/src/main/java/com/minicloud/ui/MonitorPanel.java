package com.minicloud.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.components.SectionCard;
import com.minicloud.ui.components.NotificationBar;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced MonitorPanel — AWS CloudWatch style with history range and log filtering.
 */
public class MonitorPanel extends JPanel {

    private final ApiClient apiClient;
    
    private SectionCard cpuCard, heapCard, diskCard;
    private JTextArea logArea;
    private JTextField logSearchField;
    private JPanel alarmBanner;
    private JLabel alarmText;
    
    private List<Double> cpuHistory = new ArrayList<>();
    private List<String> rawLogs = new ArrayList<>();
    
    private int visibleRange = 60; // default 5 mins (60 points * 5s)
    private Timer refreshTimer;

    public MonitorPanel(ApiClient apiClient) {
        this.apiClient = apiClient;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBackground(ThemeConstants.BG_LIGHT);
        setBorder(new EmptyBorder(20, 30, 20, 30));

        // ── Header & Alarms ───────────────────────────────────
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel breadcrumbs = new JLabel("CloudWatch > Overview");
        breadcrumbs.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        breadcrumbs.setForeground(ThemeConstants.TEXT_MUTED);
        top.add(breadcrumbs, BorderLayout.NORTH);

        alarmBanner = new JPanel(new FlowLayout(FlowLayout.LEFT));
        alarmBanner.setBackground(ThemeConstants.GREEN_BG);
        alarmBanner.setBorder(BorderFactory.createLineBorder(ThemeConstants.GREEN_TEXT));
        alarmText = new JLabel("✔ All systems operational");
        alarmText.setForeground(ThemeConstants.GREEN_TEXT);
        alarmText.setFont(ThemeConstants.getFont(13, Font.BOLD));
        alarmBanner.add(alarmText);
        top.add(alarmBanner, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);

        // ── Main Content Area ─────────────────────────────────
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        // 1. Metrics Cards
        JPanel cards = new JPanel(new GridLayout(1, 3, 16, 0));
        cards.setOpaque(false);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        cpuCard = new SectionCard("CPU Utilization");
        heapCard = new SectionCard("Heap Memory");
        diskCard = new SectionCard("Disk Usage");
        
        cards.add(cpuCard);
        cards.add(heapCard);
        cards.add(diskCard);
        content.add(cards);
        content.add(Box.createVerticalStrut(20));

        // 2. Real-time Graph Section
        JPanel graphContainer = new JPanel(new BorderLayout());
        graphContainer.setBackground(Color.WHITE);
        graphContainer.setBorder(BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY));
        graphContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        JPanel graphHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        graphHeader.setOpaque(false);
        graphHeader.add(new JLabel("Range: "));
        
        addRangeButton(graphHeader, "5m", 60);
        addRangeButton(graphHeader, "15m", 180);
        addRangeButton(graphHeader, "1h", 720);

        JPanel graphPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawGraph((Graphics2D) g);
            }
        };
        graphPanel.setBackground(Color.WHITE);
        graphPanel.setPreferredSize(new Dimension(0, 200));

        graphContainer.add(graphHeader, BorderLayout.NORTH);
        graphContainer.add(graphPanel, BorderLayout.CENTER);
        content.add(graphContainer);
        content.add(Box.createVerticalStrut(20));

        // 3. Log Viewer Section
        JPanel logs = new JPanel(new BorderLayout());
        logs.setBackground(Color.WHITE);
        logs.setBorder(BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY));
        
        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        logHeader.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        JLabel logTitle = new JLabel("Log Events");
        logTitle.setFont(ThemeConstants.getFont(14, Font.BOLD));
        logHeader.add(logTitle, BorderLayout.WEST);

        logSearchField = new JTextField(20);
        logSearchField.setToolTipText("Filter logs by keyword...");
        logSearchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) { filterLogs(); }
        });
        logHeader.add(logSearchField, BorderLayout.EAST);
        logs.add(logHeader, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(0x111111));
        logArea.setForeground(new Color(0x00FF88));
        
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(0, 250));
        logs.add(scroll, BorderLayout.CENTER);
        
        content.add(logs);
        add(new JScrollPane(content), BorderLayout.CENTER);
    }

    private void addRangeButton(JPanel p, String text, int range) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeConstants.getFont(11, Font.PLAIN));
        btn.addActionListener(e -> {
            this.visibleRange = range;
            repaint();
        });
        p.add(btn);
    }

    private void drawGraph(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight() - 20;
        
        // Grid lines
        g2.setColor(ThemeConstants.BG_LIGHT);
        for (int i = 0; i < 5; i++) {
            int y = h - (i * h / 5) + 10;
            g2.drawLine(0, y, w, y);
        }

        if (cpuHistory.size() < 2) return;

        g2.setColor(ThemeConstants.ACTIVE_BLUE);
        g2.setStroke(new BasicStroke(2));
        
        // Use visibleRange for X-scaling
        double xStep = (double) w / (visibleRange - 1);
        
        // Only draw points within the visible range from the end
        int startIdx = Math.max(0, cpuHistory.size() - visibleRange);
        int lastX = -1, lastY = -1;
        
        for (int i = startIdx; i < cpuHistory.size(); i++) {
            int x = (int) ((i - startIdx) * xStep);
            int y = h - (int) (cpuHistory.get(i) * h / 100) + 10;
            if (lastX != -1) {
                g2.drawLine(lastX, lastY, x, y);
            }
            lastX = x; lastY = y;
        }
    }

    public void startRefresh() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(5000, e -> fetch());
        }
        refreshTimer.start();
    }

    public void stopRefresh() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    private void fetch() {
        new SwingWorker<Void, Void>() {
            private JsonNode sys;
            private java.util.List<String> logs;

            @Override protected Void doInBackground() throws Exception {
                // Fetch full system metrics (includes history)
                sys = apiClient.getData(apiClient.get("/cloudwatch/system"));
                JsonNode logNode = apiClient.getData(apiClient.get("/cloudwatch/logs?lines=100"));
                if (logNode.isArray()) {
                    logs = new java.util.ArrayList<>();
                    for (JsonNode l : logNode) logs.add(l.asText());
                }
                return null;
            }

            @Override protected void done() {
                if (sys != null) {
                    double cpu = sys.get("cpuPercent").asDouble();
                    cpuCard.setValue(String.format("%.1f%%", cpu));
                    
                    // Update entire history from server
                    if (sys.has("cpuHistory")) {
                        cpuHistory.clear();
                        for (JsonNode point : sys.get("cpuHistory")) {
                            cpuHistory.add(point.asDouble());
                        }
                    } else {
                        cpuHistory.add(cpu);
                        if (cpuHistory.size() > 720) cpuHistory.remove(0);
                    }
                    
                    heapCard.setValue(sys.get("heapUsedMb").asInt() + " MB");
                    diskCard.setValue(sys.get("diskUsedGb").asInt() + " GB");
                    
                    updateAlarms(cpu);
                    repaint();
                }
                if (logs != null) {
                    rawLogs = logs;
                    filterLogs();
                }
            }
        }.execute();
    }

    private void filterLogs() {
        String query = logSearchField.getText().toLowerCase();
        List<String> filtered = rawLogs.stream()
                .filter(l -> l.toLowerCase().contains(query))
                .collect(Collectors.toList());
        
        StringBuilder sb = new StringBuilder();
        filtered.forEach(l -> sb.append(l).append("\n"));
        logArea.setText(sb.toString());
        if (query.isEmpty()) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    private void updateAlarms(double cpu) {
        if (cpu > 80) {
            alarmBanner.setBackground(ThemeConstants.RED_BG);
            alarmBanner.setBorder(BorderFactory.createLineBorder(ThemeConstants.RED_TEXT));
            alarmText.setText("✖ Critical: High CPU detected (" + String.format("%.1f%%", cpu) + ")");
            alarmText.setForeground(ThemeConstants.RED_TEXT);
        } else if (cpu > 60) {
            alarmBanner.setBackground(ThemeConstants.ORANGE_BG);
            alarmBanner.setBorder(BorderFactory.createLineBorder(ThemeConstants.ORANGE_TEXT));
            alarmText.setText("⚠ Warning: Elevated CPU load");
            alarmText.setForeground(ThemeConstants.ORANGE_TEXT);
        } else {
            alarmBanner.setBackground(ThemeConstants.GREEN_BG);
            alarmBanner.setBorder(BorderFactory.createLineBorder(ThemeConstants.GREEN_TEXT));
            alarmText.setText("✔ All systems operational");
            alarmText.setForeground(ThemeConstants.GREEN_TEXT);
        }
    }
}
