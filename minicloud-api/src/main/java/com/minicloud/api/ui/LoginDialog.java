package com.minicloud.api.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * AWS-styled login dialog. Appears before the main window.
 */
public class LoginDialog extends JDialog {

    private boolean authenticated = false;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainCardPanel = new JPanel(cardLayout);

    // Identity selection
    private JRadioButton rootRadio;
    private JRadioButton iamRadio;

    // Identifier Entry
    private JTextField identifierField; // Email or AccountId
    private JLabel identifierLabel;

    // Credentials Entry
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel backLabel;

    // Registration
    private JTextField regEmailField;
    private JTextField regNameField;
    private JPasswordField regPassField;

    // Colors (AWS Light Theme)
    private static final Color AWS_BG = new Color(242, 243, 243);
    private static final Color AWS_WHITE = Color.WHITE;
    private static final Color AWS_BLUE = new Color(0, 103, 184); // Link blue
    private static final Color AWS_ORANGE = new Color(255, 153, 0);

    public LoginDialog(Frame parent) {
        super(parent, "MiniCloud — Sign In", true);
        buildUI();
        setSize(480, 520);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }


    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AWS_BG);

        // Header (Logo)
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setBorder(new javax.swing.border.EmptyBorder(40, 0, 20, 0));
        JLabel logo = new JLabel("☁ MiniCloud", SwingConstants.CENTER);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 28));
        logo.setForeground(Color.DARK_GRAY);
        header.add(logo);

        // Setup Cards
        mainCardPanel.setOpaque(false);
        mainCardPanel.add(createSelectionPanel(), "SELECTION");
        mainCardPanel.add(createIdentifierPanel(), "IDENTIFIER");
        mainCardPanel.add(createPasswordPanel(), "PASSWORD");
        mainCardPanel.add(createRegisterPanel(), "REGISTER");
        mainCardPanel.add(createSuccessPanel(""), "SUCCESS");

        root.add(header, BorderLayout.NORTH);
        root.add(mainCardPanel, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel createSelectionPanel() {
        JPanel card = createWhiteCard("Sign in");
        
        rootRadio = new JRadioButton("Root user");
        iamRadio = new JRadioButton("IAM user");
        ButtonGroup group = new ButtonGroup();
        group.add(rootRadio);
        group.add(iamRadio);
        iamRadio.setSelected(true);

        styleRadio(rootRadio);
        styleRadio(iamRadio);

        JLabel rootDesc = createSmallLabel("Email address that provides unrestricted access.");
        JLabel iamDesc = createSmallLabel("Account ID and unique user name within account.");

        JButton next = createOrangeButton("Next");
        next.addActionListener(e -> {
            identifierLabel.setText(rootRadio.isSelected() ? "Root user email address" : "Account ID (12 digits) or account alias");
            cardLayout.show(mainCardPanel, "IDENTIFIER");
        });

        JButton register = new JButton("Create a new MiniCloud account");
        styleLinkButton(register);
        register.addActionListener(e -> cardLayout.show(mainCardPanel, "REGISTER"));

        card.add(Box.createVerticalStrut(10));
        card.add(rootRadio);
        card.add(rootDesc);
        card.add(Box.createVerticalStrut(15));
        card.add(iamRadio);
        card.add(iamDesc);
        card.add(Box.createVerticalStrut(25));
        card.add(next);
        card.add(Box.createVerticalStrut(15));
        card.add(register);

        return wrapInContainer(card);
    }

    private JPanel createIdentifierPanel() {
        JPanel card = createWhiteCard("Sign in");
        identifierLabel = createFieldLabel("Identifier");
        identifierField = new JTextField();
        styleTextField(identifierField);

        JButton next = createOrangeButton("Next");
        next.addActionListener(e -> {
            // Just show the already-built PASSWORD card — don't recreate it
            cardLayout.show(mainCardPanel, "PASSWORD");
        });

        JButton back = new JButton("Sign in as a different user");
        styleLinkButton(back);
        back.addActionListener(e -> cardLayout.show(mainCardPanel, "SELECTION"));

        card.add(Box.createVerticalStrut(10));
        card.add(identifierLabel);
        card.add(identifierField);
        card.add(Box.createVerticalStrut(25));
        card.add(next);
        card.add(Box.createVerticalStrut(15));
        card.add(back);

        return wrapInContainer(card);
    }

    private JPanel createPasswordPanel() {
        JPanel card = createWhiteCard("Sign in");

        usernameField = new JTextField();
        styleTextField(usernameField);
        passwordField = new JPasswordField();
        styleTextField(passwordField);

        JButton signIn = createOrangeButton("Sign in");
        signIn.addActionListener(e -> attemptLogin());

        JButton back = new JButton("Back");
        styleLinkButton(back);
        back.addActionListener(e -> cardLayout.show(mainCardPanel, "IDENTIFIER"));

        JLabel userLabel = createFieldLabel("IAM user name");

        // Dynamically show/hide the IAM username field based on radio selection
        // when this panel becomes visible, not at construction time.
        card.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                boolean isIam = iamRadio != null && iamRadio.isSelected();
                userLabel.setVisible(isIam);
                usernameField.setVisible(isIam);
                card.revalidate();
                card.repaint();
            }
        });

        card.add(Box.createVerticalStrut(10));
        card.add(userLabel);
        card.add(usernameField);
        card.add(Box.createVerticalStrut(15));
        card.add(createFieldLabel("Password"));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(25));
        card.add(signIn);
        card.add(Box.createVerticalStrut(15));
        card.add(back);

        return wrapInContainer(card);
    }

    private JPanel createRegisterPanel() {
        JPanel card = createWhiteCard("Create account");
        
        regNameField = new JTextField();
        styleTextField(regNameField);
        regEmailField = new JTextField();
        styleTextField(regEmailField);
        regPassField = new JPasswordField();
        styleTextField(regPassField);

        JButton register = createOrangeButton("Verify Email & Create");
        register.addActionListener(e -> attemptRegister());

        JButton back = new JButton("Back to sign in");
        styleLinkButton(back);
        back.addActionListener(e -> cardLayout.show(mainCardPanel, "SELECTION"));

        card.add(createFieldLabel("Account Name"));
        card.add(regNameField);
        card.add(Box.createVerticalStrut(15));
        card.add(createFieldLabel("Email Address"));
        card.add(regEmailField);
        card.add(Box.createVerticalStrut(15));
        card.add(createFieldLabel("Password"));
        card.add(regPassField);
        card.add(Box.createVerticalStrut(25));
        card.add(register);
        card.add(Box.createVerticalStrut(15));
        card.add(back);

        return wrapInContainer(card);
    }

    private JPanel createSuccessPanel(String accountId) {
        JPanel card = createWhiteCard("Account Created!");
        JLabel idLabel = new JLabel("Your Account ID: " + accountId, SwingConstants.CENTER);
        idLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        idLabel.setForeground(new Color(46, 125, 50));

        JLabel qrLabel = new JLabel();
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        if (!accountId.isEmpty()) {
            try {
                qrLabel.setIcon(generateQR(accountId));
            } catch (Exception e) {
                qrLabel.setText("QR Generation Error");
            }
        }

        JButton finish = createOrangeButton("Continue to Sign In");
        finish.addActionListener(e -> cardLayout.show(mainCardPanel, "SELECTION"));

        card.add(Box.createVerticalStrut(10));
        card.add(idLabel);
        card.add(Box.createVerticalStrut(20));
        card.add(new JLabel("Save this QR or Account ID:", SwingConstants.CENTER));
        card.add(Box.createVerticalStrut(10));
        card.add(qrLabel);
        card.add(Box.createVerticalStrut(30));
        card.add(finish);
        
        return wrapInContainer(card);
    }

    private void attemptLogin() {
        String type = rootRadio.isSelected() ? "ROOT" : "IAM";
        String identifier = identifierField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (ApiClient.login(type, identifier, user, pass)) {
            authenticated = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Login failed. Please check your credentials.", "Authentication Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void attemptRegister() {
        String name = regNameField.getText().trim();
        String email = regEmailField.getText().trim();
        String pass = new String(regPassField.getPassword());

        String accountId = ApiClient.register(name, email, pass);
        if (accountId != null) {
            mainCardPanel.add(createSuccessPanel(accountId), "SUCCESS");
            cardLayout.show(mainCardPanel, "SUCCESS");
        } else {
            JOptionPane.showMessageDialog(this, "Registration failed.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ImageIcon generateQR(String text) throws Exception {
        int size = 150;
        com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
        com.google.zxing.common.BitMatrix bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
        
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                img.setRGB(x, y, bitMatrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return new ImageIcon(img);
    }

    // ── Stylers ───────────────────────────────────────────────────────────────

    private JPanel createWhiteCard(String title) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AWS_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(24, 32, 24, 32)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(20));
        return card;
    }

    private JPanel wrapInContainer(JPanel card) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(card);
        return wrapper;
    }

    private void styleTextField(JTextField f) {
        f.setMaximumSize(new Dimension(340, 35));
        f.setPreferredSize(new Dimension(340, 35));
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JButton createOrangeButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(AWS_ORANGE);
        b.setForeground(Color.BLACK);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setMaximumSize(new Dimension(340, 40));
        b.setPreferredSize(new Dimension(340, 40));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    private void styleLinkButton(JButton b) {
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setForeground(AWS_BLUE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void styleRadio(JRadioButton r) {
        r.setOpaque(false);
        r.setFont(new Font("Segoe UI", Font.BOLD, 14));
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JLabel createSmallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(Color.GRAY);
        l.setBorder(BorderFactory.createEmptyBorder(0, 25, 0, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel createFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public boolean isAuthenticated() { return authenticated; }
}
