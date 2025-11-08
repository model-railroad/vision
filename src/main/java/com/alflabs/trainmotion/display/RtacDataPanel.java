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

import com.alflabs.trainmotion.util.ILogger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

public class RtacDataPanel extends JPanel {
    private static final String TAG = RtacDataPanel.class.getSimpleName();

    private static final Font mFont1 = new Font(Font.SANS_SERIF, Font.BOLD, 24);
    private static final Font mFont2 = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font mFont3 = new Font(Font.SANS_SERIF, Font.PLAIN, 8);
    private final ILogger mLogger;

    public RtacDataPanel(ILogger logger) {
        super(new GridBagLayout());
        mLogger = logger;
        setBackground(KioskView.BG_COLOR);
        setBorder(BorderFactory.createEmptyBorder(/*top*/ 0, /*left*/ 0, /*bottom*/ 0, /*right*/ 10));

        RtacDataView v = new RtacDataView();
        add(v, constraint(0, 0, 1, 1));

        v = new RtacDataView();
        add(v, constraint(0, 1, 1, 1));

        v = new RtacDataView();
        add(v, constraint(0, 2, 1, 1));
    }

    private static GridBagConstraints constraint(int gridx, int gridy, int gridw, int weighty) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = gridx;
        c.gridy = gridy;
        c.gridwidth = gridw;
        c.gridheight = 1;
        c.weightx = 1;
        c.weighty = weighty;
        c.fill = weighty == 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.VERTICAL;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static GridBagConstraints constraint(int gridx, int gridy, int gridw) {
        return constraint(gridx, gridy, gridw, 0);
    }

    private static class RtacDataView extends JPanel {
        private final JLabel mTitle;
        private final JLabel mToggle;
        private final JLabel mStatus;
        private final JLabel mDir;
        private final JLabel mSpeed;
        private final JLabel mCounter;

        public RtacDataView() {
            super(new GridBagLayout());
            setBackground(new Color(8, 8, 8));

            mTitle = new JLabel("title");
            mTitle.setFont(mFont1);
            mTitle.setForeground(Color.LIGHT_GRAY);
            add(mTitle, constraint(0, 0, 2));

            mToggle = new JLabel("toggle");
            mToggle.setFont(mFont2);
            mToggle.setForeground(Color.RED);
            add(mToggle, constraint(0, 1, 2));

            mStatus = new JLabel("status");
            mStatus.setFont(mFont2);
            mStatus.setForeground(Color.GREEN);
            add(mStatus, constraint(0, 2, 2));

            mDir = new JLabel("dir");
            mDir.setFont(mFont2);
            mDir.setForeground(Color.LIGHT_GRAY);
            mDir.setBorder(BorderFactory.createEmptyBorder(/*top*/ 0, /*left*/ 0, /*bottom*/ 0, /*right*/ 10));
            add(mDir, constraint(0, 3, 1));

            mSpeed = new JLabel("speed");
            mSpeed.setFont(mFont2);
            mSpeed.setForeground(Color.LIGHT_GRAY);
            add(mSpeed, constraint(1, 3, 1));

            mCounter = new JLabel("counter");
            mCounter.setFont(mFont3);
            mCounter.setForeground(Color.LIGHT_GRAY);
            add(mCounter, constraint(0, 4, 2));
        }
    }
}
