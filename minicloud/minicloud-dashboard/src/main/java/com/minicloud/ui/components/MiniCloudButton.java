package com.minicloud.ui.components;

import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Custom AWS-styled button.
 */
public class MiniCloudButton extends JButton {

    public enum Type { PRIMARY, SECONDARY, DANGER, OUTLINE }

    private final Type type;
    private boolean isHovered = false;

    public MiniCloudButton(String text, Type type) {
        super(text);
        this.type = type;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(ThemeConstants.getFont(13, Font.BOLD));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg;
        Color fg;

        switch (type) {
            case PRIMARY   -> { bg = ThemeConstants.ACTIVE_BLUE; fg = Color.WHITE; }
            case DANGER    -> { bg = ThemeConstants.RED_TEXT;   fg = Color.WHITE; }
            case SECONDARY -> { bg = ThemeConstants.GRAY_BG;    fg = ThemeConstants.TEXT_DARK; }
            case OUTLINE   -> { bg = Color.WHITE;               fg = ThemeConstants.ACTIVE_BLUE; }
            default        -> { bg = ThemeConstants.ACTIVE_BLUE; fg = Color.WHITE; }
        }

        if (isHovered) {
            bg = bg.darker();
        }

        // Drop shadow or border for outline
        if (type == Type.OUTLINE) {
            g2.setColor(ThemeConstants.BORDER_GRAY);
            g2.draw(new RoundRectangle2D.Float(1, 1, getWidth()-3, getHeight()-3, 4, 4));
        }

        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 4, 4));

        g2.setColor(fg);
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(getText())) / 2;
        int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(getText(), x, y);

        g2.dispose();
    }
}
