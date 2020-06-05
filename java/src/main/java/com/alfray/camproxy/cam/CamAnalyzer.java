package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.Frame;

import javax.annotation.Nullable;

/**
 */
@AutoFactory
public class CamAnalyzer extends ThreadLoop {
    private final String TAG;

    private final ILogger mLogger;
    private final CamInfo mCamInfo;
    private Frame mLastFrame;
    private double mFrameRate;

    public CamAnalyzer(
            @Provided ILogger logger,
            CamInfo camInfo) {
        TAG = "CamAn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
    }

    @Nullable
    public Frame getLastFrame() {
        return mLastFrame;
    }

    @Override
    public void start() throws Exception {
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

        while (!mQuit) {
            try {
                Thread.sleep(1000 / 4); // 4 fps
            } catch (InterruptedException e) {
                mLogger.log(TAG, e.toString());
            }
        }
    }
}
