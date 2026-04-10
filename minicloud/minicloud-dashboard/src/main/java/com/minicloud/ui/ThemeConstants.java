package com.minicloud.ui;

import java.awt.*;

/**
 * AWS-style color palette and design tokens.
 */
public class ThemeConstants {

    // Colors
    public static final Color NAVY          = new Color(0x232F3E);
    public static final Color SIDEBAR_DARK  = new Color(0x1A2332);
    public static final Color SIDEBAR_HOVER = new Color(0x2D4156);
    public static final Color ACTIVE_BLUE   = new Color(0x0073BB);
    public static final Color BRIGHT_BLUE   = new Color(0x007EB9);
    public static final Color LIGHT_BLUE_BG = new Color(0xE8F4FD);
    public static final Color WHITE         = Color.WHITE;
    public static final Color BG_LIGHT      = new Color(0xF2F3F3);
    public static final Color BORDER_GRAY   = new Color(0xDCDCDC);
    public static final Color TEXT_DARK     = new Color(0x16191F);
    public static final Color TEXT_MUTED    = new Color(0x5F6B7A);
    public static final Color TEXT_LIGHT    = new Color(0xD5DBDB);

    // States
    public static final Color GREEN_BG      = new Color(0xE6F4EA);
    public static final Color GREEN_TEXT    = new Color(0x1D8102);
    public static final Color ORANGE_BG     = new Color(0xFDF3E7);
    public static final Color ORANGE_TEXT   = new Color(0xE07B13);
    public static final Color RED_BG        = new Color(0xFBEAEA);
    public static final Color RED_TEXT      = new Color(0xD13212);
    public static final Color GRAY_BG       = new Color(0xEAEDED);
    public static final Color GRAY_TEXT     = new Color(0x879596);

    // Fonts
    public static final String FONT_FAMILY = "Segoe UI"; // Substitute for Amazon Ember
    
    public static Font getFont(int size, int style) {
        return new Font(FONT_FAMILY, style, size);
    }
}
