package com.alfray.camproxy;

import com.alfray.camproxy.cam.CamConfig;
import com.alfray.camproxy.cam.Cameras;
import com.alfray.camproxy.dagger.DaggerICamProxyComponent;
import com.alfray.camproxy.dagger.ICamProxyComponent;
import com.alfray.camproxy.util.ILogger;

import javax.inject.Inject;
import java.util.Scanner;

public class CamProxy {
    private static final String TAG = CamProxy.class.getSimpleName();

    private final ICamProxyComponent mComponent;

    @Inject ILogger mLogger;
    @Inject CommandLineArgs mCommandLineArgs;
    @Inject Cameras mCameras;

    public CamProxy() {
        mComponent = DaggerICamProxyComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run(String[] args) {
        mLogger.log(TAG, "Start");

        mCommandLineArgs.parse(args);

        mCameras.add(new CamConfig(
                mCommandLineArgs.resolve("http://$U:$P1@192.168.1.86:554/ipcam_h264.sdp"),
                1024));

        mCameras.start();

        try {
            waitForEnter();
        } finally {
            mCameras.stop();
        }


        mLogger.log(TAG, "End");
    }

    private void waitForEnter() {
        Scanner input = new Scanner(System.in);
        mLogger.log(TAG, "Press Enter to quit...");
        input.nextLine();
    }
}
