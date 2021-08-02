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

import com.alflabs.libutils.utils.IClock;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;

@Singleton
public class ConsoleTask implements IStartStop {
    private static final String TAG = ConsoleTask.class.getSimpleName();

    // The console does not need to update at the full input/output feed fps.
    private static final int CONSOLE_FPS = 2;

    private final IClock mClock;
    private final ILogger mLogger;
    @GuardedBy("mLineInfo")
    private final Map<String, String> mLineInfo = new TreeMap<>();

    private boolean mQuit;

    @Inject
    public ConsoleTask(
            IClock clock,
            ILogger logger) {
        mClock = clock;
        mLogger = logger;
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

    public void updateLineInfo(String key, @Nonnull String msg) {
        synchronized (mLineInfo) {
            mLineInfo.put(key, msg);
        }
    }

    private final StringBuffer _sTempBuf = new StringBuffer();
    public String computeLineInfo() {
        _sTempBuf.setLength(0);
        synchronized (mLineInfo) {
            for (String info : mLineInfo.values()) {
                //if (_sTempBuf.length() > 0) {
                //    _sTempBuf.append(" || ");
                //}
                _sTempBuf.append(info);
            }
        }
        _sTempBuf.append('\r');
        return _sTempBuf.toString();
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
        switch (c) {
        case '?':
        case 'h':
            mLogger.log(TAG, "Keys: ?/h=help, esc/q=quit, s=toggle shuffle, m=toggle mute, k=toggle mask");
            return true;
        case 27:
        case 'q':
            requestQuit();
            return true;
        default:
            mLogger.log(TAG, "Key ignored: '" + c + "' int: " + (int)c);
        case KeyEvent.VK_ENTER:
        case KeyEvent.CHAR_UNDEFINED:
            // ignore silently
            return false; // not consumed
        }
    }
}
