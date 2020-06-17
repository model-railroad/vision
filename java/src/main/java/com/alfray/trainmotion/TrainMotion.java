package com.alfray.trainmotion;

import com.alfray.trainmotion.cam.CamConfig;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.cam.HttpServ;
import com.alfray.trainmotion.dagger.DaggerITrainMotionComponent;
import com.alfray.trainmotion.dagger.ITrainMotionComponent;
import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;

import javax.inject.Inject;

public class TrainMotion {
    private static final String TAG = TrainMotion.class.getSimpleName();

    private final ITrainMotionComponent mComponent;

    @Inject CommandLineArgs mCommandLineArgs;
    @Inject DebugDisplay mDebugDisplay;
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

        // TODO this is intended to be pulled off some configuration file or command line args.
        mCameras.add(new CamConfig(
                mCommandLineArgs.resolve("rtsp://$U:$P11$P2@192.168.$P3.85:554/ipcam_h264.sdp"),
                0.3));
        mCameras.add(new CamConfig(
                mCommandLineArgs.resolve("rtsp://$U:$P12$P2@192.168.$P3.86:554/ipcam_h264.sdp"),
                0.3));
        mCameras.add(new CamConfig(
                mCommandLineArgs.resolve("rtsp://$U:$P13$P2@192.168.$P3.87:554/ipcam_h264.sdp"),
                0.3));

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
