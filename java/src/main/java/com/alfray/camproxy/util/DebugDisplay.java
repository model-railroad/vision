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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicReference;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class DebugDisplay implements IStartStop {
    private static final String TAG = DebugDisplay.class.getSimpleName();

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final CommandLineArgs mCommandLineArgs;

    private boolean mQuit;
    private CanvasFrame mDisplay;

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

            mDisplay.setVisible(true);
        }
    }

    public void requestQuit() {
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

    public void displayAsync(@Nullable Frame frame) {
        if (mDisplay != null && frame != null) {
            SwingUtilities.invokeLater(() -> mDisplay.showImage(frame));
        }
    }

    public void consoleWait() {
        mLogger.log(TAG, "Start loop");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        mLogger.log(TAG, "Press Enter to quit...");

        try {
            while (!mQuit && !reader.ready()) {
                if (mDisplay != null) {
                    CamInfo cam1 = mCameras.getByIndex(1);
                    if (cam1 != null) {
                        displayAsync(cam1.getGrabber().getLastFrame());
                    }
                }

                try {
                    Thread.sleep(100); // 10 fps
                } catch (InterruptedException e) {
                    mLogger.log(TAG, e.toString());
                }
            }
        } catch (IOException e) {
            mLogger.log(TAG, e.toString());
        }

        mLogger.log(TAG, "End loop");
    }
}
