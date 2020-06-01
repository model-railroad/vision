package com.alfray.camproxy.util;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FpsMeasurer {

    private long mLastMs;
    private double mFps;

    @Inject
    public FpsMeasurer() {}

    public void reset() {
        mLastMs = 0;
    }

    public void tick() {
        long now = System.currentTimeMillis();

        if (mLastMs > 0) {
            long deltaMs = now - mLastMs;
            if (deltaMs > 0) {
                mFps = 1000.0 / deltaMs;
            }
        }

        mLastMs = now;
    }

    public double getFps() {
        return mFps;
    }
}
