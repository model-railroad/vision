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

import com.alflabs.annotations.NonNull;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class StatusView extends JComponent {

    private static final String ROOT_KEY = "@root@";
    private final Map<String, JLabel> mLabels = new TreeMap<>();

    public StatusView(@NonNull StringInfo placeholderInfo) {
        // Display a placeholder text till we get at least one real status label
        setStatus(ROOT_KEY, placeholderInfo);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                super.componentResized(event);
                onComponentResized();
            }
        });
    }

    public void setDefaultLayout() {
        super.setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    }

    public void setStatus(@NonNull String key, @NonNull StringInfo info) {
        JLabel label = getOrCreateLabel(key);
        if (label != null) {
            label.setText(info.mMsg);

            // A quick hack to test coloring the label to respond to camera activty
            Color color = Color.LIGHT_GRAY;
            switch (info.mFlag) {
            case Active:
                color = Color.YELLOW;
                break;
            case On:
                color = Color.GREEN;
                break;
            }
            label.setForeground(color);
        }
    }

    @NonNull
    private JLabel getOrCreateLabel(@NonNull String key) {
        JLabel label = mLabels.get(key);

        if (label == null) {
            // Create and append the label
            label = new JLabel("--");
            label.setOpaque(true);
            label.setBackground(KioskView.BG_COLOR);
            label.setForeground(Color.LIGHT_GRAY);
            label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            label.setAlignmentX(Component.CENTER_ALIGNMENT);

            mLabels.put(key, label);

            // Remove the initial placeholder text if still present
            if (!ROOT_KEY.equals(key)) {
                mLabels.remove(ROOT_KEY);
            }

            // To ensure all labels are in the same order as the sorted map,
            // we remove all labels and re-create them in the proper order.
            // There's probably a fancy way to do this, yet we only create labels once.

            this.removeAll();
            for(JLabel v : mLabels.values()) {
                this.add(v);
            }
            onComponentResized();
        }

        return label;
    }

    @Override
    public Dimension getPreferredSize() {
        Optional<String> firstKey = mLabels.keySet().stream().findFirst();
        if (firstKey.isPresent()) {
            JLabel firstLabel = mLabels.get(firstKey.get());
            if (firstLabel != null) {
                return firstLabel.getPreferredSize();
            }
        }
        return super.getPreferredSize();
    }

    private void onComponentResized() {
        Rectangle bounds = getBounds();
        int width = bounds.width ; // / mLabels.size();

        for(JLabel v : mLabels.values()) {
            Dimension s = v.getPreferredSize();
            s.width = width;
            v.setPreferredSize(s);
        }
    }

}
