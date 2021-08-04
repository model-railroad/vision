/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.ConfigIni;
import com.alflabs.trainmotion.Playlist;
import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;
import com.alflabs.utils.IClock;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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

    // Approximage FPS to update the camera videos.
    private static final int DISPLAY_FPS = 15;

    // Highlight color
    private static final Color HIGHLIGHT_LINE_COLOR = Color.YELLOW;
    // Highlight minimum display duration with video motion ON. Total with OFF is 3 seconds.
    private static final long HIGHLIGHT_DURATION_ON_MS = 2500;
    // Highlight minimum display duration with video motion OFF after a ON event.
    private static final long HIGHLIGHT_DURATION_OFF_MS = 500;
    // Highlight stroke width
    private static final int HIGHLIGHT_LINE_SIZE = 10;

    // Player zoom minimum display duration
    private static final long PLAYER_ZOOM_MIN_DURATION_MS = 5*1000;
    // Player default volume percentage
    private static final int PLAYER_VOLUME_DEFAULT = 50;

    private final IClock mClock;
    private final ILogger mLogger;
    private final Cameras mCameras;
    private final Playlist mPlaylist;
    private final ConfigIni mConfigIni;
    private final Analytics mAnalytics;
    private final ConsoleTask mConsoleTask;

    @GuardedBy("mVideoCanvas")
    private final List<VideoCanvas> mVideoCanvas = new ArrayList<>();
    private JFrame mFrame;
    private JLabel mBottomLabel;
    private EmbeddedMediaPlayerComponent mMediaPlayer;
    private Timer mRepaintTimer;
    private int mVideosWidth;
    private int mVideosHeight;
    private boolean mForceZoom;
    private boolean mPlayerMuted;
    private boolean mToggleMask;
    private int mPlayerMaxVolume = PLAYER_VOLUME_DEFAULT;
    private long mPlayerZoomEndTS;


    @Inject
    public KioskDisplay(
            IClock clock,
            ILogger logger,
            Cameras cameras,
            Playlist playlist,
            ConfigIni configIni,
            Analytics analytics,
            ConsoleTask consoleTask) {
        mClock = clock;
        mLogger = logger;
        mCameras = cameras;
        mPlaylist = playlist;
        mConfigIni = configIni;
        mAnalytics = analytics;
        mConsoleTask = consoleTask;
    }

    @Override
    public void start() throws Exception {
        mFrame = new JFrame(mConfigIni.getWindowTitle("Train Motion"));
        mFrame.setSize(800, 600); // random startup value; most of the time it is maximized below.
        mFrame.setMinimumSize(new Dimension(64, 64));
        mFrame.setLayout(null);
        mFrame.setBackground(Color.BLACK);

        mMediaPlayer = new EmbeddedMediaPlayerComponent();
        mMediaPlayer.setBackground(Color.BLACK);
        mMediaPlayer.setBounds(0, 0, 800, 600); // matches initial frame
        mFrame.add(mMediaPlayer);

        mBottomLabel = new JLabel("Please wait, initializing camera streams...");
        mBottomLabel.setOpaque(true);
        mBottomLabel.setBackground(Color.BLACK);
        mBottomLabel.setForeground(Color.LIGHT_GRAY);
        mBottomLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        mFrame.add(mBottomLabel);

        mFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                mConsoleTask.requestQuit();
            }
        });
        mFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                super.componentResized(event);
                onFrameResized(event);
            }
        });
        mFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (processKey(keyEvent.getKeyChar())
                        || mConsoleTask.processKey(keyEvent.getKeyChar())) {
                    keyEvent.consume();
                }
                super.keyPressed(keyEvent);
            }
        });

        // Create an "invalid" cursor to make the cursor transparent in the frame.
        try {
            Toolkit toolkit = mFrame.getToolkit();
            mFrame.setCursor(toolkit.createCustomCursor(toolkit.createImage(""), new Point(), "cursor"));
        } catch (Exception ignore) {}

        boolean maximize = mConfigIni.getWindowMaximize();
        if (maximize) {
            // Remove window borders. Must be done before the setVisible call.
            mFrame.setUndecorated(true);
        }

        mFrame.setVisible(true);
        // Canvases use a "buffered strategy" (to have 2 buffers) and must be created
        // after the main frame is set visible.
        createVideoCanvas();
        onFrameResized(null /* event */);
        if (maximize) {
            mFrame.setExtendedState(mFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }

        mRepaintTimer = new Timer(1000 / DISPLAY_FPS, this::onRepaintTimerTick);
    }

    private void createVideoCanvas() {
        AtomicInteger posIndex = new AtomicInteger();
        synchronized (mVideoCanvas) {
            mCameras.forEachCamera(camInfo -> {
                VideoCanvas canvas = new VideoCanvas(posIndex.incrementAndGet(), camInfo);
                mVideoCanvas.add(canvas);
                mFrame.add(canvas);
                canvas.initialize();
            });
        }
    }

    private void computeLayout() {
        if (mFrame != null) {
            Insets insets = mFrame.getInsets();
            mVideosWidth = mFrame.getWidth() - insets.left - insets.right;
            mVideosHeight = mFrame.getHeight() - insets.top - insets.bottom;

            Dimension labelSize = mBottomLabel.getPreferredSize();
            if (labelSize != null) {
                int lh = labelSize.height;
                mVideosHeight -= lh;
                mBottomLabel.setBounds(0, mVideosHeight, mVideosWidth, lh);
            }
        }
    }

    public boolean processKey(char c) {
        // Keys handled by the ConsoleTask:
        // esc, q = quit // ?, h = help.
        // mLogger.log(TAG, "Process key: " + c); // DEBUG
        switch (c) {
        case 'f':
            // Toggle fullscreen zoom
            mPlayerZoomEndTS = 0;
            mForceZoom = !mForceZoom;
            return true;
        case 'm':
            // Toggle mute sound
            if (mMediaPlayer != null) {
                // Note mMediaPlayer.mediaPlayer().audio().setMute(!muted) seems to work in reverse
                // (and/or differently per platform) so let's avoid it. Just control volume.
                mPlayerMuted = !mPlayerMuted;
                mMediaPlayer.mediaPlayer().audio().setVolume(mPlayerMuted ? 0 : mPlayerMaxVolume);
                mLogger.log(TAG, "Audio: volume " + mMediaPlayer.mediaPlayer().audio().volume() + "%");
            }
            return true;
        case 's':
            // Toggle shuffle
            mPlaylist.setShuffle(!mPlaylist.isShuffle());
            return true;
        case 'n':
            playNext();
            return true;
        case 'k':
            mToggleMask = !mToggleMask;
            mLogger.log(TAG, "Mask toggled " + (mToggleMask ? "on" : "off"));
            return true;
        }

        return false; // not consumed
    }

    private void onFrameResized(ComponentEvent event) {
        if (mFrame != null) {
            computeLayout();
            final int width = mVideosWidth;
            final int height = mVideosHeight;
            mLogger.log(TAG, String.format("onFrameResized --> %dx%d", width, height));

            synchronized (mVideoCanvas) {
                for (VideoCanvas canvas : mVideoCanvas) {
                    canvas.computeAbsolutePosition(width, height);
                }
            }

            mFrame.revalidate();
        }
    }

    private void onRepaintTimerTick(ActionEvent event) {
        if (mFrame != null && mMediaPlayer != null && !mConsoleTask.isQuitRequested()) {
            mBottomLabel.setText(mConsoleTask.computeLineInfo());

            boolean hasHighlight = false;
            synchronized (mVideoCanvas) {
                for (VideoCanvas canvas : mVideoCanvas) {
                    canvas.displayFrame();
                    hasHighlight |= canvas.isHighlighted();
                }
            }

            // frame (window) size
            computeLayout();
            final int fw = mVideosWidth;
            final int fh = mVideosHeight;
            // target size for media player
            int tw = fw, th = fh;
            if (hasHighlight && !mForceZoom) {
                // Desired player is half size screen
                tw = fw / 2;
                th = fh / 2;
            }
            // current player size
            int pw = mMediaPlayer.getWidth();
            int ph = mMediaPlayer.getHeight();
            if (Math.abs(tw - pw) > 1 || Math.abs(th - ph) > 1) {
                if (mPlayerZoomEndTS < mClock.elapsedRealtime()) { // don't change too fast
                    /* if (tw != pw) {
                        tw = pw + (tw - pw) / 2;
                    }
                    if (th != ph) {
                        th = ph + (th - ph) / 2;
                    } */
                    mMediaPlayer.setBounds(0, 0, tw, th);
                    mMediaPlayer.revalidate();
                    mPlayerZoomEndTS = mClock.elapsedRealtime() + PLAYER_ZOOM_MIN_DURATION_MS;
                }
            }
        }
    }

    @Override
    public void stop() throws Exception {
        SwingUtilities.invokeLater(() -> {
            mLogger.log(TAG, "Stop");
            mRepaintTimer.stop();
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            if (mFrame != null) {
                mFrame.dispose();
                mFrame = null;
            }
        });
    }

    public void initialize() throws Exception {
        // Start shuffled
        mPlaylist.setShuffle(true);

        // Get desired volume
        mPlayerMaxVolume = mConfigIni.getVolumePct(PLAYER_VOLUME_DEFAULT);

        mMediaPlayer.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                super.finished(mediaPlayer);
                mLogger.log(TAG, "Media Finished: " + mediaPlayer);
                playNext();
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                super.error(mediaPlayer);
                mLogger.log(TAG, "Media Error: " + mediaPlayer);
                playNext();
            }
        });

        SwingUtilities.invokeLater(() -> {
            if (mMediaPlayer != null && !mConsoleTask.isQuitRequested()) {
                mRepaintTimer.start();
                mMediaPlayer.mediaPlayer().audio().setMute(false);
                playNext();
            }
        });
    }

    private void playNext() {
        SwingUtilities.invokeLater(() -> {
            if (mMediaPlayer != null && !mConsoleTask.isQuitRequested()) {
                Optional<File> next = mPlaylist.getNext();
                if (next.isPresent()) {
                    File file = next.get();
                    mLogger.log(TAG, "Player file = " + file.getAbsolutePath());
                    mAnalytics.sendEvent("PlayVideo", file.getName());

                    mMediaPlayer.mediaPlayer().audio().setVolume(mPlayerMuted ? 0 : mPlayerMaxVolume);
                    mMediaPlayer.mediaPlayer().media().play(file.getAbsolutePath());
                }
            }
        });
    }

    /** Based on org/bytedeco/javacv/CanvasFrame.java */
    private class VideoCanvas extends Canvas {
        private static final boolean USE_BUFFERS = true;
        private final Java2DFrameConverter mConverter = new Java2DFrameConverter();

        private final int mPosIndex;
        private final CamInfo mCamInfo;
        /** Show highlight if > 0. Indicates when highlight ON started. */
        private long mHighlightOnMS;
        /** Show highlight if > 0. Indicates when highlight OFF started. */
        private long mHighlightOffMS;
        private Image mImage;

        public VideoCanvas(int posIndex, CamInfo camInfo) {
            mPosIndex = posIndex;
            mCamInfo = camInfo;
            setBackground(Color.BLACK);
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
            if (mImage == null) {
                super.paint(g);
                return;
            }

            // Aspect ratio computation
            // Canvas ratio cr = cw / ch.
            // Image  ratio ir = iw / ih.
            // If ir < cr, image is thinner, canvas is wider: fit H, resize W.
            // If ir > cr, image is wider, canvas is thinner: fit W, resize H.
            final int cw = getWidth();
            final int ch = getHeight();
            final int iw = mImage.getWidth(null /* observer */);
            final int ih = mImage.getHeight(null /* observer */);
            if (cw <= 0 || ch <= 0 || iw <= 0 || ih <= 0) {
                return;
            }
            double cr = (double) cw / (double) ch;
            double ir = (double) iw / (double) ih;
            int dw, dh;
            if (ir < cr) {
                dh = ch;
                dw = (int)(ch * ir);
            } else {
                dw = cw;
                dh = (int)(cw / ir);
            }
            final int dx = (cw - dw) / 2;
            final int dy = (ch - dh) / 2;

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
                                drawImageAndContour(g, dw, dh, dx, dy);
                            }
                            // g.drawRect(0, 0, cw-1, ch-1); // DEBUG
                            g.dispose();
                        } while (strategy.contentsRestored());
                        strategy.show();
                    } while (strategy.contentsLost());
                } catch (NullPointerException | IllegalStateException ignored) {
                }
            } else {
                drawImageAndContour(g, dw, dh, dx, dy);
                // g.drawRect(0, 0, cw-1, ch-1); // DEBUG
            }
        }

        private void drawImageAndContour(Graphics g, int dw, int dh, int dx, int dy) {
            g.drawImage(mImage, dx, dy, dw, dh, null /* observer */);

            if (isHighlighted()) {
                g.setColor(HIGHLIGHT_LINE_COLOR);
                g.fillRect(dx, dy, dw, HIGHLIGHT_LINE_SIZE);
                g.fillRect(dx, dy + dh - HIGHLIGHT_LINE_SIZE, dw, HIGHLIGHT_LINE_SIZE);
                g.fillRect(dx, dy, HIGHLIGHT_LINE_SIZE, dh);
                g.fillRect(dx + dw - HIGHLIGHT_LINE_SIZE, dy, HIGHLIGHT_LINE_SIZE, dh);
            }
        }

        public boolean isHighlighted() {
            return mHighlightOnMS > 0;
        }

        /** Must be invoked on the Swing UI thread. */
        public void displayFrame() {
            Frame frame;
            if (mToggleMask) {
                frame = mCamInfo.getAnalyzer().getLastFrame();
            } else {
                frame = mCamInfo.getGrabber().getLastFrame();
            }

            long nowMs = mClock.elapsedRealtime();
            boolean motionDetected = mCamInfo.getAnalyzer().isMotionDetected();
            if (mHighlightOnMS == 0) {
                if (motionDetected) {
                    mHighlightOnMS = nowMs;
                }
            } else {
                long duration = nowMs - mHighlightOnMS;
                if (mHighlightOffMS == 0
                        && !motionDetected
                        && duration >= HIGHLIGHT_DURATION_ON_MS) {
                    // mHighlightOnMS is > 0 ... motion was ON and stopped.
                    mHighlightOffMS = nowMs;
                } else if (mHighlightOffMS > 0
                        && !motionDetected
                        && duration >= (HIGHLIGHT_DURATION_ON_MS + HIGHLIGHT_DURATION_OFF_MS)) {
                    // mHighlightOnMS is > 0 and mHighlightOffMS is > 0.
                    // Motion was ON and has stopped for at least the OFF duration.
                    mHighlightOnMS = 0;
                    mHighlightOffMS = 0;
                    mAnalytics.sendEvent("Highlight", "cam" + mCamInfo.getIndex(), Long.toString(duration));
                }
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
            //mLogger.log(TAG, String.format("   Video %d, cam%d = %dx%d [ %dx%d ]",
            //        mPosIndex, mCamInfo.getIndex(), x, y, w, h));
        }
    }

}
