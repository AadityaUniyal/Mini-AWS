package com.minicloud.ui.components;

import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Styled search field for the navbar.
 */
public class SearchField extends JTextField {

    private final String placeholder;

    public SearchField(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(true);
        setBackground(new Color(0x3A4B5C));
        setForeground(Color.WHITE);
        setCaretColor(Color.WHITE);
        setFont(ThemeConstants.getFont(13, Font.PLAIN));
        setBorder(new EmptyBorder(0, 35, 0, 10));
        setPreferredSize(new Dimension(300, 32));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0x95A5A6));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, 35, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        // Draw Search Icon
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0x95A5A6));
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(12, 9, 10, 10);
        g2.drawLine(20, 17, 24, 21);
        g2.dispose();
    }
}
