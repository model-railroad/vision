package com.alfray.camproxy.util;

import javax.inject.Inject;

public class FpsMeasurer {
    private long mLastMs;
    private double mFps;
    private long mMatchMs;

    @Inject
    public FpsMeasurer() {}

    public void reset() {
        mLastMs = 0;
    }

    public boolean tick() {
        long now = System.currentTimeMillis();

        if (mLastMs > 0) {
            long deltaMs = now - mLastMs;
            if (mMatchMs > 0 && deltaMs < mMatchMs) {
                // not ready for next frame yet
                return false;
            }
            // ready for next frame
            if (deltaMs > 0) {
                mFps = 1000.0 / deltaMs;
            }
        }

        mLastMs = now;
        return true;
    }

    public double getFps() {
        return mFps;
    }

    public void setFrameRate(double frameRate) {
        mMatchMs = frameRate <= 0 ? 0 : (long)(1000.0 / frameRate);
    }
}
