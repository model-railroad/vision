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

package com.alfray.trainmotion.util;

import com.alfray.libutils.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

@AutoFactory
public class FpsMeasurer {
    private final IClock mClock;
    private long mLastMs;
    private double mFps;
    private long mLoopMs;

    FpsMeasurer(@Provided IClock clock) {
        mClock = clock;
    }

    public void reset() {
        mLastMs = 0;
    }

    public void startTick() {
        long now = mClock.elapsedRealtime();

        if (mLastMs > 0) {
            long deltaMs = now - mLastMs;
            mFps = 1000.0 / deltaMs;
        }

        mLastMs = now;
    }

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

    public double getFps() {
        return mFps;
    }

    public long getLoopMs() {
        return mLoopMs;
    }

    public void setFrameRate(double frameRate) {
        mLoopMs = frameRate <= 0 ? 0 : (long)(1000.0 / frameRate);
    }
}
