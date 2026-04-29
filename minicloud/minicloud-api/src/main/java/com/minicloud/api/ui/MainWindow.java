package com.minicloud.api.ui;

import com.minicloud.api.ui.panels.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Main application window. Uses a sidebar navigation + content area layout,
 * styled to match the AWS Management Console aesthetic.
 */
public class MainWindow extends JFrame {

    private JPanel contentArea;
    private JPanel activeNavButton;
    private final CardLayout cardLayout = new CardLayout();

    public MainWindow() {
        super("MiniCloud Management Console");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(SwingLauncher.AWS_DARK_BG);

        root.add(buildTopBar(),  BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);

        setContentPane(root);
    }

    // ── Top Bar ───────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(SwingLauncher.AWS_NAVY);
        bar.setPreferredSize(new Dimension(0, 48));
        bar.setBorder(new EmptyBorder(0, 20, 0, 20));

        JLabel logo = new JLabel("☁  MiniCloud");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logo.setForeground(SwingLauncher.AWS_ORANGE);

        ApiClient.UserSession session = ApiClient.getSession();
        String username = (session != null && session.getUsername() != null) ? session.getUsername() : "admin";
        String accountId = (session != null && session.getAccountId() != null) ? session.getAccountId() : "000000000000";
        String userDisplay = username + " @ " + accountId + "  ▼";
        
        JLabel userInfo = new JLabel(userDisplay);
        userInfo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userInfo.setForeground(SwingLauncher.AWS_TEXT);
        userInfo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userInfo.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int opt = JOptionPane.showConfirmDialog(MainWindow.this,
                    "Sign out of MiniCloud?", "Sign Out", JOptionPane.YES_NO_OPTION);
                if (opt == JOptionPane.YES_OPTION) {
                    ApiClient.logout();
                    dispose();
                    new LoginDialog(null).setVisible(true);
                }
            }
        });

        JLabel regionLabel = new JLabel("us-east-1 (N. Virginia)");
        regionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        regionLabel.setForeground(SwingLauncher.AWS_TEXT);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        right.add(regionLabel);
        right.add(userInfo);

        bar.add(logo, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SwingLauncher.AWS_NAVY);
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(new EmptyBorder(8, 0, 0, 0));

        addNavGroup(sidebar, "OVERVIEW");
        addNavItem(sidebar, "🏠  Dashboard",  "dashboard");

        addNavGroup(sidebar, "COMPUTE");
        addNavItem(sidebar, "⚙  EC2",        "ec2");
        addNavItem(sidebar, "λ  Lambda",      "lambda");

        addNavGroup(sidebar, "DATABASE");
        addNavItem(sidebar, "🗄  RDS",        "rds");

        addNavGroup(sidebar, "NETWORKING");
        addNavItem(sidebar, "🌐  VPC",         "vpc");
        addNavItem(sidebar, "🔗  Route 53",    "route53");

        addNavGroup(sidebar, "STORAGE");
        addNavItem(sidebar, "🪣  S3",         "s3");

        addNavGroup(sidebar, "SECURITY");
        addNavItem(sidebar, "🔑  IAM",        "iam");

        addNavGroup(sidebar, "MONITORING");
        addNavItem(sidebar, "📋  CloudTrail", "audit");
        addNavItem(sidebar, "📈  CloudWatch", "metrics");
        addNavItem(sidebar, "🗒  Logs",       "logs");

        addNavGroup(sidebar, "BILLING");
        addNavItem(sidebar, "💰  Billing",    "billing");

        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    private void addNavGroup(JPanel sidebar, String label) {
        JLabel l = new JLabel("  " + label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(SwingLauncher.AWS_TEXT_DIM);
        l.setBorder(new EmptyBorder(14, 8, 4, 8));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sidebar.add(l);
    }

    private void addNavItem(JPanel sidebar, String label, String cardName) {
        JPanel btn = new JPanel(new BorderLayout());
        btn.setBackground(SwingLauncher.AWS_NAVY);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(4, 16, 4, 16));
        btn.setName(cardName);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(SwingLauncher.AWS_TEXT);
        btn.add(lbl, BorderLayout.CENTER);

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn != activeNavButton) btn.setBackground(new Color(0x2A, 0x38, 0x4E));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn != activeNavButton) btn.setBackground(SwingLauncher.AWS_NAVY);
            }
            @Override public void mouseClicked(MouseEvent e) {
                selectNav(btn, label);
                cardLayout.show(contentArea, cardName);
            }
        });

        if (cardName.equals("dashboard") && activeNavButton == null) {
            selectNav(btn, label);
        }

        sidebar.add(btn);
    }

    private void selectNav(JPanel btn, String label) {
        if (activeNavButton != null) {
            activeNavButton.setBackground(SwingLauncher.AWS_NAVY);
        }
        activeNavButton = btn;
        btn.setBackground(new Color(0x37, 0x4B, 0x5F));
    }

    // ── Content Area ──────────────────────────────────────────────────────────

    private JPanel buildContent() {
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(SwingLauncher.AWS_DARK_BG);

        contentArea.add(new DashboardPanel(), "dashboard");
        contentArea.add(new Ec2Panel(),       "ec2");
        contentArea.add(new LambdaPanel(),    "lambda");
        contentArea.add(new RdsPanel(),       "rds");
        contentArea.add(new S3Panel(),        "s3");
        contentArea.add(new IamPanel(),       "iam");
        contentArea.add(new AuditPanel(),     "audit");
        contentArea.add(new MetricsPanel(),   "metrics");
        contentArea.add(new CloudWatchLogsPanel(), "logs");
        contentArea.add(new VpcPanel(),       "vpc");
        contentArea.add(new Route53Panel(),   "route53");
        contentArea.add(new BillingPanel(),   "billing");


        cardLayout.show(contentArea, "dashboard");
        return contentArea;
    }

    /**
     * Rebuilds the entire content area with fresh panel instances.
     * Use this only for an explicit user-triggered full refresh (e.g., re-login).
     * Do NOT call this automatically on startup — panels are already built with
     * a valid session by the time buildContent() runs (login precedes MainWindow creation).
     */
    public void refreshAll() {
        contentArea.removeAll();
        contentArea.add(new DashboardPanel(), "dashboard");
        contentArea.add(new Ec2Panel(),       "ec2");
        contentArea.add(new LambdaPanel(),    "lambda");
        contentArea.add(new RdsPanel(),       "rds");
        contentArea.add(new S3Panel(),        "s3");
        contentArea.add(new IamPanel(),       "iam");
        contentArea.add(new AuditPanel(),     "audit");
        contentArea.add(new MetricsPanel(),   "metrics");
        contentArea.add(new CloudWatchLogsPanel(), "logs");
        contentArea.add(new VpcPanel(),       "vpc");
        contentArea.add(new Route53Panel(),   "route53");
        contentArea.add(new BillingPanel(),   "billing");
        cardLayout.show(contentArea, "dashboard");
        contentArea.revalidate();
        contentArea.repaint();
    }
}
