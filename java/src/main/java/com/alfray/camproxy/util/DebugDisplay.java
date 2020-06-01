package com.alfray.camproxy.util;

import com.alfray.camproxy.CamProxy;
import com.alfray.camproxy.CommandLineArgs;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class DebugDisplay {
    private static final String TAG = DebugDisplay.class.getSimpleName();

    private final ILogger mLogger;
    private final CommandLineArgs mCommandLineArgs;
    private final Queue<Frame> mFrameQueue = new ConcurrentLinkedDeque<>();

    private boolean mQuit;
    private CanvasFrame mDisplay;

    @Inject
    public DebugDisplay(ILogger logger, CommandLineArgs commandLineArgs) {
        mLogger = logger;
        mCommandLineArgs = commandLineArgs;
    }

    public void start() {
        if (mCommandLineArgs.hasOption(CommandLineArgs.OPT_DEBUG_DISPLAY)) {
            mDisplay = new CanvasFrame("Test video");
            mDisplay.setSize(1280, 720);

            mQuit = false;
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
        if (mDisplay != null) {
            mQuit = true;
        }
    }

    public void stop() {
        if (mDisplay != null) {
            mDisplay.dispose();
            mDisplay = null;
        }
    }


    public void queue(Frame frame) {
        mFrameQueue.offer(frame);
    }

    public void waitTillClosed() {
        while (!mQuit) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    Frame frame = mFrameQueue.poll();
                    if (frame != null) {
                        mLogger.log(TAG, "Display Image");
                        mDisplay.showImage(frame);
                    }
                });

                Thread.sleep(300 /*ms*/);
            } catch (InterruptedException | InvocationTargetException e) {
                mLogger.log(TAG, e.toString());
            }
        }
    }
}
