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
 * Lambda Panel — deploy and invoke serverless functions.
 */
public class LambdaPanel extends JPanel {

    private DefaultTableModel tableModel;
    private JTable table;
    private JTextArea logArea;

    private static final String[] COLS = {"Function Name", "Runtime", "Handler", "Memory (MB)", "Timeout (s)", "Status"};

    public LambdaPanel() {
        setBackground(SwingLauncher.AWS_DARK_BG);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Lambda — Serverless Functions");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(btn("Create Function", SwingLauncher.AWS_ORANGE, this::createFunction));
        btns.add(btn("▶ Invoke",        SwingLauncher.AWS_GREEN,  this::invokeFunction));
        btns.add(btn("Delete",          SwingLauncher.AWS_RED,    this::deleteFunction));
        btns.add(btn("↻ Refresh",       SwingLauncher.AWS_BLUE,   this::refresh));

        header.add(title, BorderLayout.WEST);
        header.add(btns,  BorderLayout.EAST);

        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        styleTable(table);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getViewport().setBackground(SwingLauncher.AWS_DARK_BG);
        tableScroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        // Invocation log
        JPanel logPanel = new JPanel(new BorderLayout(0, 4));
        logPanel.setOpaque(false);
        JLabel logTitle = new JLabel("Invocation Log");
        logTitle.setForeground(SwingLauncher.AWS_ORANGE);
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        logArea = new JTextArea(6, 0);
        logArea.setEditable(false);
        logArea.setBackground(SwingLauncher.AWS_DARK_BG);
        logArea.setForeground(SwingLauncher.AWS_GREEN);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setText("Invoke a function to see output...");

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(SwingLauncher.AWS_BORDER));

        logPanel.add(logTitle,  BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logPanel);
        split.setDividerLocation(350);
        split.setBackground(SwingLauncher.AWS_DARK_BG);
        split.setBorder(null);

        add(header, BorderLayout.NORTH);
        add(split,  BorderLayout.CENTER);
    }

    private void refresh() {
        if (!ApiClient.isLoggedIn()) return;
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.get("/api/v1/lambda");
            }
            @Override protected void done() {
                try {
                    tableModel.setRowCount(0);
                    JsonNode arr = get();
                    JsonNode data = arr.has("data") ? arr.get("data") : arr;
                    if (data != null && data.isArray()) data.forEach(n -> tableModel.addRow(new Object[]{
                        n.path("name").asText("—"),
                        n.path("runtime").asText("—"),
                        n.path("handler").asText("—"),
                        n.path("memoryMb").asText("128"),
                        n.path("timeoutSec").asText("30"),
                        n.path("status").asText("—")
                    }));
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void createFunction() {
        JTextField name    = new JTextField("my-function");
        JComboBox<String> runtime = new JComboBox<>(new String[]{"python3.11","nodejs18","java17","go1.21","ruby3.2"});
        JTextField handler = new JTextField("index.handler");
        JTextArea  code    = new JTextArea("def handler(event, context):\n    return {'statusCode': 200, 'body': 'Hello from Lambda!'}", 6, 40);
        code.setFont(new Font("Consolas", Font.PLAIN, 12));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Function Name:")); form.add(name);
        form.add(new JLabel("Runtime:"));       form.add(runtime);
        form.add(new JLabel("Handler:"));       form.add(handler);
        form.add(new JLabel("Code:"));          form.add(new JScrollPane(code));

        int res = JOptionPane.showConfirmDialog(this, form, "Create Lambda Function",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.post("/api/v1/lambda", Map.of(
                    "name", name.getText(),
                    "runtime", runtime.getSelectedItem().toString().toUpperCase(),
                    "handler", handler.getText(),
                    "description", "Created via console",
                    "memoryMb", 128,
                    "timeoutSec", 30
                ));
                return null;
            }
            @Override protected void done() { refresh(); }
        };
        w.execute();
    }

    private void invokeFunction() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a function first."); return; }
        String name = (String) tableModel.getValueAt(row, 0);

        String payload = JOptionPane.showInputDialog(this, "Event payload (JSON):", "{}", JOptionPane.PLAIN_MESSAGE);
        if (payload == null) return;

        logArea.setText("Invoking " + name + "...");
        SwingWorker<JsonNode, Void> w = new SwingWorker<>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return ApiClient.post("/api/v1/lambda/invoke/" + name + "/json", payload);
            }
            @Override protected void done() {
                try {
                    JsonNode r = get();
                    logArea.setText("=== Output ===\n" + r.path("stdout").asText(r.path("output").asText(r.toString())));
                } catch (Exception ex) { logArea.setText("Error: " + ex.getMessage()); }
            }
        };
        w.execute();
    }

    private void deleteFunction() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a function first."); return; }
        String name = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete function '" + name + "'?",
            "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ApiClient.delete("/api/v1/lambda/" + name); return null;
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
