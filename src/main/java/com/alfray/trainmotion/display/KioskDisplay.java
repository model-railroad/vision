package com.alfray.trainmotion.display;

import com.alfray.trainmotion.CommandLineArgs;
import com.alfray.trainmotion.Playlist;
import com.alfray.trainmotion.cam.CamInputGrabber;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;
import com.alfray.trainmotion.util.ThreadLoop;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Optional;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class KioskDisplay implements IStartStop {
    private static final String TAG = KioskDisplay.class.getSimpleName();

//    // The display does not need to run at the full input/output feed fps.
    private static final int DISPLAY_FPS = 30;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final Playlist mPlaylist;
    private final DebugDisplay mDebugDisplay;

    private boolean mToggleMask;
    private JFrame mFrame;
    private EmbeddedMediaPlayerComponent mMediaPlayer;

    @Inject
    public KioskDisplay(
            ILogger logger,
            Cameras cameras,
            Playlist playlist,
            DebugDisplay debugDisplay) {
        mLogger = logger;
        mCameras = cameras;
        mPlaylist = playlist;
        mDebugDisplay = debugDisplay;
    }

    @Override
    public void start() throws Exception {
        mFrame = new JFrame("Kiosk video");
        mFrame.setSize(800, 600); // FIXME

        mMediaPlayer = new EmbeddedMediaPlayerComponent();
        mFrame.add(mMediaPlayer);

        mFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                mDebugDisplay.requestQuit();
            }
        });

        mFrame.setVisible(true);
    }

    @Override
    public void stop() throws Exception {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mFrame != null) {
            mFrame.dispose();
            mFrame = null;
        }
    }

    public void initialize() throws Exception {
        SwingUtilities.invokeLater(() -> {
            Optional<File> next = mPlaylist.getNext();
            if (next.isPresent()) {
                File file = next.get();
                mLogger.log(TAG, "Start PlaylistThread file = " + file.getAbsolutePath());

                mMediaPlayer.mediaPlayer().audio().setMute(true);
                // mMediaPlayer.mediaPlayer().audio().setVolume(0);
                mMediaPlayer.mediaPlayer().media().play(file.getAbsolutePath());
            }
        });
    }

//
//                        mLogger.log(TAG, String.format("FrameGrabber size=%dx%d, fps=%f, gamma=%f",
//                                mFrameGrabber.getImageWidth(),
//                                mFrameGrabber.getImageHeight(),
//                                mFrameGrabber.getFrameRate(),
//                                mFrameGrabber.getGamma()
//                                ));
}
