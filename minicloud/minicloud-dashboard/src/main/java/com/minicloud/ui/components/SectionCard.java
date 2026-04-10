package com.minicloud.ui.components;

import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modern card container for metrics and dashboard sections.
 */
public class SectionCard extends JPanel {

    private final JLabel titleLabel;
    private final JLabel valueLabel;
    private final JPanel contentPanel;

    public SectionCard(String title) {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(ThemeConstants.BORDER_GRAY, 1, true),
            new EmptyBorder(16, 16, 16, 16)
        ));

        titleLabel = new JLabel(title);
        titleLabel.setFont(ThemeConstants.getFont(13, Font.PLAIN));
        titleLabel.setForeground(ThemeConstants.TEXT_MUTED);
        add(titleLabel, BorderLayout.NORTH);

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        
        valueLabel = new JLabel("");
        valueLabel.setFont(ThemeConstants.getFont(24, Font.BOLD));
        valueLabel.setForeground(ThemeConstants.TEXT_DARK);
        contentPanel.add(valueLabel, BorderLayout.NORTH);

        add(contentPanel, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }
}
