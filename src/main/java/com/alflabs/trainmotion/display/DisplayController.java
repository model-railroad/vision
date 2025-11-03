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
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.KVController;
import com.alflabs.trainmotion.util.ThreadLoop;
import com.alflabs.utils.IClock;
import com.alflabs.utils.RPair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final Analytics mAnalytics;
    private final ConfigIni mConfigIni;
    private final ConsoleTask mConsoleTask;
    private final KVController mKVController;
    private final KioskController mKioskController;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<LocalTime> mDailyTimeOff;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<LocalTime> mDailyTimeOn;
    private boolean mDisplayOn = true;
    private boolean mChanged = true;
    private boolean mInvertRequested = false;
    private String mDisplayScript;

    @Inject
    public DisplayController(
            IClock clock,
            ILogger logger,
            Analytics analytics,
            ConfigIni configIni,
            ConsoleTask consoleTask,
            KVController kvController,
            KioskController kioskController) {
        mClock = clock;
        mLogger = logger;
        mAnalytics = analytics;
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
        mDisplayScript = mConfigIni.getDisplayScript();

        invokeScript("start");

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
        invokeScript("stop");
        mLogger.log(TAG, "Stop");
        super.stop();
        mLogger.log(TAG, "Stopped");
    }

    /**
     * This toggles the *inversion* of the state on-off.
     *
     * Since the state is recomputed at each loop, what the "o" key does is truely
     * _invert_ the result of the state check.
     */
    public void onInvertDisplayKey() {
        mChanged = true;
        mInvertRequested = !mInvertRequested;
    }

    @Override
    protected void _runInThreadLoop() throws EndLoopException {
        LocalTime localTime = Instant
                        .ofEpochMilli(mClock.elapsedRealtime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime();

        // Perform the time-range check if enabled in the config.
        if (mDailyTimeOff.isPresent() && mDailyTimeOn.isPresent()) {
            boolean timeOn = localTime.isAfter(mDailyTimeOn.get())
                    && localTime.isBefore(mDailyTimeOff.get());
            if (timeOn != mDisplayOn) {
                mChanged = true;
                mDisplayOn = timeOn;
            }
        }

        // Perform the KV connection check if enabled in the config.
        if (mKVController.isEnabled()) {
            boolean isKVon = mKVController.isConnected();
            if (isKVon != mDisplayOn) {
                mChanged = true;
                mDisplayOn = isKVon;
            }
        }

        if (mChanged) {
            boolean displayOn = mDisplayOn;
            // Invert state when "o" console key is used.
            if (mInvertRequested) {
                displayOn = !displayOn;
            }
            mLogger.log(TAG, "State mChanged to " + displayOn + " at " + localTime);
            mConsoleTask.updateLineInfo(/* F */ "9d",
                    new StringInfo(" | " + (displayOn ? "ON" : "OFF"),
                            displayOn ? StringInfo.Flag.On : StringInfo.Flag.Default) );
            invokeScript(displayOn ? "on" : "off");
            mKioskController.onDisplayOnChanged(displayOn);
            mChanged = false;
        }

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Idle loop interrupted: " + e);
        }
    }

    /**
     * Invokes the script.
     * This is executed in the Display Controller thread loop.
     * The script commands are expected to be short, and the display state is not expected
     * to change very often, so we're fine with a synchronous exec for now.
     * We give the command up to ~5 seconds to execute.
     * In any case, we're not blocking any of the train-motion detection.
     */
    private void invokeScript(String state) {
        try {
            mAnalytics.sendEvent("Display", state);

            if (mDisplayScript.isEmpty()) {
                return;
            }

            RPair<String, String> shell = getShell();
            List<String> args = Stream
                    .of(shell.first, shell.second, mDisplayScript, state)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            mLogger.log(TAG, "Exec display script: " + args);
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.inheritIO();
            Process p = pb.start();
            boolean closed = p.waitFor(5, TimeUnit.SECONDS);
            mLogger.log(TAG, "Exec display script terminated: " + closed);

        } catch (Exception e) {
            mLogger.log(TAG, "Script exec failed: " + e);
        }
    }

    private RPair<String, String> getShell() {
        String shell = System.getenv("SHELL");
        if (shell != null) return RPair.create(shell, "");

        shell = System.getenv("ComSpec");
        if (shell != null) return RPair.create(shell, "/c");

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            return RPair.create("cmd.exe", "/c");
        } else {
            return RPair.create("sh", "");
        }
    }
}
