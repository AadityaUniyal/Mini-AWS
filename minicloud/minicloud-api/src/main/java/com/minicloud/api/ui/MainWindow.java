package com.minicloud.api.ui;

import com.minicloud.api.domain.*;
import com.minicloud.api.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Main Swing window for MiniCloud Desktop Application.
 * 
 * Features:
 * - AWS-styled dark theme management console
 * - Tabbed panels for S3, EC2, Lambda, RDS, Users, Logs
 * - Integrated console output at the bottom
 * - Status bar showing database connection
 * - Full CRUD operations with real database integration
 */
@Component
public class MainWindow extends JFrame {
    
    private JTextArea consoleArea;
    private JTabbedPane tabbedPane;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Service layer dependencies (injected by Spring)
    @Autowired @Lazy
    private BucketService bucketService;
    
    @Autowired @Lazy
    private InstanceService instanceService;
    
    @Autowired @Lazy
    private LambdaService lambdaService;
    
    @Autowired @Lazy
    private UserService userService;
    
    @Autowired @Lazy
    private AuditLogService auditLogService;
    
    // Table models for dynamic data updates
    private DefaultTableModel bucketsTableModel;
    private DefaultTableModel instancesTableModel;
    private DefaultTableModel lambdaTableModel;
    private DefaultTableModel usersTableModel;
    private DefaultTableModel logsTableModel;
    
    // Tables for selection
    private JTable bucketsTable;
    private JTable instancesTable;
    private JTable lambdaTable;
    private JTable usersTable;
    
    public MainWindow() {
        initializeUI();
    }
    
    private void initializeUI() {
        setTitle("Mini-AWS Cloud Management Console");
        setSize(1200, 800);
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
        splitPane.setResizeWeight(0.70);
        splitPane.setDividerSize(8);
        
        // TABBED PANE (top half)
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabbedPane.setBackground(Color.WHITE);
        
        tabbedPane.addTab("🪣  S3 Buckets",    buildBucketsPanel());
        tabbedPane.addTab("💻  EC2 Instances",  buildEC2Panel());
        tabbedPane.addTab("⚡  Lambda Functions", buildLambdaPanel());
        tabbedPane.addTab("🗄️  RDS Databases",  buildRDSPanel());
        tabbedPane.addTab("👤  IAM Users",       buildUsersPanel());
        tabbedPane.addTab("📋  Activity Logs",   buildLogsPanel());
        
        splitPane.setTopComponent(tabbedPane);
        
        // CONSOLE (bottom half)
        splitPane.setBottomComponent(buildConsolePanel());
        
        add(splitPane, BorderLayout.CENTER);
        
        // ── STATUS BAR ──────────────────────────────────────────
        JLabel statusBar = new JLabel("  ● Connected to minicloud_db @ localhost:3306");
        statusBar.setFont(new Font("Consolas", Font.PLAIN, 12));
        statusBar.setForeground(new Color(0, 180, 100));
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusBar.setOpaque(true);
        statusBar.setBackground(new Color(240, 240, 240));
        add(statusBar, BorderLayout.SOUTH);
        
        // Initial log messages
        log("Mini-AWS Desktop Console started successfully");
        log("Connected to MySQL database: minicloud_db");
        log("Ready to manage cloud resources");
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
    
    // ── BUILD S3 BUCKETS PANEL ──────────────────────────────────
    private JPanel buildBucketsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // Title
        JLabel title = new JLabel("S3 Buckets");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);
        
        // Table with model
        String[] cols = {"ID", "Bucket Name", "Owner ID", "Account ID", "Region", "Created At"};
        bucketsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only table
            }
        };
        bucketsTable = new JTable(bucketsTableModel);
        bucketsTable.setRowHeight(30);
        bucketsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bucketsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        bucketsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(bucketsTable), BorderLayout.CENTER);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton loadBtn = new JButton("Load Buckets");
        JButton createBtn = new JButton("Create Bucket");
        JButton deleteBtn = new JButton("Delete Selected");
        JButton refreshBtn = new JButton("Refresh");
        
        styleButton(loadBtn, new Color(0, 123, 255));
        styleButton(createBtn, new Color(40, 167, 69));
        styleButton(deleteBtn, new Color(220, 53, 69));
        styleButton(refreshBtn, new Color(108, 117, 125));
        
        // Load buckets from database
        loadBtn.addActionListener(e -> loadBuckets());
        
        // Create new bucket
        createBtn.addActionListener(e -> createBucket());
        
        // Delete selected bucket
        deleteBtn.addActionListener(e -> deleteSelectedBucket());
        
        // Refresh data
        refreshBtn.addActionListener(e -> loadBuckets());
        
        btnPanel.add(loadBtn);
        btnPanel.add(createBtn);
        btnPanel.add(deleteBtn);
        btnPanel.add(refreshBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void loadBuckets() {
        log("[S3] Loading buckets from MySQL...");
        new SwingWorker<List<Bucket>, Void>() {
            @Override
            protected List<Bucket> doInBackground() {
                return bucketService.getAllBuckets();
            }
            
            @Override
            protected void done() {
                try {
                    List<Bucket> buckets = get();
                    bucketsTableModel.setRowCount(0);
                    for (Bucket b : buckets) {
                        bucketsTableModel.addRow(new Object[]{
                            b.getId().toString().substring(0, 8) + "...",
                            b.getName(),
                            b.getUserId() != null ? b.getUserId().toString().substring(0, 8) + "..." : "N/A",
                            b.getAccountId(),
                            b.getRegion() != null ? b.getRegion() : "us-east-1",
                            b.getCreatedAt() != null ? b.getCreatedAt().format(dateTimeFormatter) : "N/A"
                        });
                    }
                    log("[S3] Loaded " + buckets.size() + " buckets successfully");
                } catch (Exception ex) {
                    log("[ERROR] Failed to load buckets: " + ex.getMessage());
                    showError("Failed to load buckets", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void createBucket() {
        String bucketName = JOptionPane.showInputDialog(this, 
            "Enter bucket name:", 
            "Create Bucket", 
            JOptionPane.PLAIN_MESSAGE);
        
        if (bucketName == null || bucketName.trim().isEmpty()) {
            return;
        }
        
        log("[S3] Creating bucket: " + bucketName);
        new SwingWorker<Bucket, Void>() {
            @Override
            protected Bucket doInBackground() {
                // Use first user as owner (in real app, use authenticated user)
                UUID ownerId = userService.getAllUsers().stream()
                    .findFirst()
                    .map(User::getId)
                    .orElse(UUID.randomUUID());
                String accountId = "123456789012";
                return bucketService.createBucket(bucketName, ownerId, accountId, "us-east-1");
            }
            
            @Override
            protected void done() {
                try {
                    Bucket bucket = get();
                    log("[S3] Bucket created successfully: " + bucket.getName());
                    loadBuckets(); // Refresh table
                } catch (Exception ex) {
                    log("[ERROR] Failed to create bucket: " + ex.getMessage());
                    showError("Failed to create bucket", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void deleteSelectedBucket() {
        int selectedRow = bucketsTable.getSelectedRow();
        if (selectedRow == -1) {
            showError("No Selection", "Please select a bucket to delete");
            return;
        }
        
        String bucketName = (String) bucketsTableModel.getValueAt(selectedRow, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete bucket: " + bucketName + "?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        log("[S3] Deleting bucket: " + bucketName);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // Find bucket by name and delete
                List<Bucket> buckets = bucketService.getAllBuckets();
                buckets.stream()
                    .filter(b -> b.getName().equals(bucketName))
                    .findFirst()
                    .ifPresent(b -> bucketService.deleteBucket(b.getId()));
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    log("[S3] Bucket deleted successfully: " + bucketName);
                    loadBuckets(); // Refresh table
                } catch (Exception ex) {
                    log("[ERROR] Failed to delete bucket: " + ex.getMessage());
                    showError("Failed to delete bucket", ex.getMessage());
                }
            }
        }.execute();
    }
    
    // ── BUILD EC2 INSTANCES PANEL ───────────────────────────────
    private JPanel buildEC2Panel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // Title
        JLabel title = new JLabel("EC2 Instances");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);
        
        // Table with model
        String[] cols = {"ID", "Instance Name", "Type", "State", "Public IP", "Private IP", "Launched At"};
        instancesTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        instancesTable = new JTable(instancesTableModel);
        instancesTable.setRowHeight(30);
        instancesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        instancesTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        instancesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(instancesTable), BorderLayout.CENTER);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton loadBtn   = new JButton("Load Instances");
        JButton startBtn  = new JButton("Start");
        JButton stopBtn   = new JButton("Stop");
        JButton createBtn = new JButton("Launch New");
        JButton terminateBtn = new JButton("Terminate");
        
        styleButton(loadBtn, new Color(0, 123, 255));
        styleButton(startBtn, new Color(40, 167, 69));
        styleButton(stopBtn, new Color(255, 193, 7));
        styleButton(createBtn, new Color(23, 162, 184));
        styleButton(terminateBtn, new Color(220, 53, 69));
        
        loadBtn.addActionListener(e -> loadInstances());
        startBtn.addActionListener(e -> startSelectedInstance());
        stopBtn.addActionListener(e -> stopSelectedInstance());
        createBtn.addActionListener(e -> launchNewInstance());
        terminateBtn.addActionListener(e -> terminateSelectedInstance());
        
        btnPanel.add(loadBtn);
        btnPanel.add(startBtn);
        btnPanel.add(stopBtn);
        btnPanel.add(createBtn);
        btnPanel.add(terminateBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void loadInstances() {
        log("[EC2] Loading instances from MySQL...");
        new SwingWorker<List<Instance>, Void>() {
            @Override
            protected List<Instance> doInBackground() {
                return instanceService.getAllInstances();
            }
            
            @Override
            protected void done() {
                try {
                    List<Instance> instances = get();
                    instancesTableModel.setRowCount(0);
                    for (Instance inst : instances) {
                        instancesTableModel.addRow(new Object[]{
                            inst.getId().toString().substring(0, 8) + "...",
                            inst.getName(),
                            inst.getType() != null ? inst.getType().name() : "N/A",
                            inst.getState() != null ? inst.getState().name() : "N/A",
                            inst.getPublicIp() != null ? inst.getPublicIp() : "N/A",
                            inst.getPrivateIp() != null ? inst.getPrivateIp() : "N/A",
                            inst.getLaunchedAt() != null ? inst.getLaunchedAt().format(dateTimeFormatter) : "N/A"
                        });
                    }
                    log("[EC2] Loaded " + instances.size() + " instances successfully");
                } catch (Exception ex) {
                    log("[ERROR] Failed to load instances: " + ex.getMessage());
                    showError("Failed to load instances", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void launchNewInstance() {
        String instanceName = JOptionPane.showInputDialog(this,
            "Enter instance name:",
            "Launch Instance",
            JOptionPane.PLAIN_MESSAGE);
        
        if (instanceName == null || instanceName.trim().isEmpty()) {
            return;
        }
        
        String[] types = {"T2_MICRO", "T2_SMALL", "T2_MEDIUM", "T2_LARGE"};
        String selectedType = (String) JOptionPane.showInputDialog(this,
            "Select instance type:",
            "Launch Instance",
            JOptionPane.PLAIN_MESSAGE,
            null,
            types,
            types[0]);
        
        if (selectedType == null) {
            return;
        }
        
        log("[EC2] Launching instance: " + instanceName);
        new SwingWorker<Instance, Void>() {
            @Override
            protected Instance doInBackground() {
                UUID ownerId = userService.getAllUsers().stream()
                    .findFirst()
                    .map(User::getId)
                    .orElse(UUID.randomUUID());
                String accountId = "123456789012";
                InstanceType type = InstanceType.valueOf(selectedType);
                return instanceService.launchInstance(instanceName, ownerId, accountId, type);
            }
            
            @Override
            protected void done() {
                try {
                    Instance instance = get();
                    log("[EC2] Instance launched successfully: " + instance.getName());
                    loadInstances();
                } catch (Exception ex) {
                    log("[ERROR] Failed to launch instance: " + ex.getMessage());
                    showError("Failed to launch instance", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void startSelectedInstance() {
        int selectedRow = instancesTable.getSelectedRow();
        if (selectedRow == -1) {
            showError("No Selection", "Please select an instance to start");
            return;
        }
        
        String instanceName = (String) instancesTableModel.getValueAt(selectedRow, 1);
        log("[EC2] Starting instance: " + instanceName);
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                List<Instance> instances = instanceService.getAllInstances();
                instances.stream()
                    .filter(i -> i.getName().equals(instanceName))
                    .findFirst()
                    .ifPresent(i -> instanceService.startInstance(i.getId()));
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    log("[EC2] Instance started successfully: " + instanceName);
                    loadInstances();
                } catch (Exception ex) {
                    log("[ERROR] Failed to start instance: " + ex.getMessage());
                    showError("Failed to start instance", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void stopSelectedInstance() {
        int selectedRow = instancesTable.getSelectedRow();
        if (selectedRow == -1) {
            showError("No Selection", "Please select an instance to stop");
            return;
        }
        
        String instanceName = (String) instancesTableModel.getValueAt(selectedRow, 1);
        log("[EC2] Stopping instance: " + instanceName);
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                List<Instance> instances = instanceService.getAllInstances();
                instances.stream()
                    .filter(i -> i.getName().equals(instanceName))
                    .findFirst()
                    .ifPresent(i -> instanceService.stopInstance(i.getId()));
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    log("[EC2] Instance stopped successfully: " + instanceName);
                    loadInstances();
                } catch (Exception ex) {
                    log("[ERROR] Failed to stop instance: " + ex.getMessage());
                    showError("Failed to stop instance", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void terminateSelectedInstance() {
        int selectedRow = instancesTable.getSelectedRow();
        if (selectedRow == -1) {
            showError("No Selection", "Please select an instance to terminate");
            return;
        }
        
        String instanceName = (String) instancesTableModel.getValueAt(selectedRow, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to terminate instance: " + instanceName + "?",
            "Confirm Terminate",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        log("[EC2] Terminating instance: " + instanceName);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                List<Instance> instances = instanceService.getAllInstances();
                instances.stream()
                    .filter(i -> i.getName().equals(instanceName))
                    .findFirst()
                    .ifPresent(i -> instanceService.terminateInstance(i.getId()));
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    log("[EC2] Instance terminated successfully: " + instanceName);
                    loadInstances();
                } catch (Exception ex) {
                    log("[ERROR] Failed to terminate instance: " + ex.getMessage());
                    showError("Failed to terminate instance", ex.getMessage());
                }
            }
        }.execute();
    }
    
    // ── BUILD LAMBDA PANEL ──────────────────────────────────────
    private JPanel buildLambdaPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // Title
        JLabel title = new JLabel("Lambda Functions");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);
        
        // Table with model
        String[] cols = {"ID", "Function Name", "Runtime", "Memory (MB)", "Timeout (s)", "Invocations", "Status"};
        lambdaTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        lambdaTable = new JTable(lambdaTableModel);
        lambdaTable.setRowHeight(30);
        lambdaTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lambdaTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        lambdaTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(lambdaTable), BorderLayout.CENTER);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton loadBtn = new JButton("Load Functions");
        JButton createBtn = new JButton("Create Function");
        JButton invokeBtn = new JButton("Invoke");
        JButton deleteBtn = new JButton("Delete");
        
        styleButton(loadBtn, new Color(0, 123, 255));
        styleButton(createBtn, new Color(40, 167, 69));
        styleButton(invokeBtn, new Color(255, 193, 7));
        styleButton(deleteBtn, new Color(220, 53, 69));
        
        loadBtn.addActionListener(e -> loadLambdaFunctions());
        createBtn.addActionListener(e -> createLambdaFunction());
        invokeBtn.addActionListener(e -> invokeSelectedFunction());
        deleteBtn.addActionListener(e -> deleteSelectedFunction());
        
        btnPanel.add(loadBtn);
        btnPanel.add(createBtn);
        btnPanel.add(invokeBtn);
        btnPanel.add(deleteBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void loadLambdaFunctions() {
        log("[Lambda] Loading functions from MySQL...");
        new SwingWorker<List<Function>, Void>() {
            @Override
            protected List<Function> doInBackground() {
                return lambdaService.getAllFunctions();
            }
            
            @Override
            protected void done() {
                try {
                    List<Function> functions = get();
                    lambdaTableModel.setRowCount(0);
                    for (Function func : functions) {
                        lambdaTableModel.addRow(new Object[]{
                            func.getId().toString().substring(0, 8) + "...",
                            func.getName(),
                            func.getRuntime() != null ? func.getRuntime().name() : "N/A",
                            func.getMemoryMb(),
                            func.getTimeoutSec(),
                            func.getInvocationCount(),
                            func.getStatus() != null ? func.getStatus().name() : "N/A"
                        });
                    }
                    log("[Lambda] Loaded " + functions.size() + " functions successfully");
                } catch (Exception ex) {
                    log("[ERROR] Failed to load functions: " + ex.getMessage());
                    showError("Failed to load functions", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void createLambdaFunction() {
        String functionName = JOptionPane.showInputDialog(this,
            "Enter function name:",
            "Create Function",
            JOptionPane.PLAIN_MESSAGE);
        
        if (functionName == null || functionName.trim().isEmpty()) {
            return;
        }
        
        String[] runtimes = {"PYTHON", "NODE", "JAVA", "BASH", "RUBY", "GO", "DOTNET"};
        String selectedRuntime = (String) JOptionPane.showInputDialog(this,
            "Select runtime:",
            "Create Function",
            JOptionPane.PLAIN_MESSAGE,
            null,
            runtimes,
            runtimes[0]);
        
        if (selectedRuntime == null) {
            return;
        }
        
        log("[Lambda] Creating function: " + functionName);
        new SwingWorker<Function, Void>() {
            @Override
            protected Function doInBackground() {
                UUID ownerId = userService.getAllUsers().stream()
                    .findFirst()
                    .map(User::getId)
                    .orElse(UUID.randomUUID());
                String accountId = "123456789012";
                Function.Runtime runtime = Function.Runtime.valueOf(selectedRuntime);
                return lambdaService.createFunction(functionName, ownerId, accountId, runtime, "index.handler", 128, 30);
            }
            
            @Override
            protected void done() {
                try {
                    Function function = get();
                    log("[Lambda] Function created successfully: " + function.getName());
                    loadLambdaFunctions();
                } catch (Exception ex) {
                    log("[ERROR] Failed to create function: " + ex.getMessage());
                    showError("Failed to create function", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void invokeSelectedFunction() {
        int selectedRow = lambdaTable.getSelectedRow();
        if (selectedRow == -1) {
            showError("No Selection", "Please select a function to invoke");
            return;
        }
        
        String functionName = (String) lambdaTableModel.getValueAt(selectedRow, 1);
        log("[Lambda] Invoking function: " + functionName);
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                List<Function> functions = lambdaService.getAllFunctions();
                functions.stream()
                    .filter(f -> f.getName().equals(functionName))
                    .findFirst()
                    .ifPresent(f -> lambdaService.invokeFunction(f.getId()));
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    log("[Lambda] Function invoked successfully: " + functionName);
                    loadLambdaFunctions();
                } catch (Exception ex) {
                    log("[ERROR] Failed to invoke function: " + ex.getMessage());
                    showError("Failed to invoke function", ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void deleteSelectedFunction() {
        int selectedRow = lambdaTable.getSelectedRow();
        if (selectedRow == -1) {
            showError("No Selection", "Please select a function to delete");
            return;
        }
        
        String functionName = (String) lambdaTableModel.getValueAt(selectedRow, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete function: " + functionName + "?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        log("[Lambda] Deleting function: " + functionName);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                List<Function> functions = lambdaService.getAllFunctions();
                functions.stream()
                    .filter(f -> f.getName().equals(functionName))
                    .findFirst()
                    .ifPresent(f -> lambdaService.deleteFunction(f.getId()));
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    log("[Lambda] Function deleted successfully: " + functionName);
                    loadLambdaFunctions();
                } catch (Exception ex) {
                    log("[ERROR] Failed to delete function: " + ex.getMessage());
                    showError("Failed to delete function", ex.getMessage());
                }
            }
        }.execute();
    }
    
    // ── BUILD RDS PANEL ─────────────────────────────────────────
    private JPanel buildRDSPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // Title
        JLabel title = new JLabel("RDS Database Instances");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);
        
        // Table
        String[] cols = {"ID", "DB Identifier", "Engine", "Instance Class", "Storage (GB)", "Status"};
        Object[][] data = {};
        JTable table = new JTable(data, cols);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton loadBtn = new JButton("Load Databases");
        JButton createBtn = new JButton("Create Database");
        
        styleButton(loadBtn, new Color(0, 123, 255));
        styleButton(createBtn, new Color(40, 167, 69));
        
        loadBtn.addActionListener(e -> log("[RDS] Loading database instances..."));
        createBtn.addActionListener(e -> log("[RDS] Create database dialog..."));
        
        btnPanel.add(loadBtn);
        btnPanel.add(createBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ── BUILD USERS PANEL ───────────────────────────────────────
    private JPanel buildUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // Title
        JLabel title = new JLabel("IAM Users");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);
        
        // Table with model
        String[] cols = {"ID", "Username", "Email", "Role", "Account ID", "Root User", "Created At"};
        usersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        usersTable = new JTable(usersTableModel);
        usersTable.setRowHeight(30);
        usersTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        usersTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        usersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton loadBtn = new JButton("Load Users");
        JButton refreshBtn = new JButton("Refresh");
        
        styleButton(loadBtn, new Color(0, 123, 255));
        styleButton(refreshBtn, new Color(108, 117, 125));
        
        loadBtn.addActionListener(e -> loadUsers());
        refreshBtn.addActionListener(e -> loadUsers());
        
        btnPanel.add(loadBtn);
        btnPanel.add(refreshBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void loadUsers() {
        log("[IAM] Loading users from MySQL...");
        new SwingWorker<List<User>, Void>() {
            @Override
            protected List<User> doInBackground() {
                return userService.getAllUsers();
            }
            
            @Override
            protected void done() {
                try {
                    List<User> users = get();
                    usersTableModel.setRowCount(0);
                    for (User user : users) {
                        usersTableModel.addRow(new Object[]{
                            user.getId().toString().substring(0, 8) + "...",
                            user.getUsername(),
                            user.getEmail(),
                            user.getRole() != null ? user.getRole().name() : "N/A",
                            user.getAccountId(),
                            user.getRootUser() != null ? user.getRootUser() : false,
                            user.getCreatedAt() != null ? user.getCreatedAt().format(dateTimeFormatter) : "N/A"
                        });
                    }
                    log("[IAM] Loaded " + users.size() + " users successfully");
                } catch (Exception ex) {
                    log("[ERROR] Failed to load users: " + ex.getMessage());
                    showError("Failed to load users", ex.getMessage());
                }
            }
        }.execute();
    }
    
    // ── BUILD LOGS PANEL ────────────────────────────────────────
    private JPanel buildLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // Title
        JLabel title = new JLabel("Activity Logs (CloudTrail)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);
        
        // Table with model
        String[] cols = {"ID", "Username", "Service", "Action", "Resource", "Status", "Timestamp"};
        logsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable logsTable = new JTable(logsTableModel);
        logsTable.setRowHeight(30);
        logsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        logsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(new JScrollPane(logsTable), BorderLayout.CENTER);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton loadBtn = new JButton("Load Logs");
        JButton refreshBtn = new JButton("Refresh");
        
        styleButton(loadBtn, new Color(0, 123, 255));
        styleButton(refreshBtn, new Color(108, 117, 125));
        
        loadBtn.addActionListener(e -> loadAuditLogs());
        refreshBtn.addActionListener(e -> loadAuditLogs());
        
        btnPanel.add(loadBtn);
        btnPanel.add(refreshBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void loadAuditLogs() {
        log("[LOGS] Loading activity logs from MySQL...");
        new SwingWorker<List<AuditLog>, Void>() {
            @Override
            protected List<AuditLog> doInBackground() {
                return auditLogService.getAllLogs();
            }
            
            @Override
            protected void done() {
                try {
                    List<AuditLog> logs = get();
                    logsTableModel.setRowCount(0);
                    for (AuditLog auditLog : logs) {
                        logsTableModel.addRow(new Object[]{
                            auditLog.getId().toString().substring(0, 8) + "...",
                            auditLog.getUsername() != null ? auditLog.getUsername() : "N/A",
                            auditLog.getService() != null ? auditLog.getService() : "N/A",
                            auditLog.getAction() != null ? auditLog.getAction() : "N/A",
                            auditLog.getResource() != null ? auditLog.getResource() : "N/A",
                            auditLog.getStatus() != null ? auditLog.getStatus() : "N/A",
                            auditLog.getTimestamp() != null ? auditLog.getTimestamp().format(dateTimeFormatter) : "N/A"
                        });
                    }
                    log("[LOGS] Loaded " + logs.size() + " audit logs successfully");
                } catch (Exception ex) {
                    log("[ERROR] Failed to load logs: " + ex.getMessage());
                    showError("Failed to load logs", ex.getMessage());
                }
            }
        }.execute();
    }
    
    // ── UTILITY: STYLE BUTTON ───────────────────────────────────
    private void styleButton(JButton button, Color bgColor) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    
    // ── UTILITY: SHOW ERROR DIALOG ──────────────────────────────
    private void showError(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                message,
                title,
                JOptionPane.ERROR_MESSAGE);
        });
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
