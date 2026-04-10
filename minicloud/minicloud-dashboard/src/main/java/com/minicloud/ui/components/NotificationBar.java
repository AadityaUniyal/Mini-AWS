package com.minicloud.ui.components;

import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Animated notification banner system.
 */
public class NotificationBar extends JPanel {

    public enum Type { SUCCESS, ERROR, WARNING, INFO }

    public static void show(JComponent parent, String message, Type type) {
        NotificationBar bar = new NotificationBar(message, type);
        parent.add(bar, 0); // Add at the very top
        parent.revalidate();
        parent.repaint();

        // Auto-dismiss for non-error
        if (type != Type.ERROR) {
            Timer timer = new Timer(5000, e -> {
                parent.remove(bar);
                parent.revalidate();
                parent.repaint();
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

    private NotificationBar(String message, Type type) {
        setLayout(new BorderLayout(10, 0));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        setPreferredSize(new Dimension(0, 45));

        Color bg, fg, border;
        String icon;

        switch (type) {
            case SUCCESS -> { bg = ThemeConstants.GREEN_BG;  fg = ThemeConstants.GREEN_TEXT; border = ThemeConstants.GREEN_TEXT; icon = "✔"; }
            case ERROR   -> { bg = ThemeConstants.RED_BG;    fg = ThemeConstants.RED_TEXT;   border = ThemeConstants.RED_TEXT;   icon = "✖"; }
            case WARNING -> { bg = ThemeConstants.ORANGE_BG; fg = ThemeConstants.ORANGE_TEXT;  border = ThemeConstants.ORANGE_TEXT; icon = "⚠"; }
            case INFO    -> { bg = ThemeConstants.LIGHT_BLUE_BG; fg = ThemeConstants.ACTIVE_BLUE; border = ThemeConstants.ACTIVE_BLUE; icon = "ℹ"; }
            default      -> { bg = ThemeConstants.BG_LIGHT;  fg = ThemeConstants.TEXT_DARK;  border = ThemeConstants.BORDER_GRAY; icon = "•"; }
        }

        setBackground(bg);
        setBorder(new MatteBorder(1, 1, 1, 1, border));

        JLabel msgLabel = new JLabel(icon + "  " + message);
        msgLabel.setForeground(fg);
        msgLabel.setFont(ThemeConstants.getFont(13, Font.BOLD));
        msgLabel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
        add(msgLabel, BorderLayout.CENTER);

        JButton closeBtn = new JButton("✕");
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setForeground(fg);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            Container parent = getParent();
            if (parent != null) {
                parent.remove(this);
                parent.revalidate();
                parent.repaint();
            }
        });
        add(closeBtn, BorderLayout.EAST);
    }
}
