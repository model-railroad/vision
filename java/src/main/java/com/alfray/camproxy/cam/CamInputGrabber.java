package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

@AutoFactory
public class CamInputGrabber extends ThreadLoop {
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

    @Override
    public void start() {
        mLogger.log(TAG, "Start");
        super.start();
    }

    @Override
    public void stop() throws InterruptedException {
        mLogger.log(TAG, "Stop");
        super.stop();
    }

    @Override
    protected void _runInThreadLoop() {
        mLogger.log(TAG, "Thread loop begin");

        try {
            Thread.sleep(3600*1000);
        } catch (InterruptedException e) {
            mLogger.log(TAG, "Interrupted");
        }
    }
}
