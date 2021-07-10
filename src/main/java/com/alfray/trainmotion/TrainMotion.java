package com.alfray.trainmotion;

import com.alfray.trainmotion.cam.CamConfig;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.cam.HttpServ;
import com.alfray.trainmotion.dagger.DaggerITrainMotionComponent;
import com.alfray.trainmotion.dagger.ITrainMotionComponent;
import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;
import com.alfray.trainmotion.util.KioskDisplay;

import javax.inject.Inject;
import java.io.File;
import java.util.Optional;

public class TrainMotion {
    private static final String TAG = TrainMotion.class.getSimpleName();
    private static final double MOTION_THRESHOLD = 0.3;

    private final ITrainMotionComponent mComponent;

    @Inject IniFileReader mIniFileReader;
    @Inject CommandLineArgs mCommandLineArgs;
    @Inject DebugDisplay mDebugDisplay;
    @Inject KioskDisplay mKioskDisplay;
    @Inject ILogger mLogger;
    @Inject Cameras mCameras;
    @Inject HttpServ mHttpServ;

    public TrainMotion() {
        mComponent = DaggerITrainMotionComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run(String[] args) {
        mLogger.log(TAG, "Start");

        mCommandLineArgs.parse(args);

        //noinspection ConstantConditions
        mIniFileReader.readFile(new File(mCommandLineArgs.getStringOption(
                                CommandLineArgs.OPT_CONFIG_INI,
                                IniFileReader.DEFAULT_CONFIG_INI)));

        addCamera(1);
        addCamera(2);
        addCamera(3);

        if (mCameras.count() < 1) {
            mLogger.log(TAG, "ERROR: No camera URLs found in " + mIniFileReader.getFile());
            System.exit(1);
        }

        try {
            mDebugDisplay.start();
            mKioskDisplay.start();
            mHttpServ.start();
            mCameras.start();
            mKioskDisplay.loadPage();
            mDebugDisplay.consoleWait();
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        } finally {
            safeStop(mCameras);
            safeStop(mHttpServ);
            safeStop(mKioskDisplay);
            safeStop(mDebugDisplay);
        }

        mLogger.log(TAG, "End");
    }

    private void addCamera(int index) {
        Optional<String> camProp = mIniFileReader.getCamN(index);
        if (camProp.isPresent()) {
            String camUrl = mCommandLineArgs.resolve(camProp.get());
            mCameras.add(new CamConfig(camUrl, MOTION_THRESHOLD));
            mLogger.log(TAG, "Added camera " + index);
        }
    }

    private void safeStop(IStartStop stoppable) {
        try {
            stoppable.stop();
        } catch (Exception e) {
            mLogger.log(TAG, "Error stopping " + stoppable.getClass().getSimpleName() + ": " + e.toString());
        }
    }
}
