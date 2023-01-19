/*
 * Project: Train-Motion
 * Copyright (C) 2022 alf.labs gmail com,
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

package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.ConfigIni;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.KVController;
import com.alflabs.trainmotion.util.ThreadLoop;
import com.alflabs.utils.IClock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Controls whether the display should be turned on/off.
 */
@Singleton
public class DisplayController extends ThreadLoop {
    private static final String TAG = DisplayController.class.getSimpleName();
    private static final int FPS = 5;
    private static final long IDLE_SLEEP_MS = 1000 / FPS;

    private final IClock mClock;
    private final ILogger mLogger;
    private final ConfigIni mConfigIni;
    private final ConsoleTask mConsoleTask;
    private final KVController mKVController;
    private final KioskController mKioskController;
    private Optional<LocalTime> mDailyTimeOff;
    private Optional<LocalTime> mDailyTimeOn;
    private boolean mDisplayOn = true;
    private boolean mChanged = true;

    @Inject
    public DisplayController(
            IClock clock,
            ILogger logger,
            ConfigIni configIni,
            ConsoleTask consoleTask,
            KVController kvController,
            KioskController kioskController) {
        mClock = clock;
        mLogger = logger;
        mConfigIni = configIni;
        mConsoleTask = consoleTask;
        mKVController = kvController;
        mKioskController = kioskController;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");
        mDailyTimeOff = mConfigIni.getDisplayOffTime();
        mDailyTimeOn = mConfigIni.getDisplayOnTime();

        if (!mDailyTimeOff.isPresent() || !mDailyTimeOn.isPresent()) {
            // This does not abort this loop since we're also checking the KVController.
            mLogger.log(TAG, "Missing daily time on/off; will not control display.");
        } else if (!mDailyTimeOn.get().isBefore(mDailyTimeOff.get())) {
            mLogger.log(TAG, "Daily time ON must be before time OFF; will not control display.");
            return;
        }

        super.start("Thread-DisplayOnOff");
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        super.stop();
        mLogger.log(TAG, "Stopped");
    }

    @Override
    protected void _runInThreadLoop() throws EndLoopException {
        LocalTime localTime = Instant
                        .ofEpochMilli(mClock.elapsedRealtime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime();

        if (mDailyTimeOff.isPresent() && mDailyTimeOn.isPresent()) {
            boolean timeOn = localTime.isAfter(mDailyTimeOn.get())
                    && localTime.isBefore(mDailyTimeOff.get());
            if (timeOn != mDisplayOn) {
                mChanged = true;
                mDisplayOn = timeOn;
            }
        }
        boolean isKVon = mKVController.isKVConnected();
        if (isKVon != mDisplayOn) {
            mChanged = true;
            mDisplayOn = isKVon;
        }

        if (mChanged) {
            mLogger.log(TAG, "State mChanged to " + mDisplayOn + " at " + localTime);
            mConsoleTask.updateLineInfo(/* F */ "9d", " | " + (mDisplayOn ? "ON" : "OFF") );
            mKioskController.onDisplayOnChanged(mDisplayOn);
            mChanged = false;
        }

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Idle loop interrupted: " + e);
        }
    }

}
