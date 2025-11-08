/*
 * Project: Train-Motion
 * Copyright (C) 2025 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alflabs.trainmotion.display;

import com.alflabs.annotations.Null;
import com.alflabs.trainmotion.util.ILogger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Typical PSA strings from RTAC:
 *
 * Placeholder: "{bg:black}{b:red}{c:white}Automation Not Working";
 * Script v35:
 * rtacPsaText = "Automation Started"
 * rtacPsaText = "{c:red}Automation Stopped"
 * rtacPsaText = "{b:red}{c:white}Automation ERROR"
 * rtacPsaText = "{c:red}Automation Turned Off\\nat 4:50 PM"
 * rtacPsaText = "{c:red}Saturday Trains Running"
 * rtacPsaText = "{c:blue}Next Train:\\n${PA_Data.PSA_Name}"
 * rtacPsaText = "{c:blue}Next Train:\\n${PA_Data.PSA_Name}\\nLeaving in 1 minute"
 * rtacPsaText = "{c:#FF008800}Next Train:\\n${FR_Data.PSA_Name}"
 * rtacPsaText = "{c:#FF008800}Next Train:\\n${FR_Data.PSA_Name}\\nLeaving in 1 minute"
 * rtacPsaText = "{c:blue}Currently Running:\\n${PA_Data.PSA_Name}"
 * rtacPsaText = "{c:#FF008800}Currently Running:\\n${FR_Data.PSA_Name}"
 * rtacPsaText = "{b:blue}{c:white}Automation Warning\\nCheck Track $names"
 *
 * The original PSA text was designed for a 4:3 tablet screen. As such some texts
 * have 3 lines to make better use of the available vertical space.
 *
 * In Vision, we have a wide horizontal bar. The suggestion is to "merge" the two
 * first lines into one, and if there's a 3rd one place it below with a smaller font.
 */

public class RtacPsaPanel extends JPanel {
    private static final String TAG = RtacPsaPanel.class.getSimpleName();

    private final JLabel mLine1;
    private final JLabel mLine2;
    private final ILogger mLogger;

    public RtacPsaPanel(ILogger logger) {
        super(new GridBagLayout());
        mLogger = logger;
        setBackground(KioskView.BG_COLOR);
        setBorder(BorderFactory.createEmptyBorder(/*top*/ 10, /*left*/ 0, /*bottom*/ 0, /*right*/ 0));

        Font font1 = new Font(Font.SANS_SERIF, Font.BOLD, 48);
        Font font2 = new Font(Font.SANS_SERIF, Font.BOLD, 24);

        mLine1 = new JLabel("", SwingConstants.CENTER);
        mLine1.setOpaque(true);
        mLine1.setFont(font1);
        mLine1.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(mLine1, constraint(0));

        mLine2 = new JLabel("", SwingConstants.CENTER);
        mLine2.setOpaque(true);
        mLine2.setFont(font2);
        mLine2.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(mLine2, constraint(1));

        updateText(null);
        // Force the initial size computation to use both line heights.
        mLine2.setText("");
    }

    private GridBagConstraints constraint(int gridy) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 1;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        return c;
    }

    @SuppressWarnings("RegExpRedundantEscape")  // lint warning is wrong, the "redundant escape" of } is necessary.
    private static final Pattern sAttribRe = Pattern.compile("^\\{([a-z]{1,2}):([^}]+)\\}(.*)");

    // Source: Conductor Project
    // android/RTAC/app/src/main/java/com/alflabs/rtac/fragment/PsaTextFragment.java
    public void updateText(@Null String text) {
        if (text == null) {
            text = "{bg:black}{b:red}{c:white}Automation Not Working";
        }

        String originalText = text;

        Color txColor = Color.WHITE;
        Color bgColor = KioskView.BG_COLOR;

        while (!text.isEmpty()) {
            text = text.trim();
            Matcher m = sAttribRe.matcher(text);
            if (!m.matches()) {
                break;
            }
            String key = m.group(1);
            String val = m.group(2);
            text = m.group(3);

            try {
                Color col = parseColor(val);

                switch (key) {
                case "c":
                    // Text area font color -- defaults to black.
                    txColor = col;
                    break;
                case "b":
                    // Text area background color -- defaults to transparent.
                    bgColor = col;
                    break;
                case "bg":
                    // Root view background color -- defaults to white.
                    // This was used in RTAC to set the parent's view background color.
                    // Not used in Vision where the parent frame background is always black.
                    // rootColor = col;
                    break;
                default:
                    mLogger.log(TAG, "Ignoring invalid PSA text formatter {" + key + "} in " + originalText);
                }
            } catch (IllegalArgumentException invalidColor) {
                mLogger.log(TAG, "Invalid color name {..:" + val + "} in " + originalText);
            }
        }

        mLine1.setBackground(bgColor);
        mLine1.setForeground(txColor);
        mLine2.setBackground(bgColor);
        mLine2.setForeground(txColor);

        // Reminder: search pattern is a regex so "\" must be escaped twice.
        String[] lines = text.split("\\\\n");
        if (lines.length > 1) {
            lines[0] += " " + lines[1];
        }
        // The first line should never be empty.
        mLine1.setText(lines.length > 0 ? lines[0] : " ");
        // When the second line is empty, the first line gets centered vertically.
        mLine2.setText(lines.length > 2 ? lines[2] : "");
    }

    /// Parses an HTM color name (e.g. "#RRGGBB" or "black")
    private Color parseColor(String val) {
        try {
            // Handle straightforward common names
            if (val.equals("red")) {
                return Color.RED;
            }
            if (val.equals("green")) {
                return Color.GREEN;
            }
            if (val.equals("blue")) {
                return Color.BLUE;
            }
            if (val.equals("white")) {
                return Color.WHITE;
            }
            if (val.equals("black")) {
                return Color.BLACK;
            }

            // Handle the case of a hexadecimal color
            if (val.startsWith("#")) {
                String hex = val.substring(1);
                if (hex.length() == 8) {
                    // That should be "#AARRGGBB". Drop the alpha.
                    hex = hex.substring(2);
                }
                if (hex.length() == 6) {
                    // That should be "#RRGGBB".
                    return Color.decode("0x" + hex);
                }
            }

            // Otherwise, try the more expensive lookup of the color name by reflection.
            // This would typically fail with NoSuchFieldException.
            Field field = Color.class.getField(val.toUpperCase(Locale.US));
            return (Color) field.get(null);

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse color: " + val, e);
        }
    }
}
