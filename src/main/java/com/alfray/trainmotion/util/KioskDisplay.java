package com.alfray.trainmotion.util;

import com.alfray.trainmotion.CommandLineArgs;
import com.alfray.trainmotion.cam.Cameras;
import com.codebrig.journey.JourneyBrowserView;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class KioskDisplay implements IStartStop {
    private static final String TAG = KioskDisplay.class.getSimpleName();

    // The display does not need to run at the full input/output feed fps.
    private static final int DEBUG_DISPLAY_FPS = 10;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final DebugDisplay mDebugDisplay;
    private final CommandLineArgs mCommandLineArgs;

    private boolean mToggleMask;
    private JFrame mFrame;
    private JourneyBrowserView mWebView;

    @Inject
    public KioskDisplay(
            ILogger logger,
            Cameras cameras,
            DebugDisplay debugDisplay,
            CommandLineArgs commandLineArgs) {
        mLogger = logger;
        mCameras = cameras;
        mDebugDisplay = debugDisplay;
        mCommandLineArgs = commandLineArgs;
    }

    public void start() {
        mFrame = new JFrame("Train Motion");

        // Source: https://github.com/CodeBrig/Journey
        // CEF = Chrome Embedded Framework (to embed Chrome in apps).
        // JCEF = Java-CEF = Java native bindinds for CEF. Java + native libs (Mac, Linux, Win).
        // Journey is built on top of JCEF and simplifies usage of the JCEF API even more.

        mWebView = new JourneyBrowserView();

        mFrame.add(mWebView);


//        mDisplay = new CanvasFrame("Test video");
//        mDisplay.setSize(CamInputGrabber.DEFAULT_WIDTH, CamInputGrabber.DEFAULT_HEIGHT);


        mFrame.setSize(800, 600); // FIXME
        mFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                mDebugDisplay.requestQuit();
            }
        });

//        // FIXME this only works as long as focus is _not_ forced on the image view.
//        mDisplay.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyPressed(KeyEvent keyEvent) {
//                if (processKey(keyEvent.getKeyChar())) {
//                    keyEvent.consume();
//                }
//                super.keyPressed(keyEvent);
//            }
//        });

        // Start visible in --debug mode
//        mDisplay.setVisible(mCommandLineArgs.hasOption(CommandLineArgs.OPT_DEBUG_DISPLAY));

        mFrame.setVisible(true);
    }

    public void stop() {
        if (mFrame != null) {
            mWebView.getCefApp().dispose();
            mFrame.dispose();
            mFrame = null;
        }
    }

    public void loadPage() {
        mWebView.getCefBrowser().loadURL("http://localhost:8080/yt.html");
    }

//    public void displayAsync(@Nullable Frame frame) {
//        if (mDisplay != null && frame != null) {
//            SwingUtilities.invokeLater(() -> mDisplay.showImage(frame));
//        }
//    }
//
//    public void displaySync(@Nullable Frame frame) throws InvocationTargetException, InterruptedException {
//        if (mDisplay != null && frame != null) {
//            SwingUtilities.invokeAndWait(() -> {
//                if (mDisplay != null && mDisplay.isVisible()) {
//                    mDisplay.showImage(frame);
//                }
//            });
//        }
//    }

}
