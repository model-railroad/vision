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

import com.alflabs.trainmotion.cam.IMotionDetector;
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import javax.annotation.Nonnull;
import java.awt.Color;

@AutoFactory
public class Highlighter {
    // Highlight color
    static final Color HIGHLIGHT_LINE_COLOR = Color.YELLOW;
    // Highlight minimum display duration with video motion ON. Total with OFF is 5 seconds.
    static final long HIGHLIGHT_DURATION_ON_MS = 3000;
    // Highlight minimum display duration with video motion OFF after a ON event.
    static final long HIGHLIGHT_DURATION_OFF_MS = 2000;
    // Highlight stroke width
    static final int HIGHLIGHT_LINE_SIZE_MAX = 10;
    static final int HIGHLIGHT_LINE_SIZE_MIN = 3;

    private final IClock mClock;
    private final Analytics mAnalytics;
    private final int mCamIndex;
    private final IMotionDetector mMotionDetector;

    /** Show highlight if > 0. Indicates when highlight ON started. */
    private long mHighlightInitialOnMS;
    /** Show highlight if > 0. Indicates when highlight OFF started. */
    private long mHighlightOffMS;


    public Highlighter(
            @Provided IClock clock,
            @Provided Analytics analytics,
            int camIndex,
            @Nonnull IMotionDetector motionDetector) {
        mClock = clock;
        mAnalytics = analytics;
        mCamIndex = camIndex;
        mMotionDetector = motionDetector;
    }

    public boolean isHighlighted() {
        return mHighlightInitialOnMS > 0;
    }

    public void update() {
        long nowMs = mClock.elapsedRealtime();
        boolean motionDetected = mMotionDetector.isMotionDetected();
        if (mHighlightInitialOnMS == 0) {
            if (motionDetected) {
                mHighlightInitialOnMS = nowMs;
                mHighlightOffMS = 0;
            }
        } else {
            if (motionDetected) {
                mHighlightOffMS = 0;
            }

            long durationSinceLastOn = nowMs - mHighlightInitialOnMS;

            if (!motionDetected) {
                if (mHighlightOffMS == 0
                        && durationSinceLastOn >= HIGHLIGHT_DURATION_ON_MS) {
                    // mHighlightOnMS is > 0 ... motion was ON and stopped.
                    mHighlightOffMS = nowMs;
                } else if (mHighlightOffMS > 0
                        && nowMs - mHighlightOffMS >= HIGHLIGHT_DURATION_OFF_MS) {
                    // mHighlightOnMS is > 0 and mHighlightOffMS is > 0.
                    // Motion was ON and has stopped for at least the OFF duration.
                    mHighlightInitialOnMS = 0;
                    mHighlightOffMS = 0;
                    mAnalytics.sendEvent("Highlight", "cam" + mCamIndex,
                            Long.toString(durationSinceLastOn));
                }
            }
        }
    }

}
