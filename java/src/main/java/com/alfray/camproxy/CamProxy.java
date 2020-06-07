package com.alfray.camproxy;

import com.alfray.camproxy.cam.CamConfig;
import com.alfray.camproxy.cam.Cameras;
import com.alfray.camproxy.cam.HttpServ;
import com.alfray.camproxy.dagger.DaggerICamProxyComponent;
import com.alfray.camproxy.dagger.ICamProxyComponent;
import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.IStartStop;
import org.bytedeco.javacpp.Loader;

import javax.inject.Inject;

public class CamProxy {
    private static final String TAG = CamProxy.class.getSimpleName();

    private final ICamProxyComponent mComponent;

    @Inject CommandLineArgs mCommandLineArgs;
    @Inject DebugDisplay mDebugDisplay;
    @Inject ILogger mLogger;
    @Inject Cameras mCameras;
    @Inject HttpServ mHttpServ;

    public CamProxy() {
        mComponent = DaggerICamProxyComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run(String[] args) {
        mLogger.log(TAG, "Start");

        mCommandLineArgs.parse(args);

        mCameras.add(new CamConfig(
                // mCommandLineArgs.resolve("rtsp://$U:$P1@192.168.1.86:554/ipcam_mjpeg.sdp"),
                mCommandLineArgs.resolve("rtsp://$U:$P1@192.168.3.86:554/ipcam_h264.sdp"),
                8000));

        try {
            mDebugDisplay.start();
            mHttpServ.start();
            mCameras.start();
            mDebugDisplay.consoleWait();
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        } finally {
            safeStop(mCameras);
            safeStop(mHttpServ);
            safeStop(mDebugDisplay);
        }

        mLogger.log(TAG, "End");
    }

    private void safeStop(IStartStop stoppable) {
        try {
            stoppable.stop();
        } catch (Exception e) {
            mLogger.log(TAG, "Error stopping " + stoppable.getClass().getSimpleName() + ": " + e.toString());
        }
    }
}
