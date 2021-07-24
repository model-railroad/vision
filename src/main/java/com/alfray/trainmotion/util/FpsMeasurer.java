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

import javax.inject.Inject;

public class FpsMeasurer {
    private long mLastMs;
    private double mFps;
    private long mLoopMs;

    @Inject
    public FpsMeasurer() {}

    public void reset() {
        mLastMs = 0;
    }

    public void startTick() {
        long now = System.currentTimeMillis();

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
        long deltaMs = System.currentTimeMillis() - mLastMs;
        deltaMs = mLoopMs - deltaMs;
        if (deltaMs > 0) {
            try {
                Thread.sleep(deltaMs);
            } catch (InterruptedException ignore) {}
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
