package com.alfray.camproxy;

import com.alfray.camproxy.cam.CamConfig;
import com.alfray.camproxy.cam.Cameras;
import com.alfray.camproxy.dagger.DaggerICamProxyComponent;
import com.alfray.camproxy.dagger.ICamProxyComponent;
import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.ILogger;

import javax.inject.Inject;
import java.util.Scanner;

public class CamProxy {
    private static final String TAG = CamProxy.class.getSimpleName();

    private final ICamProxyComponent mComponent;

    @Inject CommandLineArgs mCommandLineArgs;
    @Inject DebugDisplay mDebugDisplay;
    @Inject ILogger mLogger;
    @Inject Cameras mCameras;

    public CamProxy() {
        mComponent = DaggerICamProxyComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run(String[] args) {
        mLogger.log(TAG, "Start");

        mCommandLineArgs.parse(args);

        mCameras.add(new CamConfig(
                // mCommandLineArgs.resolve("rtsp://$U:$P1@192.168.1.86:554/ipcam_mjpeg.sdp"),
                mCommandLineArgs.resolve("rtsp://$U:$P1@192.168.1.86:554/ipcam_h264.sdp"),
                8000));

        try {
            mDebugDisplay.start();
            mCameras.start();

            if (mCommandLineArgs.hasOption(CommandLineArgs.OPT_DEBUG_DISPLAY)) {
                mDebugDisplay.waitTillClosed();
            } else {
                waitForEnter();
            }
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        } finally {
            mCameras.stop();
            mDebugDisplay.stop();
        }

        mLogger.log(TAG, "End");
    }

    private void waitForEnter() {
        Scanner input = new Scanner(System.in);
        mLogger.log(TAG, "Press Enter to quit...");
        input.nextLine();
    }
}
