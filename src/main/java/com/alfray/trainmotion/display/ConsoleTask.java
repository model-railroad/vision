package com.alfray.trainmotion.display;

import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;

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

    private final ILogger mLogger;
    @GuardedBy("mLineInfo")
    private final Map<String, String> mLineInfo = new TreeMap<>();

    private boolean mQuit;

    @Inject
    public ConsoleTask(ILogger logger) {
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
                long startMs = System.currentTimeMillis();
                displayLineInfo();

                // This is definitely... underwhelming but works well enough in a terminal.
                if (reader.ready()) {
                    char c = (char) reader.read();
                    processKey(c);
                }

                long deltaMs = System.currentTimeMillis() - startMs;
                deltaMs = sleepMs - deltaMs;
                if (deltaMs > 0) {
                    try {
                        Thread.sleep(deltaMs);
                    } catch (InterruptedException e) {
                        mLogger.log(TAG, e.toString());
                    }
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
