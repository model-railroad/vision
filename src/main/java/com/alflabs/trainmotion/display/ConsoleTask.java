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

package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;
import com.alflabs.trainmotion.util.StatsCollector;
import com.alflabs.utils.IClock;
import dagger.Lazy;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

@Singleton
public class ConsoleTask implements IStartStop {
    private static final String TAG = ConsoleTask.class.getSimpleName();

    // The console does not need to update at the full input/output feed fps.
    private static final int CONSOLE_FPS = 2;

    private final IClock mClock;
    private final ILogger mLogger;
    private final Lazy<DisplayController> mDisplayController;
    private final Lazy<StatsCollector> mStatsCollector;
    private final Lazy<KioskController> mKioskController;
    @GuardedBy("mLineInfo")
    private final SortedMap<String, StringInfo> mLineInfo = new TreeMap<>();
    private final SortedMap<String, StringInfo> mLineInfoRO =
            Collections.unmodifiableSortedMap(mLineInfo);

    private boolean mQuit;

    @Inject
    public ConsoleTask(
            IClock clock,
            ILogger logger,
            Lazy<DisplayController> displayController,
            Lazy<StatsCollector> statsCollector,
            Lazy<KioskController> kioskController) {
        mClock = clock;
        mLogger = logger;
        mDisplayController = displayController;
        mStatsCollector = statsCollector;
        mKioskController = kioskController;
        mQuit = false;
    }

    @Override
    public void start() {
    }

    public void requestQuit() {
        mLogger.log(TAG, "\nQuit Requested");
        SwingUtilities.invokeLater(() -> mQuit = true);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isQuitRequested() {
        return mQuit;
    }

    @Override
    public void stop() {
    }

    public void updateLineInfo(String key, @Nonnull StringInfo msg) {
        synchronized (mLineInfo) {
            mLineInfo.put(key, msg);
        }
    }

    private final StringBuffer _sTempBuf = new StringBuffer();
    public String computeLineInfo() {
        _sTempBuf.setLength(0);
        synchronized (mLineInfo) {
            for (StringInfo info : mLineInfo.values()) {
                _sTempBuf.append(info.mMsg);
            }
        }
        _sTempBuf.append('\r');
        return _sTempBuf.toString();
    }

    public SortedMap<String, StringInfo> getLineInfos() {
        return mLineInfoRO;
    }

    public void displayLineInfo() {
        mLogger.log(computeLineInfo());
    }

    public void consoleWait() {
        mLogger.log(TAG, "Start loop");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        mLogger.log(TAG, "Press q+enter to quit, ?+enter for more options");

        final long sleepMs = 1000 / CONSOLE_FPS;

        try {
            while (!mQuit) {
                long startMs = mClock.elapsedRealtime();
                displayLineInfo();

                // This is definitely... underwhelming but works well enough in a terminal.
                if (reader.ready()) {
                    char c = (char) reader.read();
                    processKey(c);
                }

                long deltaMs = mClock.elapsedRealtime() - startMs;
                deltaMs = sleepMs - deltaMs;
                if (deltaMs > 0) {
                    mClock.sleep(deltaMs);
                }
            }
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        }

        mLogger.log(TAG, "");
        mLogger.log(TAG, "End loop");
    }

    public boolean processKey(char c) {
        // mLogger.log(TAG, "Process key: " + c); // DEBUG
        // Keys handled by the ConsoleTask: esc, q=quit // ?, h=help, o=display off.
        // Keys handled by KioskController: f=fullscreen, s=sound, u=shuffle, n=next, m=mask.
        switch (c) {
        case '?':
        case 'h':
            mLogger.log(TAG, "Keys: ?/h=help, esc/q=quit, u=shuffle, s=sound, m=mask, o=off");
            return true;
        case 'o':
            mDisplayController.get().onInvertDisplayKey();
            return true;
        case 27:
        case 'q':
            requestQuit();
            return true;
        case KeyEvent.VK_ENTER:
        case KeyEvent.CHAR_UNDEFINED:
            // ignore silently
            return false; // not consumed
        default:
            return mKioskController.get().processKey(c);
        }
    }
}
