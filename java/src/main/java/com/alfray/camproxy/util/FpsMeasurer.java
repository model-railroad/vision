package com.alfray.camproxy.util;

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
