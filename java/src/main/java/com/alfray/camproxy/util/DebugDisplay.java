package com.alfray.camproxy.util;

import com.alfray.camproxy.CommandLineArgs;
import com.alfray.camproxy.cam.CamInfo;
import com.alfray.camproxy.cam.Cameras;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.TreeMap;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class DebugDisplay implements IStartStop {
    private static final String TAG = DebugDisplay.class.getSimpleName();

    // The display does not need to run at the full input/output feed fps.
    private static final int DEBUG_DISPLAY_FPS = 10;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final CommandLineArgs mCommandLineArgs;
    @GuardedBy("mLineInfo")
    private final Map<Integer, String> mLineInfo = new TreeMap<>();

    private boolean mQuit;
    private CanvasFrame mDisplay;
    private boolean mToggleMask;
    private int mCameraIndex = 1;

    @Inject
    public DebugDisplay(
            ILogger logger,
            Cameras cameras,
            CommandLineArgs commandLineArgs) {
        mLogger = logger;
        mCameras = cameras;
        mCommandLineArgs = commandLineArgs;
    }

    public void start() {
        mQuit = false;

        mDisplay = new CanvasFrame("Test video");
        mDisplay.setSize(1280, 720);

        mDisplay.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mDisplay.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                requestQuit();
            }
        });
        mDisplay.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (processKey(keyEvent.getKeyChar())) {
                    keyEvent.consume();
                }
                super.keyPressed(keyEvent);
            }
        });

        if (mCommandLineArgs.hasOption(CommandLineArgs.OPT_DEBUG_DISPLAY)) {
            // Start visible in --debug mode
            mDisplay.setVisible(true);
        }
    }

    public void requestQuit() {
        mLogger.log(TAG, "\nQuit Requested");
        mQuit = true;
    }

    public boolean quitRequested() {
        return mQuit;
    }

    public void stop() {
        if (mDisplay != null) {
            mDisplay.dispose();
            mDisplay = null;
        }
    }

    public void updateLineInfo(int key, @Nonnull String msg) {
        synchronized (mLineInfo) {
            mLineInfo.put(key, msg);
        }
    }

    public void displayAsync(@Nullable Frame frame) {
        if (mDisplay != null && frame != null) {
            SwingUtilities.invokeLater(() -> mDisplay.showImage(frame));
        }
    }

    public void displaySync(@Nullable Frame frame) throws InvocationTargetException, InterruptedException {
        if (mDisplay != null && frame != null) {
            SwingUtilities.invokeAndWait(() -> {
                if (mDisplay != null && mDisplay.isVisible()) {
                    mDisplay.showImage(frame);
                }
            });
        }
    }

    private final StringBuffer _sTempBuf = new StringBuffer();
    public void displayLineInfo() {
        _sTempBuf.setLength(0);
        synchronized (mLineInfo) {
            for (String info : mLineInfo.values()) {
                if (_sTempBuf.length() > 0) {
                    _sTempBuf.append(" || ");
                }
                _sTempBuf.append(info);
            }
        }
        _sTempBuf.append('\r');
        mLogger.log(_sTempBuf.toString());
    }

    public void consoleWait() {
        mLogger.log(TAG, "Start loop");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        mLogger.log(TAG, "Press q+enter to quit, ?+enter for more options");

        final long sleepMs = 1000 / DEBUG_DISPLAY_FPS;
        long lastDelta = 0;

        try {
            while (!mQuit) {
                long startMs = System.currentTimeMillis();
                if (mDisplay != null && mDisplay.isVisible()) {
                    CamInfo cam1 = mCameras.getByIndex(mCameraIndex);
                    if (cam1 != null) {
                        Frame frame;
                        if (mToggleMask) {
                            frame = cam1.getAnalyzer().getLastFrame();
                        } else {
                            frame = cam1.getGrabber().getLastFrame();
                        }
                        displaySync(frame);
                    }
                }

                displayLineInfo();

                // This does not work very well.
//                mLogger.log(TAG, "Reader Ready :" + reader.ready() + " -- delta " + lastDelta);
                if (reader.ready()) {
                    char c = (char) reader.read();
                    processKey(c);
//                    String line = reader.readLine();
//                    mLogger.log(TAG, "Reader Line : '" + line + "'");
//                    if (line != null) {
//                        line = line.trim();
//                        if (line.length() > 0) {
//                            processKey(line.charAt(0));
//                        }
//                    }
                }

                long deltaMs = System.currentTimeMillis() - startMs;
                deltaMs = sleepMs - deltaMs;
                if (deltaMs > 0) {
                    try {
                        Thread.sleep(deltaMs);
                        lastDelta = sleepMs;
                    } catch (InterruptedException e) {
                        mLogger.log(TAG, e.toString());
                    }
                }
            }
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        }

        mLogger.log(TAG, "\nEnd loop");
    }

    private boolean processKey(char c) {
        switch (c) {
        case '?':
        case 'h':
            mLogger.log(TAG, "Keys: ?/h=help, esc/q=quit, d=disply on/off, m=toggle mask on/off, 1/2/3=show cam N");
            break;
        case 27:
        case 'q':
            requestQuit();
            break;
        case 'd':
            if (mDisplay != null) {
                mDisplay.setVisible(!mDisplay.isVisible());
                mLogger.log(TAG, "Display toggled " + (mDisplay.isVisible() ? "on" : "off"));
            } else {
                mLogger.log(TAG, "No display.");
            }
            break;
        case 'm':
            mToggleMask = !mToggleMask;
            mLogger.log(TAG, "Mask toggled " + (mToggleMask ? "on" : "off"));
            break;
        case '1':
        case '2':
        case '3':
            mLogger.log(TAG, "Select cam: " + c);
            int index = Integer.parseInt(Character.toString(c));
            if (mCameras.getByIndex(index) != null) {
                mCameraIndex = index;
            }
            break;
        default:
            mLogger.log(TAG, "Key ignored: '" + c + "' int: " + (int)c);
        case KeyEvent.VK_ENTER:
        case KeyEvent.CHAR_UNDEFINED:
            // ignore silently
            return false; // not consumed
        }

        return true; // consumed
    }
}
