/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alflabs.trainmotion;

import com.alflabs.trainmotion.cam.CamConfig;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.cam.HttpServ;
import com.alflabs.trainmotion.dagger.DaggerITrainMotionComponent;
import com.alflabs.trainmotion.dagger.ITrainMotionComponent;
import com.alflabs.trainmotion.display.ConsoleTask;
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;
import com.alflabs.trainmotion.display.KioskDisplay;
import com.alflabs.utils.FileOps;

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
            mAnalytics.sendEvent("Start", "");
            mConsoleTask.consoleWait();
            mAnalytics.sendEvent("Stop", "");
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
