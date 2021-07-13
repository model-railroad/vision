package com.alfray.trainmotion.display;

import com.alfray.trainmotion.CommandLineArgs;
import com.alfray.trainmotion.Playlist;
import com.alfray.trainmotion.cam.CamInputGrabber;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.ThreadLoop;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Optional;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class KioskDisplay extends ThreadLoop {
    private static final String TAG = KioskDisplay.class.getSimpleName();

//    // The display does not need to run at the full input/output feed fps.
    private static final int DISPLAY_FPS = 30;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final Playlist mPlaylist;
    private final DebugDisplay mDebugDisplay;
    private final CommandLineArgs mCommandLineArgs;

    private boolean mToggleMask;
    private JFrame mFrame;
    private CanvasFrame mDisplay;
    private FFmpegFrameGrabber mFrameGrabber;
    private boolean mStartPlaying;

    @Inject
    public KioskDisplay(
            ILogger logger,
            Cameras cameras,
            Playlist playlist,
            DebugDisplay debugDisplay,
            CommandLineArgs commandLineArgs) {
        mLogger = logger;
        mCameras = cameras;
        mPlaylist = playlist;
        mDebugDisplay = debugDisplay;
        mCommandLineArgs = commandLineArgs;
    }

    @Override
    public void start() throws Exception {
        // Note: FrameGrabber.getGamma() return 2.2 below. Using that or 0.0 (the default), or
        // calling CanvasFrame.getDefaultGamma() makes it all look washed out (not enough constrast).
        // Randomly pick a gamma that makes it look good, however it reduces performance even further
        // (the CanvasFrame code clones/multiplies every single frame).
        mDisplay = new CanvasFrame("Kiosk video", /* gamma= */ 1.3);
        mDisplay.setSize(CamInputGrabber.DEFAULT_WIDTH, CamInputGrabber.DEFAULT_HEIGHT);
        mFrame = mDisplay;

        mFrame.setSize(800, 600); // FIXME
        mFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                mDebugDisplay.requestQuit();
            }
        });

        mFrame.setVisible(true);
        super.start();
    }

    @Override
    public void stop() throws Exception {
        mStartPlaying = false;
        super.stop();
        if (mFrameGrabber != null) {
            mFrameGrabber.stop();
            mFrameGrabber.release();
        }
        if (mFrame != null) {
            mFrame.dispose();
            mFrame = null;
        }
    }

    public void initialize() throws Exception {
        mStartPlaying = true;
    }

    @Override
    protected void _runInThreadLoop() {
        try {
            if (mStartPlaying) {
                if (mFrameGrabber == null) {
                    Optional<File> next = mPlaylist.getNext();
                    if (next.isPresent()) {
                        File file = next.get();
                        mLogger.log(TAG, "Start PlaylistThread file = " + file.getAbsolutePath());
                        mFrameGrabber = new FFmpegFrameGrabber(file);
                        mFrameGrabber.start();

                        mLogger.log(TAG, String.format("FrameGrabber size=%dx%d, fps=%f, gamma=%f",
                                mFrameGrabber.getImageWidth(),
                                mFrameGrabber.getImageHeight(),
                                mFrameGrabber.getFrameRate(),
                                mFrameGrabber.getGamma()
                                ));
                    }
                }

                if (mFrameGrabber != null) {
                    Frame grab = mFrameGrabber.grab();
                    if (grab != null) {
                        mDisplay.showImage(grab);

                    }
                }
            }

            Thread.sleep(1000 / DISPLAY_FPS);

        } catch (FrameGrabber.Exception | InterruptedException e) {
            mLogger.log(TAG, e.toString());
        }
    }
}
