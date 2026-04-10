package com.minicloud.ui.dialog;

import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Redesigned CreateBucketDialog — AWS style.
 */
public class CreateBucketDialog extends JDialog {

    private boolean confirmed = false;
    private JTextField nameField;

    public CreateBucketDialog(Frame parent) {
        super(parent, "Create bucket", true);
        initUI();
        setSize(450, 280);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 40, 20, 40));

        JLabel title = new JLabel("Create bucket");
        title.setFont(ThemeConstants.getFont(18, Font.BOLD));
        title.setForeground(ThemeConstants.TEXT_DARK);
        panel.add(title);
        panel.add(Box.createVerticalStrut(25));

        panel.add(createLabel("Bucket name"));
        nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        panel.add(nameField);

        JLabel hint = new JLabel("Bucket names must be unique and can consist of lowercase letters, numbers, and hyphens.");
        hint.setFont(ThemeConstants.getFont(11, Font.PLAIN));
        hint.setForeground(ThemeConstants.TEXT_MUTED);
        panel.add(Box.createVerticalStrut(5));
        panel.add(hint);
        
        panel.add(Box.createVerticalStrut(30));

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);

        MiniCloudButton cancelBtn = new MiniCloudButton("Cancel", MiniCloudButton.Type.OUTLINE);
        cancelBtn.addActionListener(e -> dispose());

        MiniCloudButton createBtn = new MiniCloudButton("Create bucket", MiniCloudButton.Type.PRIMARY);
        createBtn.addActionListener(e -> {
            if (nameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter a bucket name");
                return;
            }
            confirmed = true;
            dispose();
        });

        btnRow.add(cancelBtn);
        btnRow.add(createBtn);
        panel.add(btnRow);

        add(panel, BorderLayout.CENTER);
    }

    private JLabel createLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(ThemeConstants.getFont(13, Font.BOLD));
        l.setForeground(ThemeConstants.TEXT_DARK);
        l.setBorder(new EmptyBorder(0, 0, 5, 0));
        return l;
    }

    public boolean isConfirmed()  { return confirmed; }
    public String getBucketName() { return nameField.getText().trim(); }
}
