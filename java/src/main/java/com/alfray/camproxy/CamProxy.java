package com.alfray.camproxy;

import com.alfray.camproxy.cam.CamConfig;
import com.alfray.camproxy.cam.CamerasProcessor;
import com.alfray.camproxy.dagger.DaggerICamProxyComponent;
import com.alfray.camproxy.dagger.ICamProxyComponent;
import com.alfray.camproxy.util.ILogger;

import javax.inject.Inject;

public class CamProxy {
    private static final String TAG = CamProxy.class.getSimpleName();

    private final ICamProxyComponent mComponent;

    @Inject ILogger mLogger;
    @Inject CommandLineArgs mCommandLineArgs;
    @Inject CamerasProcessor mCamerasProcessor;

    public CamProxy() {
        mComponent = DaggerICamProxyComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run(String[] args) {
        mLogger.log(TAG, "Start");

        mCommandLineArgs.parse(args);

        mCamerasProcessor.add(new CamConfig(
                mCommandLineArgs.resolve("http://$U:$P1@192.168.1.86:554/ipcam_h264.sdp"),
                1024));

        mLogger.log(TAG, "End");
    }
}
