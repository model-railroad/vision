package com.alfray.camproxy.cam;

import com.alfray.camproxy.CamProxy;
import com.alfray.camproxy.util.ILogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class CamerasProcessor {
    private static final String TAG = CamerasProcessor.class.getSimpleName();

    private final List<CamConfig> mCamConfigs = new ArrayList<>();
    private final ILogger mLogger;

    @Inject
    public CamerasProcessor(ILogger logger) {
        mLogger = logger;
    }

    public void add(CamConfig camConfig) {
        mCamConfigs.add(camConfig);
        mLogger.log(TAG, "Add: " + camConfig.getInputUrl());
    }
}
