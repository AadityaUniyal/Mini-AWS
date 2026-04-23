package com.minicloud.api.ui.panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.api.ui.ApiClient;
import com.minicloud.api.ui.SwingLauncher;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import javax.swing.SwingWorker;

/**
 * RDS Panel — create, list, start, stop managed database instances.
 */
public class RdsPanel extends JPanel {

    private DefaultTableModel tableModel;
    private JTable table;

    private static final String[] COLS = {"DB Identifier", "Engine", "Status", "Endpoint", "Port", "Storage (GB)"};

    public RdsPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("RDS — Managed Databases");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(btn("Create Database", SwingLauncher.AWS_ORANGE, this::createInstance));
        btns.add(btn("Start",           SwingLauncher.AWS_GREEN,  () -> dbAction("start")));
        btns.add(btn("Stop",            SwingLauncher.AWS_RED,    () -> dbAction("stop")));
        btns.add(btn("Delete",          new Color(0x6B, 0x72, 0x80), () -> dbAction("delete")));
        btns.add(btn("↻ Refresh",       SwingLauncher.AWS_BLUE,   this::refresh));

        header.add(title, BorderLayout.WEST);
        header.add(btns,  BorderLayout.EAST);

        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        styleTable(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(SwingLauncher.AWS_DARK_BG);
        scroll.getViewport().setBackground(SwingLauncher.AWS_DARK_BG);
        scroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private void refresh() {
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/rds/instances");
            }
            @Override protected void done() {
                try {
                    tableModel.setRowCount(0);
                    JsonNode arr = get();
                    JsonNode data = arr.has("data") ? arr.get("data") : arr;
                    if (data != null && data.isArray()) data.forEach(n -> tableModel.addRow(new Object[]{
                        n.path("name").asText("—"),
                        n.path("dbName").asText("—"),
                        n.path("status").asText("—"),
                        n.path("endpoint").asText("localhost"),
                        n.path("port").asText("3306"),
                        "20"
                    }));
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void createInstance() {
        JTextField id     = new JTextField("my-db");
        JComboBox<String> engine = new JComboBox<>(new String[]{"mysql","postgresql","h2"});
        JTextField storage = new JTextField("20");
        JPasswordField pass = new JPasswordField("password123");

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("DB Identifier:")); form.add(id);
        form.add(new JLabel("Engine:"));        form.add(engine);
        form.add(new JLabel("Storage (GB):"));  form.add(storage);
        form.add(new JLabel("Master Password:")); form.add(pass);

        int res = JOptionPane.showConfirmDialog(this, form, "Create RDS Instance",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        Map<String, Object> req = Map.of(
            "name", id.getText(),
            "dbName", id.getText(),
            "masterUsername", "admin",
            "masterPassword", new String(pass.getPassword()),
            "port", 3306
        );
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/rds/instances", req); return null;
            }
            @Override protected void done() { refresh(); }
        };
        w.execute();
    }

    private void dbAction(String action) {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a database first."); return; }
        String id = (String) tableModel.getValueAt(row, 0);
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                if ("delete".equals(action)) ApiClient.delete("/rds/instances/" + id);
                else ApiClient.post("/rds/instances/" + id + "/" + action, Map.of());
                return null;
            }
            @Override protected void done() { refresh(); }
        };
        w.execute();
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

    private void styleTable(JTable t) {
        t.setBackground(SwingLauncher.AWS_DARK_BG);
        t.setForeground(SwingLauncher.AWS_TEXT);
        t.setSelectionBackground(SwingLauncher.AWS_NAVY);
        t.setGridColor(SwingLauncher.AWS_BORDER);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setRowHeight(28);
        t.getTableHeader().setBackground(SwingLauncher.AWS_NAVY);
        t.getTableHeader().setForeground(SwingLauncher.AWS_ORANGE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setFillsViewportHeight(true);
    }
}
