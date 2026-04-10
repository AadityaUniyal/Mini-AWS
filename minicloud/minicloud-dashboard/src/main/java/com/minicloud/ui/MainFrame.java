package com.minicloud.ui;

import com.minicloud.ui.components.SearchField;
import com.minicloud.ui.components.NotificationBar;
import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Main Frame — AWS Management Console Style Layout.
 */
public class MainFrame extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentWrapper = new JPanel(new BorderLayout());
    private final JPanel contentPanel = new JPanel(cardLayout);

    private final ApiClient apiClient = new ApiClient();

    // Panels
    private LoginPanel loginPanel;
    private HomePanel homePanel;
    private S3Panel s3Panel;
    private EC2Panel ec2Panel;
    private IAMPanel iamPanel;
    private MonitorPanel monitorPanel;

    // Sidebar & Components
    private JPanel sidebar;
    private JPanel navbar;
    private JLabel usernameLabel;

    public MainFrame() {
        setTitle("MiniCloud — AWS Style Management Console");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);

        initComponents();
        showLogin();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // ── Top Navigation Bar ────────────────────────────────────
        navbar = createNavbar();

        // ── Sidebar ──────────────────────────────────────────────
        sidebar = createSidebar();

        // ── Content ───────────────────────────────────────────────
        loginPanel   = new LoginPanel(apiClient, this);
        homePanel    = new HomePanel(apiClient, this);
        s3Panel      = new S3Panel(apiClient);
        ec2Panel     = new EC2Panel(apiClient);
        iamPanel     = new IAMPanel(apiClient);
        monitorPanel = new MonitorPanel(apiClient);

        contentPanel.add(loginPanel,  "LOGIN");
        contentPanel.add(homePanel,   "HOME");
        contentPanel.add(s3Panel,     "S3");
        contentPanel.add(ec2Panel,    "EC2");
        contentPanel.add(iamPanel,    "IAM");
        contentPanel.add(monitorPanel,"MONITOR");

        contentWrapper.add(contentPanel, BorderLayout.CENTER);
        
        add(navbar, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(contentWrapper, BorderLayout.CENTER);
    }

    private JPanel createNavbar() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(ThemeConstants.NAVY);
        nav.setPreferredSize(new Dimension(0, 48));
        nav.setBorder(new EmptyBorder(0, 16, 0, 16));

        // Left: Logo & Toggle
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        left.setOpaque(false);
        
        JLabel logo = new JLabel("☁ MiniCloud");
        logo.setFont(ThemeConstants.getFont(16, Font.BOLD));
        logo.setForeground(Color.WHITE);
        left.add(logo);

        JLabel separator = new JLabel("|");
        separator.setForeground(Color.GRAY);
        left.add(separator);

        JButton servicesBtn = new JButton("Services ▾");
        servicesBtn.setForeground(Color.WHITE);
        servicesBtn.setContentAreaFilled(false);
        servicesBtn.setBorderPainted(false);
        servicesBtn.setFocusPainted(false);
        servicesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        left.add(servicesBtn);

        nav.add(left, BorderLayout.WEST);

        // Center: Search
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(new SearchField("Search MiniCloud services..."));
        nav.add(center, BorderLayout.CENTER);

        // Right: Region & Account
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 12));
        right.setOpaque(false);

        JLabel region = new JLabel("🌐 local-dev-1");
        region.setForeground(Color.WHITE);
        region.setFont(ThemeConstants.getFont(13, Font.PLAIN));
        right.add(region);

        usernameLabel = new JLabel("admin");
        usernameLabel.setForeground(Color.WHITE);
        usernameLabel.setFont(ThemeConstants.getFont(13, Font.BOLD));
        right.add(usernameLabel);

        roleBadge = new JLabel("ADMIN");
        roleBadge.setOpaque(true);
        roleBadge.setBackground(ThemeConstants.ACTIVE_BLUE);
        roleBadge.setForeground(Color.WHITE);
        roleBadge.setFont(ThemeConstants.getFont(10, Font.BOLD));
        roleBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        right.add(roleBadge);

        nav.add(right, BorderLayout.EAST);
        return nav;
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(220, 0));
        panel.setBackground(ThemeConstants.SIDEBAR_DARK);
        panel.setBorder(new MatteBorder(0, 0, 0, 1, ThemeConstants.BORDER_GRAY));

        // Sidebar Header
        JLabel header = new JLabel("MiniCloud Console");
        header.setFont(ThemeConstants.getFont(14, Font.BOLD));
        header.setForeground(Color.WHITE);
        header.setBorder(new EmptyBorder(20, 20, 10, 20));
        panel.add(header);

        panel.add(Box.createVerticalStrut(10));
        
        // Navigation items
        panel.add(createSidebarItem("📊 Dashboard", "HOME"));
        
        panel.add(createSectionHeader("COMPUTE"));
        panel.add(createSidebarItem("▣ EC2 Instances", "EC2"));
        
        panel.add(createSectionHeader("STORAGE"));
        panel.add(createSidebarItem("🪣 S3 Buckets", "S3"));
        
        panel.add(createSectionHeader("SECURITY"));
        panel.add(createSidebarItem("🔑 IAM Management", "IAM"));
        
        panel.add(createSectionHeader("MONITORING"));
        panel.add(createSidebarItem("📈 CloudWatch", "MONITOR"));

        panel.add(Box.createVerticalGlue());
        
        // Bottom items
        panel.add(new JSeparator(JSeparator.HORIZONTAL));
        panel.add(createSidebarItem("⚙ Settings", "SETTINGS"));
        panel.add(createSidebarItem("🚪 Sign Out", "LOGOUT"));

        return panel;
    }

    private JComponent createSectionHeader(String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(ThemeConstants.getFont(11, Font.PLAIN));
        lbl.setForeground(ThemeConstants.TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(15, 20, 5, 20));
        return lbl;
    }

    private JPanel createSidebarItem(String text, String cardName) {
        JPanel item = new JPanel(new BorderLayout());
        item.setMaximumSize(new Dimension(220, 40));
        item.setBackground(ThemeConstants.SIDEBAR_DARK);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JLabel lbl = new JLabel(text);
        lbl.setForeground(ThemeConstants.TEXT_LIGHT);
        lbl.setFont(ThemeConstants.getFont(13, Font.PLAIN));
        lbl.setBorder(new EmptyBorder(0, 20, 0, 0));
        item.add(lbl, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { 
                item.setBackground(ThemeConstants.SIDEBAR_HOVER); 
                lbl.setForeground(Color.WHITE);
            }
            @Override public void mouseExited(MouseEvent e) { 
                if (!cardName.equals(currentCard)) {
                    item.setBackground(ThemeConstants.SIDEBAR_DARK);
                    lbl.setForeground(ThemeConstants.TEXT_LIGHT);
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if ("LOGOUT".equals(cardName)) logout();
                else if (!"SETTINGS".equals(cardName)) showPanel(cardName);
            }
        });

        return item;
    }

    private String currentCard = "HOME";

    public void showPanel(String name) {
        currentCard = name;
        cardLayout.show(contentPanel, name);
        
        // Update sidebar highlighting
        for (Component c : sidebar.getComponents()) {
            if (c instanceof JPanel p) {
                if (p.getComponentCount() > 0 && p.getComponent(0) instanceof JLabel l) {
                    if (l.getText().contains(name)) {
                        p.setBackground(ThemeConstants.SIDEBAR_HOVER);
                        p.setBorder(new MatteBorder(0, 4, 0, 0, ThemeConstants.ACTIVE_BLUE));
                    } else {
                        p.setBackground(ThemeConstants.SIDEBAR_DARK);
                        p.setBorder(null);
                    }
                }
            }
        }

        switch (name) {
            case "HOME"    -> homePanel.refresh();
            case "S3"      -> s3Panel.refresh();
            case "EC2"     -> ec2Panel.refresh();
            case "IAM"     -> iamPanel.refresh();
            case "MONITOR" -> monitorPanel.startRefresh();
        }
        revalidate(); repaint();
    }

    private void logout() {
        apiClient.clearToken();
        showLogin();
    }

    public void showLogin() {
        if (navbar != null) navbar.setVisible(false);
        if (sidebar != null) sidebar.setVisible(false);
        cardLayout.show(contentPanel, "LOGIN");
    }

    private JLabel roleBadge;

    public void showDashboard(String username) {
        usernameLabel.setText(username);
        String role = apiClient.getRole();
        roleBadge.setText(role);
        
        if ("READONLY".equals(role)) {
            roleBadge.setBackground(ThemeConstants.TEXT_MUTED);
            applyReadonlyPolicy();
        } else if ("ADMIN".equals(role)) {
            roleBadge.setBackground(ThemeConstants.RED_TEXT);
        } else {
            roleBadge.setBackground(ThemeConstants.ACTIVE_BLUE);
        }

        navbar.setVisible(true);
        sidebar.setVisible(true);
        showPanel("HOME");
    }

    private void applyReadonlyPolicy() {
        // Recursively disable action buttons across all panels
        disableMutatingComponents(contentPanel);
    }

    private void disableMutatingComponents(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof com.minicloud.ui.components.MiniCloudButton btn) {
                // If it's a PRIMARY button (usually Create/Launch/Delete), disable it
                if (btn.getBackground().equals(ThemeConstants.ACTIVE_BLUE) || 
                    btn.getText().toLowerCase().contains("create") ||
                    btn.getText().toLowerCase().contains("launch") ||
                    btn.getText().toLowerCase().contains("delete")) {
                    btn.setEnabled(false);
                    btn.setToolTipText("Access Denied: Read-Only Role");
                }
            } else if (c instanceof Container child) {
                disableMutatingComponents(child);
            }
        }
    }

    public void notify(String msg, NotificationBar.Type type) {
        NotificationBar.show(contentWrapper, msg, type);
    }
}
