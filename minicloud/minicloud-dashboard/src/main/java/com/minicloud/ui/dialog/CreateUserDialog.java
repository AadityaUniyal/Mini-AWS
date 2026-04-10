package com.minicloud.ui.dialog;

import com.minicloud.ui.components.MiniCloudButton;
import com.minicloud.ui.ThemeConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Redesigned CreateUserDialog — AWS style.
 */
public class CreateUserDialog extends JDialog {

    private boolean confirmed = false;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<String> roleCombo;

    public CreateUserDialog(Frame parent) {
        super(parent, "Create user", true);
        initUI();
        setSize(450, 420);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 40, 20, 40));

        JLabel title = new JLabel("Create user");
        title.setFont(ThemeConstants.getFont(18, Font.BOLD));
        title.setForeground(ThemeConstants.TEXT_DARK);
        panel.add(title);
        panel.add(Box.createVerticalStrut(25));

        // Username
        panel.add(createLabel("User name"));
        usernameField = new JTextField();
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        panel.add(usernameField);
        panel.add(Box.createVerticalStrut(15));

        // Password
        panel.add(createLabel("Console password"));
        passwordField = new JPasswordField();
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        panel.add(passwordField);
        panel.add(Box.createVerticalStrut(15));

        // Role
        panel.add(createLabel("User role"));
        roleCombo = new JComboBox<>(new String[]{"DEVELOPER", "ADMIN", "READONLY"});
        roleCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        panel.add(roleCombo);
        panel.add(Box.createVerticalStrut(30));

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);

        MiniCloudButton cancelBtn = new MiniCloudButton("Cancel", MiniCloudButton.Type.OUTLINE);
        cancelBtn.addActionListener(e -> dispose());

        MiniCloudButton createBtn = new MiniCloudButton("Create user", MiniCloudButton.Type.PRIMARY);
        createBtn.addActionListener(e -> {
            if (usernameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter a user name");
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
    public String getNewUsername(){ return usernameField.getText().trim(); }
    public String getNewPassword(){ return new String(passwordField.getPassword()); }
    public String getRole()       { return (String) roleCombo.getSelectedItem(); }
}
