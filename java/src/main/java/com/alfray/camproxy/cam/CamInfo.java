package com.alfray.camproxy.cam;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import javax.annotation.Nonnull;

@AutoFactory
public class CamInfo {
    private final int mIndex;
    private final CamConfig mConfig;
    private final CamInputGrabber mGrabber;
    private final CamOutputGenerator mGenerator;

    public CamInfo(
            @Provided CamInputGrabberFactory camInputGrabberFactory,
            @Provided CamOutputGeneratorFactory camOutputGeneratorFactory,
            int index,
            @Nonnull CamConfig config) {
        mIndex = index;
        mConfig = config;
        mGrabber = camInputGrabberFactory.create(this);
        mGenerator = camOutputGeneratorFactory.create(this);
    }

    public int getIndex() {
        return mIndex;
    }

    public CamConfig getConfig() {
        return mConfig;
    }

    public CamInputGrabber getGrabber() {
        return mGrabber;
    }

    public CamOutputGenerator getGenerator() {
        return mGenerator;
    }
}
