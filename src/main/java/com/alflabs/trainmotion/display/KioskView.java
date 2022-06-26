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

import com.alflabs.trainmotion.cam.CamAnalyzer;
import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.FpsMeasurer;
import com.alflabs.trainmotion.util.FpsMeasurerFactory;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.utils.IClock;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.component.callback.ScaledCallbackImagePainter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import javax.annotation.concurrent.GuardedBy;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_COLOR;
import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_SIZE_MAX;
import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_SIZE_MIN;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

/**
 * Kiosk Display is split in 2 parts: a KioskView class encapsulates all the Swing-related APIs,
 * and this controller contains all the "business" logic. This makes it possible to test the
 * controller using a mock UI that does not uses any actual views.
 */
@Singleton
public class KioskView {
    private static final String TAG = KioskView.class.getSimpleName();

    private static final Color BG_COLOR = Color.BLACK;
    private static final Color LIVE_COLOR = Color.RED;
    private static final String LIVE_TEXT = "LIVE CAM %d";
    private static final int VIEW_GAP_PX = 2;

    private final ILogger mLogger;
    private final IClock mClock;

    private final Cameras mCameras;
    private final ConsoleTask mConsoleTask;
    private final HighlighterFactory mHighlighterFactory;
    private final FpsMeasurerFactory mFpsMeasurerFactory;

    private KioskController.Callbacks mCallbacks;
    @GuardedBy("mVideoCanvas")
    private final List<VlcMediaComponent> mCameraPlayers = new ArrayList<>();
    private EmbeddedMediaPlayerComponent mMainPlayer;
    private JFrame mFrame;
    private JLabel mBottomLabel;
    private Timer mRepaintTimer;
    private int mContentWidth;
    private int mContentHeight;

    @Inject
    public KioskView(
            ILogger logger,
            IClock clock,
            Cameras cameras,
            ConsoleTask consoleTask,
            HighlighterFactory highlighterFactory,
            FpsMeasurerFactory fpsMeasurerFactory) {
        mLogger = logger;
        mClock = clock;
        mCameras = cameras;
        mConsoleTask = consoleTask;
        mHighlighterFactory = highlighterFactory;
        mFpsMeasurerFactory = fpsMeasurerFactory;
    }

    public void invokeLater(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    public void create(
            int width, int height,
            int minWidth, int minHeight,
            int displayFps,
            String windowTitle,
            boolean maximize,
            KioskController.Callbacks callbacks) throws Exception {
        mCallbacks = callbacks;

        mLogger.log(TAG, "Look and Feel: " + UIManager.getLookAndFeel().getName());

        mFrame = new JFrame(windowTitle);
        mFrame.setSize(width, height);
        mFrame.setMinimumSize(new Dimension(minWidth, minHeight));
        mFrame.setLayout(null);
        mFrame.setBackground(BG_COLOR);
        if (mFrame.getRootPane() != null && mFrame.getRootPane().getContentPane() != null) {
            mFrame.getRootPane().getContentPane().setBackground(BG_COLOR);
        }

        mMainPlayer = new EmbeddedMediaPlayerComponent();
        mMainPlayer.setBackground(BG_COLOR);
        mMainPlayer.setBounds(0, 0, width, height); // matches initial frame
        setupLogo();
        mFrame.add(mMainPlayer);

        mBottomLabel = new JLabel("Please wait, initializing camera streams...");
        mBottomLabel.setOpaque(true);
        mBottomLabel.setBackground(BG_COLOR);
        mBottomLabel.setForeground(Color.LIGHT_GRAY);
        mBottomLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        mFrame.add(mBottomLabel);

        mFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                mCallbacks.onWindowClosing();
            }
        });
        mFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                super.componentResized(event);
                mCallbacks.onFrameResized();
            }
        });
        mFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (mCallbacks.onProcessKey(keyEvent.getKeyChar())) {
                    keyEvent.consume();
                }
                super.keyPressed(keyEvent);
            }
        });

        mMainPlayer.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                super.finished(mediaPlayer);
                mCallbacks.onMainPlayerFinished();
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                super.error(mediaPlayer);
                mCallbacks.onMainPlayerError();
            }
        });

        // Create an "invalid" cursor to make the cursor transparent in the frame.
        try {
            Toolkit toolkit = mFrame.getToolkit();
            mFrame.setCursor(toolkit.createCustomCursor(toolkit.createImage(""), new Point(), "cursor"));
        } catch (Exception ignore) {
        }

        if (maximize) {
            // Remove window borders. Must be done before the setVisible call.
            mFrame.setUndecorated(true);
        }

        mFrame.setVisible(true);
        // Canvases use a "buffered strategy" (to have 2 buffers) and must be created
        // after the main frame is set visible.
        createVideoCanvases();
        mCallbacks.onFrameResized();
        if (maximize) {
            mFrame.setExtendedState(mFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }

        mRepaintTimer = new Timer(1000 / displayFps, this::onRepaintTimerTick);
    }

    private void setupLogo() {
        try {
            URL resource = Resources.getResource("logo_youtube_75pct.png");
            mLogger.log(TAG, "Logo read: " + resource.getPath());
            File tmpFile = File.createTempFile("logo_youtube_", ".png");
            mLogger.log(TAG, "Logo write: " + tmpFile);
            Files.write(Resources.toByteArray(resource), tmpFile);
            tmpFile.deleteOnExit();

            mMainPlayer.mediaPlayer().logo().setFile(tmpFile.getPath());
            mMainPlayer.mediaPlayer().logo().setLocation(0, Integer.MAX_VALUE);
            mMainPlayer.mediaPlayer().logo().setOpacity(0.75f);
            mMainPlayer.mediaPlayer().logo().enable(true);
        } catch (Throwable t) {
            mLogger.log(TAG, "Error setting logo: " + t.getMessage());
        }
    }

    public int getContentWidth() {
        return mContentWidth;
    }

    public int getContentHeight() {
        return mContentHeight;
    }

    public int getMediaPlayerWidth() {
        return mMainPlayer.getWidth();
    }

    public int getMediaPlayerHeight() {
        return mMainPlayer.getHeight();
    }

    public void setMediaPlayerSize(int width, int height) {
        mMainPlayer.setBounds(0, 0, width, height);
        mMainPlayer.revalidate();
    }

    private void createVideoCanvases() {
        AtomicInteger posIndex = new AtomicInteger();
        synchronized (mCameraPlayers) {
            mCameras.forEachCamera(camInfo -> {
                VlcMediaComponent canvas = new VlcMediaComponent(
                        posIndex.incrementAndGet(),
                        camInfo,
                        mHighlighterFactory.create(
                                camInfo.getIndex(),
                                camInfo.getAnalyzer()));

                mCameraPlayers.add(canvas);
                mFrame.add(canvas);
                canvas.initialize();
            });
        }
    }

    public void resizeVideoCanvases(int width, int height) {
        if (mFrame == null) {
            return;
        }
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                canvas.computeAbsolutePosition(width, height);
            }
        }
        mFrame.revalidate();
    }

    public void computeLayout() {
        if (mFrame != null) {
            Insets insets = mFrame.getInsets();
            mContentWidth = mFrame.getWidth() - insets.left - insets.right;
            mContentHeight = mFrame.getHeight() - insets.top - insets.bottom;

            Dimension labelSize = mBottomLabel.getPreferredSize();
            if (labelSize != null) {
                int lh = labelSize.height;
                mContentHeight -= lh;
                mBottomLabel.setBounds(0, mContentHeight, mContentWidth, lh);
            }
        }
    }

    private void onRepaintTimerTick(ActionEvent event) {
        if (mFrame == null || mMainPlayer == null) {
            return;
        }
        mCallbacks.onRepaintTimerTick();
    }

    public void setBottomLabelText(String lineInfo) {
        mBottomLabel.setText(lineInfo);
    }

    public boolean updateAllHighlights() {
        boolean hasHighlight = false;
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                canvas.updateFrame();
                hasHighlight |= canvas.mHighlighter.isHighlighted();
            }
        }
        return hasHighlight;
    }

    public void release() {
        SwingUtilities.invokeLater(() -> {
            mRepaintTimer.stop();
            if (mMainPlayer != null) {
                mMainPlayer.mediaPlayer().controls().stop();
                mMainPlayer.release();
                mMainPlayer = null;
            }
            synchronized (mCameraPlayers) {
                for (VlcMediaComponent canvas : mCameraPlayers) {
                    canvas.release();
                }
                mCameraPlayers.clear();
            }
            if (mFrame != null) {
                mFrame.dispose();
                mFrame = null;
            }
        });
    }

    public void startTimer() {
        // TODO does this really need SwingUtilities.invokeLater ?
        mRepaintTimer.start();
    }

    public void setMainPlayerMute(boolean isMuted) {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().audio().setMute(isMuted);
        }
    }

    public void setMainPlayerVolume(int percent) {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().audio().setVolume(percent);
        }
    }

    public int getMainPlayerVolume() {
        return mMainPlayer == null ? -1 : mMainPlayer.mediaPlayer().audio().volume();
    }

    public void startMainPlayer(File media) {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().media().play(media.getAbsolutePath());
        }
    }

    public void startCameraPlayer(CamInfo camInfo, String media) {
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                if (canvas.mCamInfo == camInfo) {
                    canvas.mPlayer.mediaPlayer().media().play(media);
                }
            }
        }
    }

    private class VlcMediaComponent extends JPanel {
        private final FpsMeasurer mFpsMeasurer;
        private final String mKey;
        private final int mPosIndex;
        private final CamInfo mCamInfo;
        private final Highlighter mHighlighter;
        private final VlcOverlayHelper mOverlay;
        private final CallbackMediaPlayerComponent mPlayer;
        private final VlcMediaComponent mVideoSurface;
        private final VlcRenderCallback mRenderCallback;
        private final ScaledCallbackImagePainter mImagePainter;
        private BufferedImage mImage;

        public VlcMediaComponent(int posIndex, CamInfo camInfo, Highlighter highlighter) {
            mPosIndex = posIndex;
            mCamInfo = camInfo;
            mHighlighter = highlighter;
            setBackground(BG_COLOR);
            mVideoSurface = this;
            mKey = String.format("%da", mCamInfo.getIndex());
            mFpsMeasurer = mFpsMeasurerFactory.create();

            mImagePainter = new ScaledCallbackImagePainter();
            mRenderCallback = new VlcRenderCallback();
            VlcBufferFormatCallback bufferFormatCallback = new VlcBufferFormatCallback();

            mPlayer = new CallbackMediaPlayerComponent(
                    null /* mediaPlayerFactory */,
                    null /* fullScreenFactory */,
                    null /* inputEvents */,
                    false /* lockBuffers */,
                    null /* ignore ScaledCallbackImagePainter() */,
                    mRenderCallback /* renderCallback */,
                    bufferFormatCallback /* bufferFormatCallback */,
                    mVideoSurface /* videoSurfaceComponent */
                    );

            mOverlay = new VlcOverlayHelper(camInfo, highlighter);
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D)g;

            int scaledW = getWidth();
            int scaledH = getHeight();
            BufferedImage image = mImage;
            if (image != null) {
                mImagePainter.prepare(g2, this);
                mImagePainter.paint(g2, this, image);

                scaledW = image.getWidth();
                scaledH = image.getHeight();
            }

            mOverlay.paint(g2, scaledW, scaledH);
        }

        public void initialize() {
            setVisible(true);

            mPlayer.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void finished(MediaPlayer mediaPlayer) {
                    super.finished(mediaPlayer);
                    mCallbacks.onCameraPlayerFinished(mCamInfo);
                }

                @Override
                public void error(MediaPlayer mediaPlayer) {
                    super.error(mediaPlayer);
                    mCallbacks.onCameraPlayerError(mCamInfo);
                }
            });
        }

        public void release() {
            mPlayer.mediaPlayer().controls().stop();
            mPlayer.release();
            mImage = null;
        }

        public void computeAbsolutePosition(int frameW, int frameH) {
            if (frameH <= 0 || frameW <= 0) {
                return;
            }

            // Our videos are 16/9.
            // If our videos are wider (our ratio > frame ratio), we want a horizontal gap between views.
            // If our videos are taller (our ratio < frame ratio), we want a vertical gap between views.
            final double refRatio = 16. / 9;
            final double frameRatio = ((double) frameW) / frameH;
            final int gapH = (refRatio >= frameRatio) ? VIEW_GAP_PX : 0;
            final int gapV = (refRatio <= frameRatio) ? VIEW_GAP_PX : 0;

            int x = mPosIndex % 2;
            int y = mPosIndex / 2;
            int w = frameW / 2;
            int h = frameH / 2;
            x *= w;
            y *= h;
            w -= gapH;
            h -= gapV;
            if (x > 0) {
                x += gapH;
            }
            if (y > 0) {
                y += gapV;
            }

            mOverlay.mHighlightLineSize = Math.max(HIGHLIGHT_LINE_SIZE_MIN, (int) Math.ceil((double) (HIGHLIGHT_LINE_SIZE_MAX * w) / (1980. / 2)));
            mOverlay.mLiveCircleRadius = mOverlay.mHighlightLineSize;
            mOverlay.mLiveFont = null;

            this.setBounds(x, y, w, h);
        }


        /**
         * Must be invoked on the Swing UI thread.
         */
        public void updateFrame() {
            mHighlighter.update();

            CamAnalyzer analyzer = mCamInfo.getAnalyzer();
            if (mCallbacks.showMask()) {
                Frame frame = analyzer.getMaskFrame();
                mOverlay.mNoiseLevel = analyzer.getNoiseLevel();
                if (frame != null) {
                    mOverlay.mMaskImage = mOverlay.mConverter.getBufferedImage(frame);
                }
            } else if (mOverlay.mMaskImage != null) {
                mOverlay.mMaskImage = null;
                mOverlay.mNoiseLevel = -1;
            }

            mConsoleTask.updateLineInfo(/* A */ mKey,
                    String.format(" [%d] %5.1f fps", mCamInfo.getIndex(), mFpsMeasurer.getFps()));
        }

        private void newVideoBuffer(int width, int height) {
            mImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            mRenderCallback.setImageBuffer(mImage);
            if (mVideoSurface != null) {
                mVideoSurface.setPreferredSize(new Dimension(width, height));
            }
        }

        private class VlcRenderCallback extends RenderCallbackAdapter {

            public VlcRenderCallback() {}

            private void setImageBuffer(BufferedImage image) {
                setBuffer(((DataBufferInt) image.getRaster().getDataBuffer()).getData());
            }

            @Override
            protected void onDisplay(MediaPlayer mediaPlayer, int[] buffer) {
                mFpsMeasurer.startTick();

                BufferedImage image = mImage;
                if (image != null) {
                    mCamInfo.getAnalyzer().offerPlayerImage(image);
                }
                mVideoSurface.repaint();
            }
        }

        private class VlcBufferFormatCallback extends BufferFormatCallbackAdapter {
            @Override
            public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
                newVideoBuffer(sourceWidth, sourceHeight);
                return new RV32BufferFormat(sourceWidth, sourceHeight);
            }
        }
    }

    private class VlcOverlayHelper {
        private final Java2DFrameConverter mConverter = new Java2DFrameConverter();
        private final String mLiveText;
        private final Highlighter mHighlighter;
        public int mHighlightLineSize = HIGHLIGHT_LINE_SIZE_MAX;
        public int mLiveCircleRadius = HIGHLIGHT_LINE_SIZE_MAX;
        public Font mLiveFont;
        public Image mMaskImage;
        public double mNoiseLevel = -1;

        public VlcOverlayHelper(CamInfo camInfo, Highlighter highlighter) {
            mLiveText = String.format(Locale.US, LIVE_TEXT, camInfo.getIndex());
            mHighlighter = highlighter;
        }

        public void paint(Graphics g, int cw, int ch) {
            drawImage(g, cw, ch, 0, 0);
            drawContour(g, cw, ch, 0, 0);
            drawLive(g, cw, ch, 0, 0);
        }

        private void drawImage(Graphics g, int dw, int dh, int dx, int dy) {
            if (mMaskImage != null) {
                g.drawImage(mMaskImage, dx, dy, dw, dh, null /* observer */);
            }
        }

        private void drawContour(Graphics g, int dw, int dh, int dx, int dy) {
            if (mHighlighter.isHighlighted()) {
                g.setColor(HIGHLIGHT_LINE_COLOR);
                g.fillRect(dx, dy, dw, mHighlightLineSize);
                g.fillRect(dx, dy + dh - mHighlightLineSize, dw, mHighlightLineSize);
                g.fillRect(dx, dy, mHighlightLineSize, dh);
                g.fillRect(dx + dw - mHighlightLineSize, dy, mHighlightLineSize, dh);
            }
        }

        private void drawLive(Graphics g, int dw, int dh, int dx, int dy) {
            // Blink at 1 fps
            long secondsNow = mClock.elapsedRealtime() / 1000;
            if (mNoiseLevel < 0 && (secondsNow & 0x1) == 0) return;

            final int radius = mLiveCircleRadius;
            final int diam = 2 * mLiveCircleRadius;
            if (mLiveFont == null) {
                mLiveFont = new Font("Arial", Font.BOLD | Font.ITALIC, diam);
            }
            g.setFont(mLiveFont);

            g.setColor(LIVE_COLOR);

            int x = dx + 2 * mHighlightLineSize + radius;
            int y = dy + dh - 2 * mHighlightLineSize - radius;

            g.fillOval(x - radius, y - radius, diam, diam);

            String s = mLiveText;
            if (mNoiseLevel >= 0) {
                s = String.format("%s      %.2f%%", s, mNoiseLevel);
            }

            g.drawString(s, x + diam, y + radius);
        }
    }
}
