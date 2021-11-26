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

import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.utils.IClock;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.component.MediaPlayerSpecs;

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

    private KioskController.Callbacks mCallbacks;
    @GuardedBy("mVideoCanvas")
    private final List<VlcMediaComponent> mVideoCanvas = new ArrayList<>();
    private JFrame mFrame;
    private JLabel mBottomLabel;
    private EmbeddedMediaPlayerComponent mMediaPlayer;
    private Timer mRepaintTimer;
    private int mContentWidth;
    private int mContentHeight;

    @Inject
    public KioskView(ILogger logger, IClock clock) {
        mLogger = logger;
        mClock = clock;
    }

    public void invokeLater(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    public void create(
            int width, int height,
            int minWidth, int minHeight,
            int displayFps,
            Cameras cameras, HighlighterFactory highlighterFactory, String windowTitle,
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

        mMediaPlayer = new EmbeddedMediaPlayerComponent();
        mMediaPlayer.setBackground(BG_COLOR);
        mMediaPlayer.setBounds(0, 0, width, height); // matches initial frame
        mFrame.add(mMediaPlayer);

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

        mMediaPlayer.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                super.finished(mediaPlayer);
                mCallbacks.onMediaPlayerFinished();
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                super.error(mediaPlayer);
                mCallbacks.onMediaPlayerError();
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
        createVideoCanvases(cameras, highlighterFactory);
        mCallbacks.onFrameResized();
        if (maximize) {
            mFrame.setExtendedState(mFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }

        mRepaintTimer = new Timer(1000 / displayFps, this::onRepaintTimerTick);
    }

    public int getContentWidth() {
        return mContentWidth;
    }

    public int getContentHeight() {
        return mContentHeight;
    }

    public int getMediaPlayerWidth() {
        return mMediaPlayer.getWidth();
    }

    public int getMediaPlayerHeight() {
        return mMediaPlayer.getHeight();
    }

    public void setMediaPlayerSize(int width, int height) {
        mMediaPlayer.setBounds(0, 0, width, height);
        mMediaPlayer.revalidate();
    }

    private void createVideoCanvases(Cameras cameras, HighlighterFactory highlighterFactory) {
        AtomicInteger posIndex = new AtomicInteger();
        synchronized (mVideoCanvas) {
            cameras.forEachCamera(camInfo -> {
//                VideoCanvas canvas = new VideoCanvas(
//                        posIndex.incrementAndGet(),
//                        camInfo,
//                        highlighterFactory.create(
//                                camInfo.getIndex(),
//                                camInfo.getAnalyzer()));

                VlcMediaComponent canvas = new VlcMediaComponent(
                        posIndex.incrementAndGet(),
                        camInfo,
                        highlighterFactory.create(
                                camInfo.getIndex(),
                                camInfo.getAnalyzer()));

                mVideoCanvas.add(canvas);
                mFrame.add(canvas);
                canvas.initialize();
            });
        }
    }

    public void resizeVideoCanvases(int width, int height) {
        if (mFrame == null) {
            return;
        }
        synchronized (mVideoCanvas) {
            for (VlcMediaComponent canvas : mVideoCanvas) {
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
        if (mFrame == null || mMediaPlayer == null) {
            return;
        }
        mCallbacks.onRepaintTimerTick();
    }

    public void setBottomLabelText(String lineInfo) {
        mBottomLabel.setText(lineInfo);
    }

    public boolean getVideoCanvasesHighlight() {
        boolean hasHighlight = false;
        synchronized (mVideoCanvas) {
            for (VlcMediaComponent canvas : mVideoCanvas) {
                canvas.displayFrame();
                hasHighlight |= canvas.mHighlighter.isHighlighted();
            }
        }
        return hasHighlight;
    }

    public void release() {
        SwingUtilities.invokeLater(() -> {
            mRepaintTimer.stop();
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            synchronized (mVideoCanvas) {
                for (VlcMediaComponent canvas : mVideoCanvas) {
                    canvas.release();
                }
                mVideoCanvas.clear();
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

    public void setMediaPlayerMute(boolean isMuted) {
        if (mMediaPlayer != null) {
            mMediaPlayer.mediaPlayer().audio().setMute(isMuted);
        }
    }

    public void setMediaPlayerVolume(int percent) {
        if (mMediaPlayer != null) {
            mMediaPlayer.mediaPlayer().audio().setVolume(percent);
        }
    }

    public int getMediaPlayerVolume() {
        return mMediaPlayer == null ? -1 : mMediaPlayer.mediaPlayer().audio().volume();
    }

    public void playMediaPlayer(File media) {
        if (mMediaPlayer != null) {
            mMediaPlayer.mediaPlayer().media().play(media.getAbsolutePath());
        }
    }

    public void initPlayCanvasesHack(List<String> medias) {
        synchronized (mVideoCanvas) {
            int index = 0;
            for (VlcMediaComponent canvas : mVideoCanvas) {
                String media = medias.get(index);
                mLogger.log(TAG, "Play index " + index + " -> " + media);
                canvas.mediaPlayer().media().play(media);
                canvas.mediaPlayer().controls().setRepeat(true);
                canvas.mediaPlayer().overlay().enable(true);
                index = (index + 1) % medias.size();
            }
        }
    }

//    /**
//     * Based on org/bytedeco/javacv/CanvasFrame.java
//     */
//    private class VideoCanvas extends Canvas {
//        private static final boolean USE_BUFFERS = true;
//        private final Java2DFrameConverter mConverter = new Java2DFrameConverter();
//
//        private final int mPosIndex;
//        private final CamInfo mCamInfo;
//        private final Highlighter mHighlighter;
//        private final String mLiveText;
//        private Image mImage;
//        private int mHighlightLineSize = HIGHLIGHT_LINE_SIZE_MAX;
//        private int mLiveCircleRadius = HIGHLIGHT_LINE_SIZE_MAX;
//        private Font mLiveFont;
//
//        public VideoCanvas(int posIndex, CamInfo camInfo, Highlighter highlighter) {
//            mPosIndex = posIndex;
//            mCamInfo = camInfo;
//            mHighlighter = highlighter;
//            mLiveText = String.format(Locale.US, LIVE_TEXT, camInfo.getIndex());
//            setBackground(BG_COLOR);
//        }
//
//        public void initialize() {
//            setVisible(true);
//            if (USE_BUFFERS) {
//                createBufferStrategy(2);
//            }
//        }
//
//        public void release() {}
//
//        @Override
//        public void update(Graphics g) {
//            paint(g);
//        }
//
//        @Override
//        public void paint(Graphics g) {
//            if (mImage == null) {
//                super.paint(g);
//                return;
//            }
//
//            // Aspect ratio computation
//            // Canvas ratio cr = cw / ch.
//            // Image  ratio ir = iw / ih.
//            // If ir < cr, image is thinner, canvas is wider: fit H, resize W.
//            // If ir > cr, image is wider, canvas is thinner: fit W, resize H.
//            final int cw = getWidth();
//            final int ch = getHeight();
//            final int iw = mImage.getWidth(null /* observer */);
//            final int ih = mImage.getHeight(null /* observer */);
//            if (cw <= 0 || ch <= 0 || iw <= 0 || ih <= 0) {
//                return;
//            }
//            double cr = (double) cw / (double) ch;
//            double ir = (double) iw / (double) ih;
//            int dw, dh;
//            if (ir < cr) {
//                dh = ch;
//                dw = (int) (ch * ir);
//            } else {
//                dw = cw;
//                dh = (int) (cw / ir);
//            }
//            final int dx = (cw - dw) / 2;
//            final int dy = (ch - dh) / 2;
//
//            if (USE_BUFFERS) {
//                // Calling BufferStrategy.show() here sometimes throws
//                // NullPointerException or IllegalStateException,
//                // but otherwise seems to work fine.
//                try {
//
//                    BufferStrategy strategy = getBufferStrategy();
//                    do {
//                        do {
//                            g = strategy.getDrawGraphics();
//                            if (mImage != null) {
//                                drawImage(g, dw, dh, dx, dy);
//                                drawContour(g, dw, dh, dx, dy);
//                                drawLive(g, dw, dh, dx, dy);
//                            }
//                            g.dispose();
//                        } while (strategy.contentsRestored());
//                        strategy.show();
//                    } while (strategy.contentsLost());
//                } catch (NullPointerException | IllegalStateException ignored) {
//                }
//            } else {
//                drawImage(g, dw, dh, dx, dy);
//                drawContour(g, dw, dh, dx, dy);
//                drawLive(g, dw, dh, dx, dy);
//            }
//        }
//
//        private void drawImage(Graphics g, int dw, int dh, int dx, int dy) {
//            g.drawImage(mImage, dx, dy, dw, dh, null /* observer */);
//        }
//
//        private void drawContour(Graphics g, int dw, int dh, int dx, int dy) {
//            if (mHighlighter.isHighlighted()) {
//                g.setColor(HIGHLIGHT_LINE_COLOR);
//                g.fillRect(dx, dy, dw, mHighlightLineSize);
//                g.fillRect(dx, dy + dh - mHighlightLineSize, dw, mHighlightLineSize);
//                g.fillRect(dx, dy, mHighlightLineSize, dh);
//                g.fillRect(dx + dw - mHighlightLineSize, dy, mHighlightLineSize, dh);
//            }
//        }
//
//        private void drawLive(Graphics g, int dw, int dh, int dx, int dy) {
//            // Blink at 1 fps
//            long secondsNow = mClock.elapsedRealtime() / 1000;
//            if ((secondsNow & 0x1) == 0) return;
//
//            final int radius = mLiveCircleRadius;
//            final int diam = 2 * mLiveCircleRadius;
//            if (mLiveFont == null) {
//                mLiveFont = new Font("Arial", Font.BOLD | Font.ITALIC, diam);
//            }
//            g.setFont(mLiveFont);
//
//            g.setColor(LIVE_COLOR);
//
//            int x = dx + 2 * mHighlightLineSize + radius;
//            int y = dy + dh - 2 * mHighlightLineSize - radius;
//
//            g.fillOval(x - radius, y - radius, diam, diam);
//            g.drawString(mLiveText, x + diam, y + radius);
//        }
//
//        /**
//         * Must be invoked on the Swing UI thread.
//         */
//        public void displayFrame() {
//            Frame frame;
//            if (mCallbacks.showMask()) {
//                frame = mCamInfo.getAnalyzer().getLastFrame();
//            } else {
//                frame = mCamInfo.getGrabber().getLastFrame();
//            }
//
//            mHighlighter.update();
//
//            if (frame != null) {
//                mImage = mConverter.getBufferedImage(frame);
//                repaint();
//            }
//        }
//
//        public void computeAbsolutePosition(int frameW, int frameH) {
//            if (frameH <= 0 || frameW <= 0) {
//                return;
//            }
//
//            // Our videos are 16/9.
//            // If our videos are wider (our ratio > frame ratio), we want a horizontal gap between views.
//            // If our videos are taller (our ratio < frame ratio), we want a vertical gap between views.
//            final double refRatio = 16. / 9;
//            final double frameRatio = ((double) frameW) / frameH;
//            final int gapH = (refRatio >= frameRatio) ? VIEW_GAP_PX : 0;
//            final int gapV = (refRatio <= frameRatio) ? VIEW_GAP_PX : 0;
//
//            int x = mPosIndex % 2;
//            int y = mPosIndex / 2;
//            int w = frameW / 2;
//            int h = frameH / 2;
//            x *= w;
//            y *= h;
//            w -= gapH;
//            h -= gapV;
//            if (x > 0) {
//                x += gapH;
//            }
//            if (y > 0) {
//                y += gapV;
//            }
//
//            mHighlightLineSize = Math.max(HIGHLIGHT_LINE_SIZE_MIN, (int) Math.ceil((double) (HIGHLIGHT_LINE_SIZE_MAX * w) / (1980. / 2)));
//            mLiveCircleRadius = mHighlightLineSize;
//            mLiveFont = null;
//
//            this.setBounds(x, y, w, h);
//            // mLogger.log(TAG, String.format("   Video %d, cam%d = %dx%d [ %dx%d ]",
//            //        mPosIndex, mCamInfo.getIndex(), x, y, w, h));
//        }
//    }

    private class VlcMediaComponent extends EmbeddedMediaPlayerComponent {

        private final int mPosIndex;
        private final CamInfo mCamInfo;
        private final String mLiveText;
        private final Highlighter mHighlighter;
        private final VlcWrapperOverlay mOverlay;

        public VlcMediaComponent(int posIndex, CamInfo camInfo, Highlighter highlighter) {
            super();
            mPosIndex = posIndex;
            mCamInfo = camInfo;
            mLiveText = String.format(Locale.US, LIVE_TEXT, camInfo.getIndex());
            mHighlighter = highlighter;
            setBackground(BG_COLOR);
            mOverlay = new VlcWrapperOverlay(mFrame, camInfo, highlighter);
            this.mediaPlayer().overlay().set(mOverlay);
        }


        public void initialize() {
            setVisible(true);

            this.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void finished(MediaPlayer mediaPlayer) {
                    mLogger.log(TAG, "VLC for " + mLiveText + " EVENT Finished");
                    super.finished(mediaPlayer);
                }

                @Override
                public void error(MediaPlayer mediaPlayer) {
                    mLogger.log(TAG, "VLC for " + mLiveText + " EVENT Error");
                    super.error(mediaPlayer);
                }
            });
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
            // mLogger.log(TAG, String.format("   Video %d, cam%d = %dx%d [ %dx%d ]",
            //        mPosIndex, mCamInfo.getIndex(), x, y, w, h));
        }


        /**
         * Must be invoked on the Swing UI thread.
         */
        public void displayFrame() {
            mHighlighter.update();

            if (mCallbacks.showMask()) {
                Frame frame = mCamInfo.getAnalyzer().getLastFrame();
                if (frame != null) {
                    mOverlay.mImage = mOverlay.mConverter.getBufferedImage(frame);
                }
            } else if (mOverlay.mImage != null) {
                mOverlay.mImage = null;
            }
            mOverlay.repaint();
            mLogger.log(TAG, "DEBUG " + mPosIndex + " highlight=" + mHighlighter.isHighlighted() + " // showMask=" + mCallbacks.showMask() + " // image=" + mOverlay.mImage);
        }
    }

    private class VlcWrapperOverlay extends Window {
        private final Java2DFrameConverter mConverter = new Java2DFrameConverter();
        private final String mLiveText;
        private final Highlighter mHighlighter;
        public int mHighlightLineSize = HIGHLIGHT_LINE_SIZE_MAX;
        public int mLiveCircleRadius = HIGHLIGHT_LINE_SIZE_MAX;
        public Font mLiveFont;
        public Image mImage;

        public VlcWrapperOverlay(Window owner, CamInfo camInfo, Highlighter highlighter) {
            super(owner);
            mLiveText = String.format(Locale.US, LIVE_TEXT, camInfo.getIndex());
            mHighlighter = highlighter;
            setBackground(new Color(0, 0, 0, 0));
        }

        @Override
        public void update(Graphics g) {
            paint(g);
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);

            final int cw = getWidth();
            final int ch = getHeight();
            drawImage(g, cw, ch, 0, 0);
            drawContour(g, cw, ch, 0, 0);
            drawLive(g, cw, ch, 0, 0);
        }

        private void drawImage(Graphics g, int dw, int dh, int dx, int dy) {
            if (mImage != null) {
                g.drawImage(mImage, dx, dy, dw, dh, null /* observer */);
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
            mLogger.log(TAG, "DRAW TEXT " + mLiveText + " ? --> " + ((secondsNow & 0x1) == 0) );
            if ((secondsNow & 0x1) == 0) return;

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
            g.drawString(mLiveText, x + diam, y + radius);
        }

    }
}
