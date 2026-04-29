package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import javax.swing.Timer;
import javax.swing.SwingWorker;

/**
 * Dashboard panel — live overview of all services and key metrics.
 */
public class DashboardPanel extends JPanel {

    private JLabel ec2CountLabel, s3CountLabel, rdsCountLabel, lambdaCountLabel;
    private JLabel cpuLabel, memLabel, reqLabel;
    private JTextArea auditFeed;
    private Timer refreshTimer;

    public DashboardPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(16, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        scheduleRefresh();
    }

    private void buildUI() {
        // Page title
        JLabel title = new JLabel("Dashboard");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(0, 0, 16, 0));
        add(title, BorderLayout.NORTH);

        // Main grid
        JPanel grid = new JPanel(new GridLayout(2, 2, 16, 16));
        grid.setOpaque(false);

        grid.add(buildServiceCard("EC2 Instances", "0",  "⚙", SwingLauncher.AWS_BLUE));
        grid.add(buildServiceCard("S3 Buckets",    "0",  "🪣", SwingLauncher.AWS_ORANGE));
        grid.add(buildServiceCard("RDS Databases", "0",  "🗄", SwingLauncher.AWS_GREEN));
        grid.add(buildServiceCard("Lambda Funcs",  "0",  "λ", new Color(0xA7, 0x85, 0xFF)));

        // Store label refs
        Component[] cards = grid.getComponents();
        ec2CountLabel  = getCountLabel((JPanel) cards[0]);
        s3CountLabel   = getCountLabel((JPanel) cards[1]);
        rdsCountLabel  = getCountLabel((JPanel) cards[2]);
        lambdaCountLabel = getCountLabel((JPanel) cards[3]);

        // Right: metrics + recent audit
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 16));
        right.setOpaque(false);
        right.add(buildMetricsCard());
        right.add(buildAuditFeed());

        JPanel center = new JPanel(new GridLayout(1, 2, 16, 0));
        center.setOpaque(false);
        center.add(grid);
        center.add(right);
        add(center, BorderLayout.CENTER);

        refresh();
    }

    private JPanel buildServiceCard(String name, String count, String icon, Color accent) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(SwingLauncher.AWS_PANEL_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER),
            new EmptyBorder(20, 24, 20, 24)));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameLabel.setForeground(SwingLauncher.AWS_TEXT_DIM);

        JLabel countLabel = new JLabel(count);
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        countLabel.setForeground(accent);
        countLabel.putClientProperty("countLabel", true);

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        textPanel.setOpaque(false);
        textPanel.add(nameLabel);
        textPanel.add(countLabel);

        card.add(iconLabel,  BorderLayout.WEST);
        card.add(textPanel,  BorderLayout.CENTER);

        // Colored left border strip
        JPanel strip = new JPanel();
        strip.setBackground(accent);
        strip.setPreferredSize(new Dimension(4, 0));
        card.add(strip, BorderLayout.WEST);

        return card;
    }

    private JLabel getCountLabel(JPanel card) {
        for (Component c : getAllComponents(card)) {
            if (c instanceof JLabel l && l.getClientProperty("countLabel") != null) return l;
        }
        return new JLabel();
    }

    private JPanel buildMetricsCard() {
        JPanel card = new JPanel(new GridLayout(3, 1, 8, 8));
        card.setBackground(SwingLauncher.AWS_PANEL_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER),
            new EmptyBorder(16, 20, 16, 20)));

        cpuLabel = metricRow(card, "CPU Usage",    "—%",    SwingLauncher.AWS_ORANGE);
        memLabel = metricRow(card, "Memory",       "—%",    SwingLauncher.AWS_BLUE);
        reqLabel = metricRow(card, "Requests/min", "—",     SwingLauncher.AWS_GREEN);
        return card;
    }

    private JLabel metricRow(JPanel parent, String name, String value, Color color) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel nameLbl = new JLabel(name);
        nameLbl.setForeground(SwingLauncher.AWS_TEXT_DIM);
        nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JLabel valLbl = new JLabel(value);
        valLbl.setForeground(color);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        row.add(nameLbl, BorderLayout.WEST);
        row.add(valLbl,  BorderLayout.EAST);
        parent.add(row);
        return valLbl;
    }

    private JPanel buildAuditFeed() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(SwingLauncher.AWS_PANEL_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER),
            new EmptyBorder(16, 16, 16, 16)));

        JLabel title = new JLabel("Recent Activity");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);

        auditFeed = new JTextArea();
        auditFeed.setEditable(false);
        auditFeed.setBackground(SwingLauncher.AWS_DARK_BG);
        auditFeed.setForeground(SwingLauncher.AWS_TEXT);
        auditFeed.setFont(new Font("Consolas", Font.PLAIN, 11));
        auditFeed.setText("Loading events...");

        JScrollPane scroll = new JScrollPane(auditFeed);
        scroll.setBorder(null);

        card.add(title,  BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void scheduleRefresh() {
        refreshTimer = new Timer(5000, e -> refresh());
        refreshTimer.setInitialDelay(5000);
        refreshTimer.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (refreshTimer != null) refreshTimer.stop();
    }

    private void refresh() {
        if (!ApiClient.isLoggedIn()) return;
        SwingWorker<java.util.Map<String, Object>, Void> worker = new SwingWorker<>() {
            @Override protected java.util.Map<String, Object> doInBackground() throws Exception {
                java.util.Map<String, Object> results = new java.util.HashMap<>();
                try {
                    JsonNode ec2 = ApiClient.get("/api/v1/compute/instances");
                    JsonNode d = ec2.has("data") ? ec2.get("data") : ec2;
                    if (d != null && d.isArray()) results.put("ec2", String.valueOf(d.size()));
                } catch (Exception ignored) {}
                try {
                    JsonNode s3 = ApiClient.get("/api/v1/storage/buckets/user/" + ApiClient.getSession().getUserId());
                    JsonNode d = s3.has("data") ? s3.get("data") : s3;
                    if (d != null && d.isArray()) results.put("s3", String.valueOf(d.size()));
                } catch (Exception ignored) {}
                try {
                    JsonNode rds = ApiClient.get("/api/v1/rds/instances");
                    JsonNode d = rds.has("data") ? rds.get("data") : rds;
                    if (d != null && d.isArray()) results.put("rds", String.valueOf(d.size()));
                } catch (Exception ignored) {}
                try {
                    JsonNode lam = ApiClient.get("/api/v1/lambda");
                    JsonNode d = lam.has("data") ? lam.get("data") : lam;
                    if (d != null && d.isArray()) results.put("lambda", String.valueOf(d.size()));
                } catch (Exception ignored) {}
                try {
                    JsonNode m = ApiClient.get("/api/v1/monitoring/metrics/current");
                    results.put("cpu", String.format("%.1f%%", m.path("cpuUsage").asDouble()));
                    results.put("mem", String.format("%.1f%%", m.path("heapUsedPercent").asDouble()));
                } catch (Exception ignored) {}
                try {
                    JsonNode audit = ApiClient.get("/api/v1/monitoring/audit?limit=8");
                    JsonNode d = audit.has("data") ? audit.get("data") : audit;
                    StringBuilder sb = new StringBuilder();
                    if (d != null && d.isArray()) {
                        d.forEach(e -> sb.append(
                            e.path("timestamp").asText("").substring(0, Math.min(16, e.path("timestamp").asText("").length()))
                            + "  " + e.path("action").asText() + "\n"));
                    }
                    results.put("audit", sb.toString());
                } catch (Exception ignored) {}
                return results;
            }
            @Override protected void done() {
                try {
                    java.util.Map<String, Object> r = get();
                    if (r.containsKey("ec2"))    ec2CountLabel.setText((String) r.get("ec2"));
                    if (r.containsKey("s3"))     s3CountLabel.setText((String) r.get("s3"));
                    if (r.containsKey("rds"))    rdsCountLabel.setText((String) r.get("rds"));
                    if (r.containsKey("lambda")) lambdaCountLabel.setText((String) r.get("lambda"));
                    if (r.containsKey("cpu"))    cpuLabel.setText((String) r.get("cpu"));
                    if (r.containsKey("mem"))    memLabel.setText((String) r.get("mem"));
                    if (r.containsKey("audit"))  auditFeed.setText((String) r.get("audit"));
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private java.util.List<Component> getAllComponents(Container c) {
        java.util.List<Component> list = new java.util.ArrayList<>();
        for (Component comp : c.getComponents()) {
            list.add(comp);
            if (comp instanceof Container) list.addAll(getAllComponents((Container) comp));
        }
        return list;
    }
}
