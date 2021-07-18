package com.alfray.trainmotion.display;

import com.alfray.trainmotion.ConfigIni;
import com.alfray.trainmotion.Playlist;
import com.alfray.trainmotion.cam.CamInfo;
import com.alfray.trainmotion.cam.Cameras;
import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
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
    // Highlight minimum display duration
    private static final long HIGHLIGHT_DURATION_MS = 3*1000;
    // Highlight stroke width
    private static final int HIGHLIGHT_LINE_SIZE = 10;

    // Player zoom minimum display duration
    private static final long PLAYER_ZOOM_MIN_DURATION_MS = 5*1000;
    // Player default volume percentage
    private static final int PLAYER_VOLUME_DEFAULT = 50;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final Playlist mPlaylist;
    private final ConfigIni mConfigIni;
    private final DebugDisplay mDebugDisplay;

    private final List<VideoCanvas> mVideoCanvas = new ArrayList<>();
    private JFrame mFrame;
    private JLabel mBottomLabel;
    private EmbeddedMediaPlayerComponent mMediaPlayer;
    private Timer mRepaintTimer;
    private int mVideosWidth;
    private int mVideosHeight;
    private boolean mForceZoom;
    private boolean mPlayerMuted;
    private int mPlayerMaxVolume = PLAYER_VOLUME_DEFAULT;
    private long mPlayerZoomEndTS;


    @Inject
    public KioskDisplay(
            ILogger logger,
            Cameras cameras,
            Playlist playlist,
            ConfigIni configIni,
            DebugDisplay debugDisplay) {
        mLogger = logger;
        mCameras = cameras;
        mPlaylist = playlist;
        mConfigIni = configIni;
        mDebugDisplay = debugDisplay;
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
                mDebugDisplay.requestQuit();
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
                        || mDebugDisplay.processKey(keyEvent.getKeyChar())) {
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
        mCameras.forEachCamera(camInfo -> {
            VideoCanvas canvas = new VideoCanvas(posIndex.incrementAndGet(), camInfo);
            mVideoCanvas.add(canvas);
            mFrame.add(canvas);
            canvas.initialize();
        });
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
        // Keys handled by the DebugDisplay:
        // esc, q = quit // d = toggle debug // m = toggle mask // 1, 2, 3 = debug cam select.
        // mLogger.log(TAG, "Process key: " + c); // DEBUG
        switch (c) {
        case 'f':
            // Toggle fullscreen zoom
            mPlayerZoomEndTS = 0;
            mForceZoom = !mForceZoom;
            return true;
        case 's':
            // Toggle sound
            if (mMediaPlayer != null) {
                // Note mMediaPlayer.mediaPlayer().audio().setMute(!muted) seems to work in reverse
                // (and/or differently per platform) so let's avoid it. Just control volume.
                mPlayerMuted = !mPlayerMuted;
                mMediaPlayer.mediaPlayer().audio().setVolume(mPlayerMuted ? 0 : mPlayerMaxVolume);
                mLogger.log(TAG, "Audio: volume " + mMediaPlayer.mediaPlayer().audio().volume() + "%");
            }
            return true;
        case 'u':
            // Toggle shuffle
            mPlaylist.setShuffle(!mPlaylist.isShuffle());
            return true;
        case 'n':
            playNext();
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

            for (VideoCanvas canvas : mVideoCanvas) {
                canvas.computeAbsolutePosition(width, height);
            }

            mFrame.revalidate();
        }
    }

    private void onRepaintTimerTick(ActionEvent event) {
        if (mFrame != null && mMediaPlayer != null && !mDebugDisplay.quitRequested()) {
            mBottomLabel.setText(mDebugDisplay.computeLineInfo());

            boolean hasHighlight = false;
            for (VideoCanvas canvas : mVideoCanvas) {
                canvas.displayFrame();
                hasHighlight |= canvas.isHighlighted();
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
                if (mPlayerZoomEndTS < System.currentTimeMillis()) { // don't change too fast
                    /* if (tw != pw) {
                        tw = pw + (tw - pw) / 2;
                    }
                    if (th != ph) {
                        th = ph + (th - ph) / 2;
                    } */
                    mMediaPlayer.setBounds(0, 0, tw, th);
                    mMediaPlayer.revalidate();
                    mPlayerZoomEndTS = System.currentTimeMillis() + PLAYER_ZOOM_MIN_DURATION_MS;
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
            if (mMediaPlayer != null && !mDebugDisplay.quitRequested()) {
                mRepaintTimer.start();
                mMediaPlayer.mediaPlayer().audio().setMute(false);
                playNext();
            }
        });
    }

    private void playNext() {
        SwingUtilities.invokeLater(() -> {
            if (mMediaPlayer != null && !mDebugDisplay.quitRequested()) {
                Optional<File> next = mPlaylist.getNext();
                if (next.isPresent()) {
                    File file = next.get();
                    mLogger.log(TAG, "Player file = " + file.getAbsolutePath());

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
        /** Show highlight if > 0. Indicates when highlight should end. */
        private long mHighlightEndTS;
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
            return mHighlightEndTS > 0;
        }

        /** Must be invoked on the Swing UI thread. */
        public void displayFrame() {
            Frame frame;
            if (mDebugDisplay.isToggleMask()) {
                frame = mCamInfo.getAnalyzer().getLastFrame();
            } else {
                frame = mCamInfo.getGrabber().getLastFrame();
            }

            if (mCamInfo.getAnalyzer().isMotionDetected()) {
                mHighlightEndTS = System.currentTimeMillis() + HIGHLIGHT_DURATION_MS;
            } else if (mHighlightEndTS > 0 && mHighlightEndTS < System.currentTimeMillis()) {
                mHighlightEndTS = 0;
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
