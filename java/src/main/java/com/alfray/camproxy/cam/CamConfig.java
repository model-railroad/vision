package com.alfray.camproxy.cam;

import javax.annotation.Nonnull;

public class CamConfig {
    private final String mInputUrl;
    private final int mOutputPort;

    public CamConfig(@Nonnull String inputUrl, @Deprecated int outputPort) {
        mInputUrl = inputUrl;
        mOutputPort = outputPort;
    }

    @Nonnull
    public String getInputUrl() {
        return mInputUrl;
    }

    @Deprecated
    public int getOutputPort() {
        return mOutputPort;
    }
}
