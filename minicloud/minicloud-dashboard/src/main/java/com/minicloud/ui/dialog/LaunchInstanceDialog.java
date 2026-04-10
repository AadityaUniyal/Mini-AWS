package com.minicloud.ui.dialog;

import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Redesigned LaunchInstanceDialog — AWS summary-box style.
 */
public class LaunchInstanceDialog extends JDialog {

    private boolean confirmed = false;
    private JTextField nameField;
    private JComboBox<String> typeCombo;
    private JTextField commandField;

    public LaunchInstanceDialog(Frame parent) {
        super(parent, "Launch an instance", true);
        initUI();
        setSize(700, 480);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeConstants.BG_LIGHT);

        // ── Main Content Area ─────────────────────────────────
        JPanel main = new JPanel(new GridLayout(1, 2));
        main.setOpaque(false);

        // Left Col: Form
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("Launch an instance");
        title.setFont(ThemeConstants.getFont(18, Font.BOLD));
        form.add(title);
        form.add(Box.createVerticalStrut(20));

        form.add(createLabel("Name (Tag)"));
        nameField = new JTextField("my-instance-1");
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        form.add(nameField);
        
        form.add(Box.createVerticalStrut(15));
        form.add(createLabel("Instance type"));
        typeCombo = new JComboBox<>(new String[]{"MICRO (1 vCPU, 0.5 GB)", "SMALL (1 vCPU, 2 GB)", "MEDIUM (2 vCPU, 4 GB)"});
        typeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        form.add(typeCombo);

        form.add(Box.createVerticalStrut(15));
        form.add(createLabel("Launch Command (JAR path)"));
        commandField = new JTextField("java -jar app.jar");
        commandField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        form.add(commandField);

        main.add(form);

        // Right Col: Summary Panel
        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBackground(ThemeConstants.BG_LIGHT);
        summary.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel sumTitle = new JLabel("Summary");
        sumTitle.setFont(ThemeConstants.getFont(14, Font.BOLD));
        summary.add(sumTitle);
        summary.add(Box.createVerticalStrut(15));

        summary.add(createSummaryLabel("Instance: 1"));
        summary.add(createSummaryLabel("Region: local-dev-1"));
        summary.add(createSummaryLabel("Firewall: default-sg"));
        summary.add(Box.createVerticalStrut(30));

        MiniCloudButton launchBtn = new MiniCloudButton("Launch instance", MiniCloudButton.Type.PRIMARY);
        launchBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        launchBtn.addActionListener(e -> { confirmed = true; dispose(); });
        summary.add(launchBtn);

        MiniCloudButton cancelBtn = new MiniCloudButton("Cancel", MiniCloudButton.Type.OUTLINE);
        cancelBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cancelBtn.addActionListener(e -> dispose());
        summary.add(Box.createVerticalStrut(10));
        summary.add(cancelBtn);

        main.add(summary);

        add(main, BorderLayout.CENTER);
    }

    private JLabel createLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(ThemeConstants.getFont(13, Font.BOLD));
        l.setForeground(ThemeConstants.TEXT_DARK);
        l.setBorder(new EmptyBorder(5, 0, 5, 0));
        return l;
    }

    private JLabel createSummaryLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(ThemeConstants.getFont(12, Font.PLAIN));
        l.setForeground(ThemeConstants.TEXT_MUTED);
        l.setBorder(new EmptyBorder(2, 0, 2, 0));
        return l;
    }

    public boolean isConfirmed()    { return confirmed; }
    public String getInstanceName() { return nameField.getText().trim(); }
    public String getInstanceType() { return ((String) typeCombo.getSelectedItem()).split(" ")[0]; }
    public String getCommand()      { return commandField.getText().trim(); }
}
