package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.cam.CamAnalyzer;
import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.util.FpsMeasurer;
import com.alflabs.utils.IClock;
import org.bytedeco.javacv.Frame;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.player.component.callback.ScaledCallbackImagePainter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_SIZE_MAX;
import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_SIZE_MIN;

class VlcMediaComponent extends JPanel {
    private static final int VIEW_GAP_PX = 2;

    private final FpsMeasurer mFpsMeasurer;
    private final String mKey;
    private final KioskController.Callbacks mCallbacks;
    private final ConsoleTask mConsoleTask;
    private final int mPosIndex;
    private final CamInfo mCamInfo;
    private final Highlighter mHighlighter;
    private final VlcOverlayHelper mOverlay;
    private final CallbackMediaPlayerComponent mPlayer;
    private final VlcMediaComponent mVideoSurface;
    private final VlcRenderCallback mRenderCallback;
    private final ScaledCallbackImagePainter mImagePainter;
    private BufferedImage mImage;

    public VlcMediaComponent(
            IClock clock,
            KioskController.Callbacks callbacks,
            ConsoleTask consoleTask,
            int posIndex,
            CamInfo camInfo,
            FpsMeasurer fpsMeasurer,
            Highlighter highlighter) {
        mCallbacks = callbacks;
        mConsoleTask = consoleTask;
        mPosIndex = posIndex;
        mCamInfo = camInfo;
        mHighlighter = highlighter;
        setBackground(KioskView.BG_COLOR);
        mVideoSurface = this;
        mKey = String.format("%da", mCamInfo.getIndex());
        mFpsMeasurer = fpsMeasurer;

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

        mOverlay = new VlcOverlayHelper(clock, camInfo, highlighter);
    }

    public Highlighter getHighlighter() {
        return mHighlighter;
    }

    public CamInfo getCamInfo() {
        return mCamInfo;
    }

    public CallbackMediaPlayerComponent getPlayer() {
        return mPlayer;
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

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
                mOverlay.mMaskImage = mOverlay.getConverter().getBufferedImage(frame);
            }
        } else if (mOverlay.mMaskImage != null) {
            mOverlay.mMaskImage = null;
            mOverlay.mNoiseLevel = -1;
        }

        mConsoleTask.updateLineInfo(/* A */ mKey,
                new StringInfo(
                        String.format(" [%d] %5.1f fps", mCamInfo.getIndex(), mFpsMeasurer.getFps())));
    }

    private void newVideoBuffer(int width, int height) {
        mImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        mRenderCallback.setImageBuffer(mImage);
        if (mVideoSurface != null) {
            mVideoSurface.setPreferredSize(new Dimension(width, height));
        }
    }

    private class VlcRenderCallback extends RenderCallbackAdapter {

        public VlcRenderCallback() {
        }

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
