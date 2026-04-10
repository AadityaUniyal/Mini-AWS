package com.minicloud.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.components.MiniCloudTable;
import com.minicloud.ui.components.NotificationBar;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Enhanced S3Panel — Deep features: Multi-upload, Progress, Custom Download.
 */
public class S3Panel extends JPanel {

    private final ApiClient apiClient;
    private DefaultTableModel tableModel;
    private MiniCloudTable s3Table;
    private JLabel breadcrumbLabel;
    private MiniCloudButton uploadBtn, downloadBtn, deleteBtn, backBtn;
    
    // View state
    private String currentBucket = null;

    public S3Panel(ApiClient apiClient) {
        this.apiClient = apiClient;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeConstants.BG_LIGHT);
        setBorder(new EmptyBorder(20, 30, 20, 30));

        // ── Header ───────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        breadcrumbLabel = new JLabel("S3 > Buckets");
        breadcrumbLabel.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        breadcrumbLabel.setForeground(ThemeConstants.TEXT_MUTED);
        header.add(breadcrumbLabel, BorderLayout.NORTH);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("Buckets");
        title.setFont(ThemeConstants.getFont(20, Font.BOLD));
        titlePanel.add(title);
        header.add(titlePanel, BorderLayout.CENTER);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);
        
        MiniCloudButton createBtn = new MiniCloudButton("Create bucket", MiniCloudButton.Type.PRIMARY);
        createBtn.addActionListener(e -> showCreateBucketDialog());
        toolbar.add(createBtn);

        uploadBtn = new MiniCloudButton("Upload", MiniCloudButton.Type.PRIMARY);
        uploadBtn.setVisible(false);
        uploadBtn.addActionListener(e -> uploadFiles());
        toolbar.add(uploadBtn);

        downloadBtn = new MiniCloudButton("Download", MiniCloudButton.Type.OUTLINE);
        downloadBtn.setVisible(false);
        downloadBtn.addActionListener(e -> downloadFile());
        toolbar.add(downloadBtn);

        deleteBtn = new MiniCloudButton("Delete", MiniCloudButton.Type.OUTLINE);
        deleteBtn.addActionListener(e -> deleteAction());
        toolbar.add(deleteBtn);
        
        backBtn = new MiniCloudButton("← Back", MiniCloudButton.Type.OUTLINE);
        backBtn.setVisible(false);
        backBtn.addActionListener(e -> showBuckets());
        toolbar.add(backBtn);
        
        header.add(toolbar, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // ── Table ───────────────────────────────────────────
        tableModel = new DefaultTableModel(new String[]{"Bucket name", "Region", "Objects", "Size", "Created", "Access"}, 0);
        s3Table = new MiniCloudTable();
        s3Table.setModel(tableModel);

        s3Table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && currentBucket == null) {
                    int row = s3Table.getSelectedRow();
                    if (row >= 0) showObjects(tableModel.getValueAt(row, 0).toString());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(s3Table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY));
        add(scroll, BorderLayout.CENTER);
    }

    public void refresh() {
        if (currentBucket == null) showBuckets();
        else showObjects(currentBucket);
    }

    private void showBuckets() {
        currentBucket = null;
        breadcrumbLabel.setText("S3 > Buckets");
        uploadBtn.setVisible(false);
        downloadBtn.setVisible(false);
        backBtn.setVisible(false);

        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return apiClient.getData(apiClient.get("/s3/buckets"));
            }
            @Override protected void done() {
                try {
                    JsonNode res = get();
                    tableModel.setDataVector(new Object[][]{}, new String[]{"Bucket name", "Region", "Objects", "Size", "Created", "Access"});
                    if (res.isArray()) {
                        for (JsonNode b : res) {
                            tableModel.addRow(new Object[]{
                                b.get("name").asText(), "local-dev-1",
                                b.has("objectCount") ? b.get("objectCount").asInt() : 0,
                                b.has("sizeTotal") ? String.format("%.1f MB", b.get("sizeTotal").asDouble()/1024/1024) : "0 B",
                                b.has("createdAt") ? b.get("createdAt").asText().substring(0, 10) : "—", "Private"
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void showObjects(String bucketName) {
        currentBucket = bucketName;
        breadcrumbLabel.setText("S3 > Buckets > " + bucketName);
        uploadBtn.setVisible(true);
        downloadBtn.setVisible(true);
        backBtn.setVisible(true);

        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception {
                return apiClient.getData(apiClient.get("/s3/buckets/" + bucketName + "/list"));
            }
            @Override protected void done() {
                try {
                    JsonNode res = get();
                    tableModel.setDataVector(new Object[][]{}, new String[]{"Name", "Type", "Size", "Last modified", "Storage class"});
                    if (res.isArray()) {
                        for (JsonNode o : res) {
                            tableModel.addRow(new Object[]{
                                o.get("objectKey").asText(),
                                o.has("contentType") ? o.get("contentType").asText() : "binary",
                                String.format("%.1f KB", o.get("sizeBytes").asDouble()/1024),
                                o.has("createdAt") ? o.get("createdAt").asText().replace("T", " ").substring(0, 19) : "—", "STANDARD"
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void uploadFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            uploadWithProgress(files);
        }
    }

    private void uploadWithProgress(File[] files) {
        final JDialog dialog = new JDialog((Frame)null, "Uploading...", true);
        JProgressBar bar = new JProgressBar(0, files.length);
        bar.setStringPainted(true);
        JLabel label = new JLabel("Starting upload...");
        
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        p.add(label, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        dialog.add(p);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() throws Exception {
                int count = 0;
                for (File f : files) {
                    publish("Uploading: " + f.getName() + " (" + (count + 1) + "/" + files.length + ")");
                    apiClient.upload("/s3/buckets/" + currentBucket + "/upload", f);
                    count++;
                    setProgress(count);
                }
                return null;
            }
            @Override protected void process(List<String> chunks) {
                label.setText(chunks.get(chunks.size() - 1));
                bar.setValue(getProgress());
            }
            @Override protected void done() {
                dialog.dispose();
                NotificationBar.show((JPanel)getParent().getParent(), "Successfully uploaded " + files.length + " files", NotificationBar.Type.SUCCESS);
                refresh();
            }
        }.execute();
        dialog.setVisible(true);
    }

    private void downloadFile() {
        int row = s3Table.getSelectedRow();
        if (row < 0 || currentBucket == null) return;
        String key = tableModel.getValueAt(row, 0).toString();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Destination Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File destDir = chooser.getSelectedFile();
            File destFile = new File(destDir, key);
            
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    byte[] data = apiClient.download("/s3/buckets/" + currentBucket + "/" + key);
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        fos.write(data);
                    }
                    return null;
                }
                @Override protected void done() {
                    NotificationBar.show((JPanel)getParent().getParent(), "Downloaded to " + destFile.getAbsolutePath(), NotificationBar.Type.SUCCESS);
                }
            }.execute();
        }
    }

    private void showCreateBucketDialog() {
        com.minicloud.ui.dialog.CreateBucketDialog dialog = new com.minicloud.ui.dialog.CreateBucketDialog((Frame) SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String name = dialog.getBucketName();
            new SwingWorker<JsonNode, Void>() {
                @Override protected JsonNode doInBackground() throws Exception {
                    return apiClient.postForm("/s3/buckets?name=" + name);
                }
                @Override protected void done() {
                    try {
                        JsonNode res = get();
                        if (res.has("success") && res.get("success").asBoolean()) {
                            NotificationBar.show((JPanel)getParent().getParent(), "Bucket created: " + name, NotificationBar.Type.SUCCESS);
                            refresh();
                        } else {
                            String msg = res.has("message") ? res.get("message").asText() : "Failed to create bucket";
                            NotificationBar.show((JPanel)getParent().getParent(), msg, NotificationBar.Type.ERROR);
                        }
                    } catch (Exception e) {
                        NotificationBar.show((JPanel)getParent().getParent(), "Connection error", NotificationBar.Type.ERROR);
                    }
                }
            }.execute();
        }
    }

    private void deleteAction() {
        int row = s3Table.getSelectedRow();
        if (row < 0) return;
        String name = tableModel.getValueAt(row, 0).toString();

        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception {
                if (currentBucket == null) {
                    // Delete bucket
                    return apiClient.delete("/s3/buckets/" + name);
                } else {
                    // Delete object
                    return apiClient.delete("/s3/buckets/" + currentBucket + "/" + name);
                }
            }
            @Override protected void done() {
                try {
                    JsonNode res = get();
                    if (res.has("success") && !res.get("success").asBoolean()) {
                        String msg = res.has("message") ? res.get("message").asText() : "Delete failed";
                        NotificationBar.show((JPanel)getParent().getParent(), msg, NotificationBar.Type.ERROR);
                    } else {
                        NotificationBar.show((JPanel)getParent().getParent(), "Successfully deleted", NotificationBar.Type.SUCCESS);
                        refresh();
                    }
                } catch (Exception e) {
                    NotificationBar.show((JPanel)getParent().getParent(), "Connection error", NotificationBar.Type.ERROR);
                }
            }
        }.execute();
    }
}
