package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.ILogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class Cameras {
    private static final String TAG = Cameras.class.getSimpleName();

    private final List<CamInfo> mCamInfos = new ArrayList<>();
    private final CamInfoFactory mCamInfoFactory;
    private final ILogger mLogger;

    @Inject
    public Cameras(
            CamInfoFactory camInfoFactory,
            ILogger logger) {
        mCamInfoFactory = camInfoFactory;
        mLogger = logger;
    }

    public void add(@Nonnull CamConfig camConfig) {
        CamInfo info = mCamInfoFactory.create(1 + mCamInfos.size(), camConfig);
        mCamInfos.add(info);
        mLogger.log(TAG, "Add Cam #" + info.getIndex() + " for " + info.getConfig().getInputUrl());
    }

    @Nullable
    public CamInfo getByIndex(int index) {
        for (CamInfo camInfo : mCamInfos) {
            if (camInfo.getIndex() == index) {
                return camInfo;
            }
        }
        return null;
    }

    public void start() throws Exception {
        for (CamInfo camInfo : mCamInfos) {
            camInfo.getGrabber().start();
            camInfo.getGenerator().start();
        }
    }

    public void stop() {
        for (CamInfo camInfo : mCamInfos) {
            try {
                camInfo.getGenerator().stop();
            } catch (InterruptedException e) {
                mLogger.log(TAG, "Stopping gen-" + camInfo.getIndex() + ": " + e);
            }
            try {
                camInfo.getGrabber().stop();
            } catch (InterruptedException e) {
                mLogger.log(TAG, "Stopping grab-" + camInfo.getIndex() + ": " + e);
            }
        }
    }
}
