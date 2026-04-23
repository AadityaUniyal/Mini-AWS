package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CloudWatch Metrics Panel — live-updating bar charts and metric cards.
 */
public class MetricsPanel extends JPanel {

    private JLabel cpuVal, heapVal, reqVal, gcVal;
    private MetricBarPanel cpuBar, heapBar;
    private JTextArea rawJson;
    private Timer timer;

    public MetricsPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        startRefresh();
    }

    private void buildUI() {
        JLabel title = new JLabel("CloudWatch — Live Metrics");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        // 4 metric cards in a row
        JPanel cards = new JPanel(new GridLayout(1, 4, 16, 0));
        cards.setOpaque(false);
        cards.setPreferredSize(new Dimension(0, 110));

        cpuVal  = addMetricCard(cards, "CPU Usage",    "—%",    SwingLauncher.AWS_ORANGE);
        heapVal = addMetricCard(cards, "Heap Used",    "—%",    SwingLauncher.AWS_BLUE);
        reqVal  = addMetricCard(cards, "Total Requests","—",    SwingLauncher.AWS_GREEN);
        gcVal   = addMetricCard(cards, "GC Pauses",    "—ms",  new Color(0xA7, 0x85, 0xFF));

        // Live bars
        JPanel bars = new JPanel(new GridLayout(2, 1, 0, 16));
        bars.setOpaque(false);
        bars.setPreferredSize(new Dimension(0, 160));

        cpuBar  = new MetricBarPanel("CPU Usage (%)",    SwingLauncher.AWS_ORANGE);
        heapBar = new MetricBarPanel("Heap Usage (%)",   SwingLauncher.AWS_BLUE);
        bars.add(cpuBar);
        bars.add(heapBar);

        // Raw JSON view
        rawJson = new JTextArea(6, 0);
        rawJson.setEditable(false);
        rawJson.setBackground(SwingLauncher.AWS_DARK_BG);
        rawJson.setForeground(new Color(0x7C, 0xD7, 0x5A));
        rawJson.setFont(new Font("Consolas", Font.PLAIN, 12));
        rawJson.setText("Waiting for first data point...");

        JLabel rawTitle = new JLabel("Raw Metrics Payload");
        rawTitle.setForeground(SwingLauncher.AWS_TEXT_DIM);
        rawTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel rawPanel = new JPanel(new BorderLayout(0, 4));
        rawPanel.setOpaque(false);
        rawPanel.add(rawTitle, BorderLayout.NORTH);
        rawPanel.add(new JScrollPane(rawJson), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(bars,     BorderLayout.NORTH);
        center.add(rawPanel, BorderLayout.CENTER);

        add(title,  BorderLayout.NORTH);
        add(cards,  BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private JLabel addMetricCard(JPanel parent, String name, String initial, Color accent) {
        JPanel card = new JPanel(new GridLayout(3, 1, 4, 4));
        card.setBackground(SwingLauncher.AWS_PANEL_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER),
            new EmptyBorder(14, 16, 14, 16)));

        JPanel strip = new JPanel(); strip.setBackground(accent); strip.setPreferredSize(new Dimension(4, 0));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setForeground(SwingLauncher.AWS_TEXT_DIM);
        nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JLabel valLbl = new JLabel(initial);
        valLbl.setForeground(accent);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 26));

        JLabel liveLbl = new JLabel("● LIVE");
        liveLbl.setForeground(SwingLauncher.AWS_GREEN);
        liveLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));

        card.add(nameLbl); card.add(valLbl); card.add(liveLbl);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(strip, BorderLayout.WEST);
        wrapper.add(card,  BorderLayout.CENTER);
        parent.add(wrapper);

        return valLbl;
    }

    private void startRefresh() {
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                try {
                    JsonNode m = ApiClient.get("/monitoring/metrics/current");
                    SwingUtilities.invokeLater(() -> updateUI(m));
                } catch (Exception ignored) {}
            }
        }, 0, 3000);
    }

    private void updateUI(JsonNode m) {
        double cpu  = m.path("cpuUsage").asDouble();
        double heap = m.path("heapUsedPercent").asDouble();
        long   reqs = m.path("totalRequests").asLong();
        long   gc   = m.path("gcPauseMs").asLong(0);

        cpuVal.setText(String.format("%.1f%%", cpu));
        heapVal.setText(String.format("%.1f%%", heap));
        reqVal.setText(String.valueOf(reqs));
        gcVal.setText(gc + "ms");

        cpuBar.setValue((int) cpu);
        heapBar.setValue((int) heap);

        rawJson.setText(m.toPrettyString());
    }

    // ── Inner: Live Bar Chart ─────────────────────────────────────────────────

    static class MetricBarPanel extends JPanel {
        private final String metricName;
        private final Color barColor;
        private int value = 0;

        MetricBarPanel(String name, Color color) {
            this.metricName = name;
            this.barColor   = color;
            setBackground(SwingLauncher.AWS_PANEL_BG);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER),
                new EmptyBorder(10, 14, 10, 14)));
        }

        void setValue(int v) { this.value = Math.max(0, Math.min(100, v)); repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth() - 28, h = getHeight() - 28;
            int barH = 16;
            int barY = h / 2 + 4;

            // Label
            g2.setColor(SwingLauncher.AWS_TEXT_DIM);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.drawString(metricName, 0, barY - 8);

            // Track
            g2.setColor(SwingLauncher.AWS_DARK_BG);
            g2.fillRoundRect(0, barY, w, barH, 8, 8);

            // Fill
            int fillW = (int) (w * (value / 100.0));
            g2.setColor(barColor);
            if (fillW > 0) g2.fillRoundRect(0, barY, fillW, barH, 8, 8);

            // Value text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.drawString(value + "%", w + 6, barY + barH - 2);
        }
    }
}
