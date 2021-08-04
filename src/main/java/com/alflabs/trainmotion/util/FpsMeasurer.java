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

package com.alflabs.trainmotion.util;

import com.alflabs.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

/**
 * Utility class that can be dropped in an FPS-controlled loop.
 * <p/>
 * Given a specific target framerate, this waits at the end of the loop to reach the desired
 * frame rate (if there's any time left). It also computes the actual FPS achieved.
 */
@AutoFactory
public class FpsMeasurer {
    private final IClock mClock;
    private long mLastMs;
    private double mFps;
    private long mLoopMs;

    FpsMeasurer(@Provided IClock clock) {
        mClock = clock;
    }

    /** Sets the desired framerate, to determine how much to wait in {@link #endWait()}. */
    public void setFrameRate(double frameRate) {
        mLoopMs = frameRate <= 0 ? 0 : (long)(1000.0 / frameRate);
    }

    /** Returns the loop duration in milliseconds needed to achieve the target framerate. */
    public long getLoopMs() {
        return mLoopMs;
    }

    /** Returns the actual FPS achieved. Updated by the {@link #startTick()} call. */
    public double getFps() {
        return mFps;
    }

    /**
     * Called at the very beginning of a loop iteration.
     * <p/>
     * This computes the time spent in the <em>last</em> iteration and updates {@link #getFps()}
     * with the actual FPS achieved in the last iteration.
     */
    public void startTick() {
        long now = mClock.elapsedRealtime();

        if (mLastMs > 0) {
            long deltaMs = now - mLastMs;
            mFps = 1000.0 / deltaMs;
        }

        mLastMs = now;
    }

    /**
     * Called at the very end of a loop iteration.
     * <p/>
     * If there's any time left, pauses to achieve the desired frame rate.
     *
     * @return The number of milliseconds paused waiting. Can be negative.
     */
    public long endWait() {
        if (mLastMs <= 0) {
            return 0;
        }
        long deltaMs = mClock.elapsedRealtime() - mLastMs;
        deltaMs = mLoopMs - deltaMs;
        if (deltaMs > 0) {
            mClock.sleep(deltaMs);
        }
        return deltaMs;
    }

    /** Reset the internal state used to compute actual FPS, if this object is reused. */
    public void reset() {
        mLastMs = 0;
    }
}
