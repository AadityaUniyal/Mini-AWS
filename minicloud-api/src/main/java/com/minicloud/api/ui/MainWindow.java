package com.minicloud.api.ui;

import com.minicloud.api.ui.panels.*;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Main Swing window for MiniCloud Desktop Application.
 * Displays all services via individual tabbed panels.
 */
@Component
public class MainWindow extends JFrame {
    
    private JTextArea consoleArea;
    private JTabbedPane tabbedPane;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    
    public MainWindow() {
        initializeUI();
    }
    
    private void initializeUI() {
        setTitle("Mini-AWS Cloud Management Console");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // ── TOP HEADER ──────────────────────────────────────────
        JLabel header = new JLabel("  ☁ Mini-AWS Management Console", JLabel.LEFT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setOpaque(true);
        header.setBackground(new Color(35, 47, 62));
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 10));
        add(header, BorderLayout.NORTH);
        
        // ── MAIN SPLIT: TABS (top) + CONSOLE (bottom) ────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.80);
        splitPane.setDividerSize(8);
        
        // TABBED PANE (top half)
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabbedPane.setBackground(SwingLauncher.AWS_NAVY);
        
        // Register all functional panels
        tabbedPane.addTab("📊  Dashboard",     new DashboardPanel());
        tabbedPane.addTab("🪣  S3 Storage",    new S3Panel());
        tabbedPane.addTab("💻  EC2 Compute",    new Ec2Panel());
        tabbedPane.addTab("⚡  Lambda Exec",     new LambdaPanel());
        tabbedPane.addTab("🗄️  RDS Database",   new RdsPanel());
        tabbedPane.addTab("🌐  VPC Network",    new VpcPanel());
        tabbedPane.addTab("🗺️  Route 53 DNS",   new Route53Panel());
        tabbedPane.addTab("👤  IAM Security",   new IamPanel());
        tabbedPane.addTab("📋  Task Manager",   new TaskPanel());
        tabbedPane.addTab("📈  Metrics",        new MetricsPanel());
        tabbedPane.addTab("🪵  Log Streams",    new CloudWatchLogsPanel());
        tabbedPane.addTab("💳  Billing",        new BillingPanel());
        tabbedPane.addTab("📋  CloudTrail Log", new AuditPanel());
        
        splitPane.setTopComponent(tabbedPane);
        
        // CONSOLE (bottom half)
        splitPane.setBottomComponent(buildConsolePanel());
        
        add(splitPane, BorderLayout.CENTER);
        
        // ── STATUS BAR ──────────────────────────────────────────
        JLabel statusBar = new JLabel("  ● Connected to Neon PostgreSQL (ep-wispy-lab-apla5k4t.us-east-1.aws.neon.tech)");
        statusBar.setFont(new Font("Consolas", Font.PLAIN, 12));
        statusBar.setForeground(new Color(0, 180, 100));
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusBar.setOpaque(true);
        statusBar.setBackground(new Color(240, 240, 240));
        add(statusBar, BorderLayout.SOUTH);
        
        // Initial log messages
        log("Mini-AWS Desktop Console started successfully");
        log("Connected to Neon PostgreSQL database");
        log("REST API and real-time metrics engine running on http://localhost:8080");
    }
    
    // ── BUILD CONSOLE PANEL ─────────────────────────────────────
    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel consoleHeader = new JLabel("  CONSOLE OUTPUT");
        consoleHeader.setFont(new Font("Consolas", Font.BOLD, 13));
        consoleHeader.setOpaque(true);
        consoleHeader.setBackground(new Color(30, 30, 30));
        consoleHeader.setForeground(new Color(0, 255, 128));
        consoleHeader.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        panel.add(consoleHeader, BorderLayout.NORTH);
        
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        consoleArea.setBackground(new Color(20, 20, 20));
        consoleArea.setForeground(new Color(0, 255, 128));
        consoleArea.setCaretColor(Color.GREEN);
        consoleArea.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        
        JScrollPane scroll = new JScrollPane(consoleArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));
        panel.add(scroll, BorderLayout.CENTER);
        
        // Clear console button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(new Color(30, 30, 30));
        JButton clearBtn = new JButton("Clear Console");
        clearBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clearBtn.addActionListener(e -> {
            consoleArea.setText("");
            log("Console cleared");
        });
        clearBtn.setBackground(new Color(60, 60, 60));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.setFocusPainted(false);
        buttonPanel.add(clearBtn);
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    // ── PUBLIC: LOG TO CONSOLE ──────────────────────────────────
    /**
     * Logs a message to the console with timestamp.
     * Thread-safe for use from service classes.
     * 
     * @param message The message to log
     */
    public void log(String message) {
        String timestamp = timeFormat.format(new Date());
        SwingUtilities.invokeLater(() -> {
            consoleArea.append("[" + timestamp + "] " + message + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }
}
