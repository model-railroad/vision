package com.alfray.trainmotion.display;

import com.alfray.trainmotion.Playlist;
import com.alfray.trainmotion.cam.CamInfo;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

@Singleton
public class KioskDisplay implements IStartStop {
    private static final String TAG = KioskDisplay.class.getSimpleName();

    // The display does not need to run at the full input/output feed fps.
    private static final int DISPLAY_FPS = 15;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final Playlist mPlaylist;
    private final DebugDisplay mDebugDisplay;

    private final List<VideoCanvas> mVideoCanvas = new ArrayList<>();
    private boolean mToggleMask;
    private JFrame mFrame;
    private EmbeddedMediaPlayerComponent mMediaPlayer;
    private Timer mRepaintTimer;

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
        mFrame.setLayout(null);

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
        mFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                super.componentResized(event);
                mLogger.log(TAG, String.format("componentResized --> %dx%d", mFrame.getWidth(), mFrame.getHeight()));
                onFrameResized(event);
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                super.componentMoved(event);
                mLogger.log(TAG, String.format("componentMoved --> %dx%d", mFrame.getWidth(), mFrame.getHeight()));
                onFrameResized(event);
            }

            @Override
            public void componentShown(ComponentEvent event) {
                super.componentShown(event);
                mLogger.log(TAG, String.format("componentShown --> %dx%d", mFrame.getWidth(), mFrame.getHeight()));
                onFrameResized(event);
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                super.componentHidden(event);
                mLogger.log(TAG, String.format("componentHidden --> %dx%d", mFrame.getWidth(), mFrame.getHeight()));
                onFrameResized(event);
            }
        });

        mFrame.setVisible(true);

        mRepaintTimer = new Timer(1000 / DISPLAY_FPS, this::onRepaintTimerTick);
    }

    private void createVideoCanvas() {
        AtomicInteger posIndex = new AtomicInteger();
        mCameras.forEachCamera(camInfo -> {
            VideoCanvas canvas = new VideoCanvas(posIndex.incrementAndGet(), camInfo);
            mVideoCanvas.add(canvas);
            mFrame.add(canvas);
            canvas.initialize();
        });
    }

    private void onFrameResized(ComponentEvent event) {
        if (mFrame != null) {
            int width = mFrame.getWidth();
            int height = mFrame.getHeight();

            mMediaPlayer.setBounds(0, 0, width / 2, height / 2);

            for (VideoCanvas canvas : mVideoCanvas) {
                canvas.computeAbsolutePosition(width, height);
            }
        }
    }

    private void onRepaintTimerTick(ActionEvent event) {
        if (mFrame != null) {
            for (VideoCanvas canvas : mVideoCanvas) {
                canvas.displayFrame();
            }
        }
    }

    @Override
    public void stop() throws Exception {
        mRepaintTimer.stop();
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
            // Canvases use a "buffered strategy" (to have 2 buffers) and must be created
            // after the main frame is set visible.
            createVideoCanvas();

            onFrameResized(null /* event */);
            mRepaintTimer.start();

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

    /** Based on org/bytedeco/javacv/CanvasFrame.java */
    private class VideoCanvas extends Canvas {
        private final boolean USE_BUFFERS = true;
        private final Java2DFrameConverter mConverter = new Java2DFrameConverter();

        private final int mPosIndex;
        private final CamInfo mCamInfo;
        private Image mImage;

        public VideoCanvas(int posIndex, CamInfo camInfo) {
            mPosIndex = posIndex;
            mCamInfo = camInfo;
            setBackground(Color.DARK_GRAY);
        }

        public void initialize() {
            setVisible(true);
            if (USE_BUFFERS) {
                createBufferStrategy(2);
            }
        }

        @Override
        public void update(Graphics g) {
            paint(g);
        }

        @Override
        public void paint(Graphics g) {
            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            if (USE_BUFFERS) {
                // Calling BufferStrategy.show() here sometimes throws
                // NullPointerException or IllegalStateException,
                // but otherwise seems to work fine.
                try {

                    BufferStrategy strategy = getBufferStrategy();
                    do {
                        do {
                            g = strategy.getDrawGraphics();
                            if (mImage != null) {
                                g.drawImage(mImage, 0, 0, w, h, null);
                            }
                            g.dispose();
                        } while (strategy.contentsRestored());
                        strategy.show();
                    } while (strategy.contentsLost());
                } catch (NullPointerException | IllegalStateException ignored) {
                }
            } else {
                super.paint(g);
                g.drawImage(mImage, 0, 0, w, h, null /* observer */);
            }
        }

        /** Must be invoked on the Swing UI thread. */
        public void displayFrame() {
            Frame frame;
            if (mToggleMask) {
                frame = mCamInfo.getAnalyzer().getLastFrame();
            } else {
                frame = mCamInfo.getGrabber().getLastFrame();
            }
            if (frame != null) {
                mImage = mConverter.getBufferedImage(frame);
                repaint();
            }
        }

        public void computeAbsolutePosition(int frameW, int frameH) {
            if (frameH <= 0 || frameW <= 0) {
                return;
            }
            int x = mPosIndex % 2;
            int y = mPosIndex / 2;
            int w = frameW / 2;
            int h = frameH / 2;
            x *= w;
            y *= h;
            this.setBounds(x, y, w, h);
            mLogger.log(TAG, String.format("   Video %d, cam%d = %dx%d [ %dx%d ]",
                    mPosIndex, mCamInfo.getIndex(), x, y, w, h));
        }
    }

}
