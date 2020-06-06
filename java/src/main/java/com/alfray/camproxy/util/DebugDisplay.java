package com.alfray.camproxy.util;

import com.alfray.camproxy.CommandLineArgs;
import com.alfray.camproxy.cam.CamInfo;
import com.alfray.camproxy.cam.Cameras;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class DebugDisplay implements IStartStop {
    private static final String TAG = DebugDisplay.class.getSimpleName();

    // The display does not need to run at the full input/output feed fps.
    private static final int ANALYZER_FPS = 10;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final CommandLineArgs mCommandLineArgs;

    private boolean mQuit;
    private CanvasFrame mDisplay;
    private boolean mToggleMask;

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

        if (mCommandLineArgs.hasOption(CommandLineArgs.OPT_DEBUG_DISPLAY)) {
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

            mDisplay.setVisible(true);
        }
    }

    public void requestQuit() {
        mLogger.log(TAG, "Quit Requested");
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

    public void displayAsync(@Nullable Frame frame, @Nullable Frame mask) {
        if (mToggleMask && mask != null) {
            frame = mask;
        }
        final Frame _frame = frame;
        if (mDisplay != null && _frame != null) {
            SwingUtilities.invokeLater(() -> mDisplay.showImage(_frame));
        }
    }

    public void consoleWait() {
        mLogger.log(TAG, "Start loop");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        mLogger.log(TAG, "Press q+enter to quit, ?+enter for more options");

        final long sleepMs = 1000 / ANALYZER_FPS;

        try {
            while (!mQuit) {
                long startMs = System.currentTimeMillis();
                if (mDisplay != null) {
                    CamInfo cam1 = mCameras.getByIndex(1);
                    if (cam1 != null) {
                        Frame mask = cam1.getAnalyzer().getLastFrame();
                        Frame frame = cam1.getGrabber().getLastFrame();
                        displayAsync(frame, mask);
                    }
                }

                if (reader.ready()) {
                    char c = (char) reader.read();
                    processKey(c);
                }

                long deltaMs = System.currentTimeMillis() - startMs;
                if (deltaMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        mLogger.log(TAG, e.toString());
                    }
                }
            }
        } catch (IOException e) {
            mLogger.log(TAG, e.toString());
        }

        mLogger.log(TAG, "End loop");
    }

    private boolean processKey(char c) {
        switch (c) {
        case '?':
        case 'h':
            mLogger.log(TAG, "Keys: ?/h=help, esc/q=quit, m=toggle mask on/off, 1/2/3=show cam N");
            break;
        case 27:
        case 'q':
            requestQuit();
            break;
        case 'm':
            mToggleMask = !mToggleMask;
            mLogger.log(TAG, "Mask toggled " + (mToggleMask ? "on" : "off"));
            break;
        case '1':
        case '2':
        case '3':
            mLogger.log(TAG, "Select cam: " + c);
            break;
        default:
            mLogger.log(TAG, "Key ignored: '" + c + "' int: " + (int)c);
        case KeyEvent.CHAR_UNDEFINED:
            // ignore silently
            return false; // not consumed
        }

        return true; // consumed
    }
}
