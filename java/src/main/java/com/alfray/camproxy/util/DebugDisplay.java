package com.alfray.camproxy.util;

import com.alfray.camproxy.CommandLineArgs;
import com.alfray.camproxy.cam.CamInfo;
import com.alfray.camproxy.cam.Cameras;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.opencv.core.Core;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
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
    private OpenCVFrameConverter.ToMat mMatConverter;

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

        mMatConverter = new OpenCVFrameConverter.ToMat();
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

    public void displayAsync(@Nullable final Frame frame, @Nullable final Frame mask) {
        if (mDisplay != null && frame != null) {
            SwingUtilities.invokeLater(() -> {
                Frame _frame = frame;
                if (mask != null) {
//                    Frame dest = _frame.clone();
//                    Mat output = mMatConverter.convert(dest);
//                    Mat input = mMatConverter.convert(frame);
                    Mat mmask = mMatConverter.convert(mask);

                    int nz = opencv_core.countNonZero(mmask);
                    int npx = mask.imageHeight * mask.imageWidth;
                    double noisePercent = 100.0 * nz / npx;
                    mLogger.log(TAG, "Mask Non-zero: " + nz + " => " + noisePercent);

                    if (noisePercent > 10) {
                        _frame = mask;
                    }
//                    // opencv_core.multiply(input, mmask, output);
//                    input.copyTo(output, mmask);
//                    _frame = dest;
                }
                mDisplay.showImage(_frame);
            });
        }
    }

    public void consoleWait() {
        mLogger.log(TAG, "Start loop");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        mLogger.log(TAG, "Press Enter to quit...");

        final long sleepMs = 1000 / ANALYZER_FPS;

        try {
            while (!mQuit && !reader.ready()) {
                long startMs = System.currentTimeMillis();
                if (mDisplay != null) {
                    CamInfo cam1 = mCameras.getByIndex(1);
                    if (cam1 != null) {
                        Frame mask = cam1.getAnalyzer().getLastFrame();
                        Frame frame = cam1.getGrabber().getLastFrame();
                        displayAsync(frame, mask);
                    }
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
}
