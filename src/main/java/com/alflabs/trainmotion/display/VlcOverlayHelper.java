package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.utils.IClock;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.util.Locale;

import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_COLOR;
import static com.alflabs.trainmotion.display.Highlighter.HIGHLIGHT_LINE_SIZE_MAX;

class VlcOverlayHelper {
    private static final String LIVE_TEXT = "LIVE CAM %d";
    private static final Color LIVE_COLOR = Color.RED;

    private final IClock mClock;
    private final Java2DFrameConverter mConverter = new Java2DFrameConverter();
    private final String mLiveText;
    private final Highlighter mHighlighter;
    public int mHighlightLineSize = HIGHLIGHT_LINE_SIZE_MAX;
    public int mLiveCircleRadius = HIGHLIGHT_LINE_SIZE_MAX;
    public Font mLiveFont;
    public Image mMaskImage;
    public double mNoiseLevel = -1;

    public VlcOverlayHelper(IClock clock, CamInfo camInfo, Highlighter highlighter) {
        mClock = clock;
        mLiveText = String.format(Locale.US, LIVE_TEXT, camInfo.getIndex());
        mHighlighter = highlighter;
    }

    public Java2DFrameConverter getConverter() {
        return mConverter;
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
