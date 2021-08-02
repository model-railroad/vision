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

package com.alfray.trainmotion.cam;

import com.alfray.libutils.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.opencv.core.CvType;

import javax.annotation.Nonnull;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;

@AutoFactory
public class FakeFrameGrabber implements IFrameGrabber {
    private static final double OUTPUT_ASPECT_RATIO = 16./9;

    private static final int WIDTH = 640;
    private static final int HEIGHT = (int)(WIDTH / OUTPUT_ASPECT_RATIO);
    private static final int SIZE = 64;
    private static final int FRAME_RATE_FPS = 10;
    public static final String PREFIX = "fake_srgb_";
    private final int mSpeedRgb;
    private final IClock mClock;
    @Nonnull private final String mInputUrl;
    private OpenCVFrameConverter.ToMat mMatConverter;
    private Mat mMat;
    private int mX, mY;

    FakeFrameGrabber(
            @Provided IClock clock,
            @Nonnull String inputUrl) {
        mClock = clock;
        mInputUrl = inputUrl;
        int srgb = 0x01ff00ff;
        if (inputUrl.startsWith(PREFIX)) {
            try {
                String hex = inputUrl.substring(PREFIX.length());
                srgb = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        mSpeedRgb = srgb;
    }

    @Override
    public void setOption(String key, String value) {
        // no-op
    }

    @Override
    public void setTimeout(int timeout) {
        // no-op
    }

    @Override
    public void start() throws FrameGrabber.Exception {
        mMatConverter = new OpenCVFrameConverter.ToMat();

        int r = (mSpeedRgb >> 16) & 0xFF;
        int g = (mSpeedRgb >> 8) & 0xFF;
        int b = mSpeedRgb & 0xFF;
        mMat = new Mat(new Size(WIDTH, HEIGHT), CvType.CV_8UC4, new Scalar(255, b, g, r));
    }

    @Override
    public int getPixelFormat() {
        return AV_PIX_FMT_BGR24;
    }

    @Override
    public double getFrameRate() {
        return FRAME_RATE_FPS;
    }

    @Override
    public int getImageWidth() {
        return WIDTH;
    }

    @Override
    public int getImageHeight() {
        return HEIGHT;
    }

    @Override
    public Frame grabImage() throws FrameGrabber.Exception {
        long startMs = mClock.elapsedRealtime();

        int speed = Math.max(1, (mSpeedRgb >> 24) & 0x7F);
        mX += speed;
        mY += speed / 2;
        int ratio2 = speed < 2 ? 2 : 1;
        if (mX > WIDTH / ratio2) {
            mY += speed;
            mX = 0;
        }
        if (mY > HEIGHT) {
            mY = 0;
        }
        int x = mX; // Math.max(0, Math.min(WIDTH - SIZE - 1, mX));
        int y = mY; // Math.max(0, Math.min(HEIGHT - SIZE - 1, mY));
        int sz = Math.max(2, ((x / ratio2) + y) % (SIZE * ratio2));
        int col = (x+y) & 0xFF;

        // Note: I don't know how to fill a portion of a sub-mat with an RGB color.
        // So instead clone the full-color one, and fill a a sub-rect with a gray value. Good enough.
        Mat clone = mMat.clone();
        if (x >= 0 && x <= WIDTH / 2 - SIZE - 1 && y >= 0 && y <= HEIGHT - SIZE - 1) {
            Mat subMat = new Mat(clone, new Rect(x, y, sz, sz));
            subMat.setTo(new Mat(1, 1, CvType.CV_8UC1, new Scalar(col)));
        }

        Frame frame = mMatConverter.convert(clone);
        long endMs = mClock.elapsedRealtime();
        long sleepMs = 1000 / FRAME_RATE_FPS - (endMs - startMs);
        if (sleepMs > 0) {
            mClock.sleep(sleepMs);
        }
        return frame;
    }

    @Override
    public void flush() throws FrameGrabber.Exception {
        // no-op
    }

    @Override
    public void stop() throws FrameGrabber.Exception {
        // no-op
    }

    @Override
    public void release() throws FrameGrabber.Exception {
        // no-op
    }
}
