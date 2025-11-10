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

import com.alflabs.kv.IKeyValue;
import com.alflabs.manifest.Constants;
import com.alflabs.manifest.RouteInfo;
import com.alflabs.manifest.RouteInfos;
import com.alflabs.trainmotion.util.ILogger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RtacDataPanel extends JPanel {
    private static final String TAG = RtacDataPanel.class.getSimpleName();

    private static final Color BG_COLOR = new Color(8, 8, 8);
    private static final Font mFont1 = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font mFont2 = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font mFont3 = new Font(Font.SANS_SERIF, Font.PLAIN, 8);

    private final ILogger mLogger;
    private final List<RtacDataView> mViews = new ArrayList<>();

    public RtacDataPanel(ILogger logger) {
        super(new GridBagLayout());
        mLogger = logger;
        setBackground(BG_COLOR);
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
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    private static GridBagConstraints constraint(int gridx, int gridy, int gridw) {
        return constraint(gridx, gridy, gridw, 0);
    }

    public void initializeRoutes(IKeyValue kvClient, String jsonRoutes) {
        removeAll();
        mViews.clear();

        try {
            RouteInfos infos = RouteInfos.parseJson(jsonRoutes);
            mLogger.log(TAG, "@@ Adding " + infos.getRouteInfos().length + " routes");

            int y = 0;
            for (RouteInfo info : infos.getRouteInfos()) {

                RtacDataView v = new RtacDataView(info);
                add(v, constraint(0, y++, 1, 0));
                mViews.add(v);

                updateCell(kvClient, v, info.getStatusKey());
                updateCell(kvClient, v, info.getThrottleKey());
                updateCell(kvClient, v, info.getToggleKey());
            }

            // Add a filler to force all views to the top "north"
            JPanel filler = new JPanel();
            filler.setBackground(new Color(8, 8, 8));
            add(filler, constraint(0, y, 1, 1));

        } catch (IOException e) {
            mLogger.log(TAG, "@@ Parse RouteInfos JSON error: " + e);
        }
    }

    private void updateCell(IKeyValue kvClient, RtacDataView v, String key) {
        v.onKVChanged(key, kvClient.getValue(key));
    }

    public void onKVChanged(String key, String value) {
        for (RtacDataView v : mViews) {
            v.onKVChanged(key, value);
        }
    }

    private static class RtacDataView extends JPanel {
        private final RouteInfo mRouteInfo;
        private final JLabel mToggle;
        private final JLabel mStatus;
        private final JLabel mDir;
        private final JLabel mSpeed;

        public RtacDataView(RouteInfo routeInfo) {
            super(new GridBagLayout());
            setBackground(new Color(8, 8, 8));
            setBorder(BorderFactory.createEmptyBorder(/*top*/ 20, /*left*/ 5, /*bottom*/ 20, /*right*/ 10));
            mRouteInfo = routeInfo;

            JLabel title = new JLabel(routeInfo.getName());
            title.setFont(mFont1);
            title.setForeground(Color.LIGHT_GRAY);
            add(title, constraint(0, 0, 2));

            mToggle = new JLabel(" ");
            mToggle.setFont(mFont2);
            mToggle.setForeground(Color.RED);
            add(mToggle, constraint(0, 1, 2));

            mStatus = new JLabel(" ");
            mStatus.setFont(mFont2);
            mStatus.setForeground(Color.GREEN);
            add(mStatus, constraint(0, 2, 2));

            mDir = new JLabel(" ");
            mDir.setFont(mFont2);
            mDir.setForeground(Color.LIGHT_GRAY);
            mDir.setBorder(BorderFactory.createEmptyBorder(/*top*/ 0, /*left*/ 0, /*bottom*/ 0, /*right*/ 10));
            add(mDir, constraint(0, 3, 1));

            mSpeed = new JLabel(" ");
            mSpeed.setFont(mFont2);
            mSpeed.setForeground(Color.LIGHT_GRAY);
            add(mSpeed, constraint(1, 3, 1));
        }

        public void onKVChanged(String key, String value) {
            if (value == null) {
                return;
            }
            if (key.equals(mRouteInfo.getToggleKey())) {
                mToggle.setText(value);
                mToggle.setForeground(Constants.On.equals(value) ? Color.RED : Color.GREEN);

            } else if (key.equals(mRouteInfo.getStatusKey())) {
                mStatus.setText(value);

            } else if (key.equals(mRouteInfo.getThrottleKey())) {
                try {
                    int speed = Integer.parseInt(value);
                    mSpeed.setText(Integer.toString(Math.abs(speed)));
                    mDir.setText(speed < 0 ? "Rev" : (speed > 0 ? "Fwd" : "Stop"));
                } catch (Exception e) {
                    // Log.e(TAG, "Failed to parse speed: '" + value + "'", e);
                }
            }
        }
    }
}
