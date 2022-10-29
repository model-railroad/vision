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
    private ConsoleTask mConsoleTask;
    private KioskController mKioskController;
    private Optional<LocalTime> mDailyTimeOff;
    private Optional<LocalTime> mDailyTimeOn;
    private boolean mDisplayOn = true;

    @Inject
    public DisplayController(
            IClock clock,
            ILogger logger,
            ConfigIni configIni,
            ConsoleTask consoleTask,
            KioskController kioskController) {
        mClock = clock;
        mLogger = logger;
        mConfigIni = configIni;
        mConsoleTask = consoleTask;
        mKioskController = kioskController;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");
        mDailyTimeOff = mConfigIni.getDisplayOffTime();
        mDailyTimeOn = mConfigIni.getDisplayOnTime();

        if (!mDailyTimeOff.isPresent() || !mDailyTimeOn.isPresent()) {
            mLogger.log(TAG, "Missing daily time on/off; will not control display.");
            return;
        }

        if (!mDailyTimeOn.get().isBefore(mDailyTimeOff.get())) {
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

        boolean displayOn = localTime.isAfter(mDailyTimeOn.get())
                && localTime.isBefore(mDailyTimeOff.get());
        if (displayOn != mDisplayOn) {
            mDisplayOn = displayOn;
            mConsoleTask.updateLineInfo(/* F */ "9d", " | " + (displayOn ? "ON" : "OFF") );
            mLogger.log(TAG, "State changed to " + displayOn + " at " + localTime);
            mKioskController.onDisplayOnChanged(displayOn);
        }

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Idle loop interrupted: " + e);
        }
    }

}
