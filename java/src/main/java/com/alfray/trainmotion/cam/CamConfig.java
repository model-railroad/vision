package com.alfray.trainmotion.cam;

import javax.annotation.Nonnull;

public class CamConfig {
    private final String mInputUrl;
    private final double mMotionThreshold;

    public CamConfig(@Nonnull String inputUrl, double motionThreshold) {
        mInputUrl = inputUrl;
        mMotionThreshold = motionThreshold;
    }

    @Nonnull
    public String getInputUrl() {
        return mInputUrl;
    }

    public double getMotionThreshold() {
        return mMotionThreshold;
    }
}
