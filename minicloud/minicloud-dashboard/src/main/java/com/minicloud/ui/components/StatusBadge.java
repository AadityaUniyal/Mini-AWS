package com.minicloud.ui.components;

import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Pill-shaped status badge following AWS design.
 */
public class StatusBadge extends JLabel {

    public enum ColorTheme { GREEN, ORANGE, RED, GRAY, BLUE }

    private final ColorTheme theme;

    public StatusBadge(String text, ColorTheme theme) {
        super("  ●  " + text + "  ");
        this.theme = theme;
        setOpaque(false);
        setFont(ThemeConstants.getFont(11, Font.BOLD));
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg, fg;
        switch (theme) {
            case GREEN  -> { bg = ThemeConstants.GREEN_BG;  fg = ThemeConstants.GREEN_TEXT; }
            case ORANGE -> { bg = ThemeConstants.ORANGE_BG; fg = ThemeConstants.ORANGE_TEXT; }
            case RED    -> { bg = ThemeConstants.RED_BG;    fg = ThemeConstants.RED_TEXT; }
            case GRAY   -> { bg = ThemeConstants.GRAY_BG;   fg = ThemeConstants.GRAY_TEXT; }
            case BLUE   -> { bg = ThemeConstants.LIGHT_BLUE_BG; fg = ThemeConstants.ACTIVE_BLUE; }
            default     -> { bg = ThemeConstants.GRAY_BG;   fg = ThemeConstants.GRAY_TEXT; }
        }

        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

        setForeground(fg);
        super.paintComponent(g2);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 10, d.height + 4);
    }
}
