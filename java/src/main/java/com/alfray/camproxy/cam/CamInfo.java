package com.alfray.camproxy.cam;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import javax.annotation.Nonnull;

@AutoFactory
public class CamInfo {
    private final int mIndex;
    private final CamConfig mConfig;
    private final CamInputGrabber mGrabber;

    public CamInfo(
            @Provided CamInputGrabberFactory camInputGrabberFactory,
            int index,
            @Nonnull CamConfig config) {
        mIndex = index;
        mConfig = config;
        mGrabber = camInputGrabberFactory.create(this);
    }

    public int getIndex() {
        return mIndex;
    }

    @Nonnull
    public CamConfig getConfig() {
        return mConfig;
    }

    @Nonnull
    public CamInputGrabber getGrabber() {
        return mGrabber;
    }
}
