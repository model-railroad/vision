package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.ILogger;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

@AutoFactory
public class CamInputGrabber {
    private final String TAG;
    private final ILogger mLogger;
    private final CamInfo mCamInfo;

    public CamInputGrabber(
            @Provided ILogger logger,
            CamInfo camInfo) {
        TAG = "CamIn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
    }

    public void start() {
        mLogger.log(TAG, "Start");
    }

    public void stop() {
        mLogger.log(TAG, "Stop");
    }
}
