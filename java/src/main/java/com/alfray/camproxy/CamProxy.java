package com.alfray.camproxy;

import javax.inject.Inject;

public class CamProxy {
    private static final String TAG = CamProxy.class.getSimpleName();

    private final ICamProxyComponent mComponent;

    @Inject Logger mLogger;

    public CamProxy() {
        mComponent = DaggerICamProxyComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run() {
        mLogger.log(TAG, "Start");

        mLogger.log(TAG, "End");
    }
}
