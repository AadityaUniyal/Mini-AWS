package com.minicloud.ui;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

public class SwingMain {

    public static void main(String[] args) {
        // Install FlatLaf Dark theme — must be done before any Swing component is created
        FlatDarkLaf.setup();

        // Set global UI properties for a polished dark cloud dashboard look
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("TabbedPane.selectedBackground", new java.awt.Color(0x2D5BE3));
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);

        // Always update UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
