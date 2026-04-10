package com.minicloud.ui.components;

import com.minicloud.ui.ThemeConstants;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Custom JTable with AWS styling.
 */
public class MiniCloudTable extends JTable {

    public MiniCloudTable() {
        setRowHeight(40);
        setBackground(Color.WHITE);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setSelectionBackground(ThemeConstants.LIGHT_BLUE_BG);
        setSelectionForeground(ThemeConstants.TEXT_DARK);
        setFocusable(false);
        setAutoCreateRowSorter(true);

        JTableHeader header = getTableHeader();
        header.setPreferredSize(new Dimension(0, 40));
        header.setBackground(ThemeConstants.BG_LIGHT);
        header.setForeground(ThemeConstants.TEXT_MUTED);
        header.setFont(ThemeConstants.getFont(12, Font.BOLD));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeConstants.BORDER_GRAY));

        setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeConstants.BORDER_GRAY));
                
                if (!isSelected) {
                    if (row % 2 != 0) {
                        c.setBackground(new Color(0xFAFAFA));
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                }
                
                if (isSelected) {
                    // Left border indicator for selected row
                    ((JComponent)c).setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 1, 0, ThemeConstants.ACTIVE_BLUE),
                        BorderFactory.createEmptyBorder(0, 4, 0, 0)
                    ));
                }
                
                setFont(ThemeConstants.getFont(13, Font.PLAIN));
                setForeground(ThemeConstants.TEXT_DARK);
                return c;
            }
        });
    }
}
