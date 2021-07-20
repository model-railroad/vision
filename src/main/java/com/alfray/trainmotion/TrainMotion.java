package com.alfray.trainmotion;

import com.alfray.trainmotion.cam.CamConfig;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.cam.HttpServ;
import com.alfray.trainmotion.dagger.DaggerITrainMotionComponent;
import com.alfray.trainmotion.dagger.ITrainMotionComponent;
import com.alfray.trainmotion.display.ConsoleTask;
import com.alfray.trainmotion.util.Analytics;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;
import com.alfray.trainmotion.display.KioskDisplay;

import javax.inject.Inject;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public class TrainMotion {
    private static final String TAG = TrainMotion.class.getSimpleName();
    private static final double MOTION_THRESHOLD = 0.3;

    private final ITrainMotionComponent mComponent;

    @Inject ConfigIni mConfigIniReader;
    @Inject CommandLineArgs mCommandLineArgs;
    @Inject ConsoleTask mConsoleTask;
    @Inject KioskDisplay mKioskDisplay;
    @Inject Playlist mPlaylist;
    @Inject Analytics mAnalytics;
    @Inject ILogger mLogger;
    @Inject Cameras mCameras;
    @Inject HttpServ mHttpServ;

    public TrainMotion() {
        mComponent = DaggerITrainMotionComponent.factory().createComponent();
        mComponent.inject(this);
    }

    public void run(String[] args) {
        mLogger.log(TAG, "Start");

        mCommandLineArgs.initialize(args);

        //noinspection ConstantConditions
        mConfigIniReader.initialize(new File(mCommandLineArgs.getStringOption(
                                CommandLineArgs.OPT_CONFIG_INI,
                                ConfigIni.DEFAULT_CONFIG_INI)));


        addCamera(1);
        addCamera(2);
        addCamera(3);

        if (mCameras.count() < 1) {
            mLogger.log(TAG, "ERROR: No camera URLs found in " + mConfigIniReader.getFile());
            System.exit(1);
        }

        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                super.run();
                mLogger.log(TAG, "Shutdown Hook");
                mConsoleTask.requestQuit();
                try {
                    mLogger.log(TAG, "Shutdown Hook await");
                    shutdownLatch.await();
                } catch (InterruptedException e) {
                    mLogger.log(TAG, e.toString());
                }
            }
        });

        try {
            mAnalytics.setAnalyticsId(mConfigIniReader.getAnalyticsId());
            mAnalytics.start();
            //noinspection ConstantConditions
            mPlaylist.initialize(
                    mCommandLineArgs.getStringOption(CommandLineArgs.OPT_MEDIA_DIR,
                            mConfigIniReader.getPlaylistDir()) );
            mConsoleTask.start();
            mKioskDisplay.start();
            mHttpServ.start();
            mCameras.start();
            mKioskDisplay.initialize();
            mConsoleTask.consoleWait();
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        } finally {
            safeStop(mCameras);
            safeStop(mHttpServ);
            safeStop(mKioskDisplay);
            safeStop(mConsoleTask);
            safeStop(mAnalytics);
        }

        mLogger.log(TAG, "Shutdown Hook release");
        shutdownLatch.countDown();
        mLogger.log(TAG, "End");
    }

    private void addCamera(int index) {
        Optional<String> camProp = mConfigIniReader.getCamN(index);
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
