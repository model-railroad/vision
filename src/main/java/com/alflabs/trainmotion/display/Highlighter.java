/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
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

import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import javax.annotation.Nonnull;
import java.awt.Color;

@AutoFactory
public class Highlighter {
    // Highlight color
    static final Color HIGHLIGHT_LINE_COLOR = Color.YELLOW;
    // Highlight minimum display duration with video motion ON. Total with OFF is 3 seconds.
    static final long HIGHLIGHT_DURATION_ON_MS = 2500;
    // Highlight minimum display duration with video motion OFF after a ON event.
    static final long HIGHLIGHT_DURATION_OFF_MS = 500;
    // Highlight stroke width
    static final int HIGHLIGHT_LINE_SIZE = 10;

    private final IClock mClock;
    private final CamInfo mCamInfo;

    /** Show highlight if > 0. Indicates when highlight ON started. */
    private long mHighlightOnMS;
    /** Show highlight if > 0. Indicates when highlight OFF started. */
    private long mHighlightOffMS;


    public Highlighter(
            @Provided IClock clock,
            @Nonnull CamInfo camInfo) {
        mClock = clock;
        mCamInfo = camInfo;
    }

    public boolean isHighlighted() {
        return mHighlightOnMS > 0;
    }

    public void update() {
        long nowMs = mClock.elapsedRealtime();
        boolean motionDetected = mCamInfo.getAnalyzer().isMotionDetected();
        if (mHighlightOnMS == 0) {
            if (motionDetected) {
                mHighlightOnMS = nowMs;
            }
        } else {
            long duration = nowMs - mHighlightOnMS;
            if (mHighlightOffMS == 0
                    && !motionDetected
                    && duration >= HIGHLIGHT_DURATION_ON_MS) {
                // mHighlightOnMS is > 0 ... motion was ON and stopped.
                mHighlightOffMS = nowMs;
            } else if (mHighlightOffMS > 0
                    && !motionDetected
                    && duration >= (HIGHLIGHT_DURATION_ON_MS + HIGHLIGHT_DURATION_OFF_MS)) {
                // mHighlightOnMS is > 0 and mHighlightOffMS is > 0.
                // Motion was ON and has stopped for at least the OFF duration.
                mHighlightOnMS = 0;
                mHighlightOffMS = 0;
                // TODO (move to controller) mAnalytics.sendEvent("Highlight", "cam" + mCamInfo.getIndex(), Long.toString(duration));
            }
        }
    }

}
