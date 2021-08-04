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

package com.alflabs.trainmotion.cam;

import com.alflabs.trainmotion.CommandLineArgs;
import com.alflabs.trainmotion.display.ConsoleTask;
import com.alflabs.trainmotion.util.FpsMeasurer;
import com.alflabs.trainmotion.util.FpsMeasurerFactory;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Strings;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;

import javax.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

/**
 * Thread loop to grab frames from the source camera feed.
 * <p/>
 * Uses FFMpeg FFmpegFrameGrabber (via JavaCV).
 * Currently supported: any URL that works for FFMpeg. E.g. RTSP or HTTP with U/P, and MJPEG or MP4.
 * <p/>
 * If the camera source URL starts with the {@link FakeFrameGrabber#PREFIX}, a
 * {@link FakeFrameGrabber} is used as the source instead of FFmpeg. This is useful for debugging.
 * <p/>
 * This thread loop "aggressively" reconnects as soon as the feed disconnects.
 */
@AutoFactory
public class CamInputGrabber extends ThreadLoop {

    private static final double OUTPUT_ASPECT_RATIO = 16./9;

    public static final int DEFAULT_WIDTH = 640;
    public static final int DEFAULT_HEIGHT = (int)(DEFAULT_WIDTH / OUTPUT_ASPECT_RATIO);

    private final ConsoleTask mConsoleTask;
    private final CommandLineArgs mCommandLineArgs;
    private final FpsMeasurerFactory mFpsMeasurerFactory;
    private final FakeFrameGrabberFactory mFakeFrameGrabberFactory;
    private final String TAG;

    private final ILogger mLogger;
    private final CamInfo mCamInfo;
    private final AtomicReference<Frame> mLastFrame = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> mFrameLatch = new AtomicReference<>(new CountDownLatch(1));
    private OpenCVFrameConverter.ToMat mMatConverter;
    private double mFrameRate;
    private int mPixelFormat = AV_PIX_FMT_NONE;

    CamInputGrabber(
            @Provided ILogger logger,
            @Provided ConsoleTask consoleTask,
            @Provided CommandLineArgs commandLineArgs,
            @Provided FpsMeasurerFactory fpsMeasurerFactory,
            @Provided FakeFrameGrabberFactory fakeFrameGrabberFactory,
            CamInfo camInfo) {
        mConsoleTask = consoleTask;
        mCommandLineArgs = commandLineArgs;
        mFpsMeasurerFactory = fpsMeasurerFactory;
        mFakeFrameGrabberFactory = fakeFrameGrabberFactory;
        TAG = "CamIn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
    }

    /** Returns the frame rate from the FFMpeg frame grabber or 0 if unknown. */
    public double getFrameRate() {
        return mFrameRate;
    }

    public int getPixelFormat() {
        return mPixelFormat;
    }

    /** Returns the last cached frame clone. */
    @Nullable
    public Frame getLastFrame() {
        return mLastFrame.get();
    }

    /** Refreshes and returns a frame, with a 200 ms deadline. */
    @Nullable
    public Frame refreshAndGetFrame(long deadlineMs) {
        return refreshAndGetFrame(deadlineMs, TimeUnit.MILLISECONDS); // 5 fps
    }

    /**
     * Refreshes and returns a frame. When the deadline expires, returns whatever previous frame
     * we have. The frames are cloned, and can be used in other threads independently from the
     * grabber thread. */
    @Nullable
    public Frame refreshAndGetFrame(long waitTime, TimeUnit timeUnit) {
        CountDownLatch latch = mFrameLatch.get();

        if (latch == null) {
            // Setting the latch requests a frame refresh.
            synchronized (mFrameLatch) {
                mFrameLatch.set(latch = new CountDownLatch(1));
            }
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            latch.await(waitTime, timeUnit);
        } catch (InterruptedException e) {
            // Option: Either return null or return the last frame we have? Do the latter for now.
            return mLastFrame.get();
        }

        synchronized (mFrameLatch) {
            mFrameLatch.set(null);
            return mLastFrame.get();
        }
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");

        // Most JavaCV objects must be allocated on the main thread
        // and after the dagger constructor.
        mMatConverter = new OpenCVFrameConverter.ToMat();
        FFmpegFrameGrabber.tryLoad(); // must be initialized on main thread

        super.start();
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        super.stop();
    }

    @Override
    protected void _runInThreadLoop() {
        boolean verboseLog = mCommandLineArgs.hasOption(CommandLineArgs.OPT_VERBOSE_LOG);
        if (verboseLog) {
            mLogger.log(TAG, "Thread loop begin");
        }

        final String key = String.format("%da", mCamInfo.getIndex());
        final String info = " | Cam" + mCamInfo.getIndex() + ": ";

        final int outputWidth = mCommandLineArgs.getIntOption(CommandLineArgs.OPT_SIZE_WIDTH, DEFAULT_WIDTH);
        final int outputHeight = (int) (outputWidth / OUTPUT_ASPECT_RATIO);
        final Size outputSize = new Size(outputWidth, outputHeight);

        IFrameGrabber grabber = null;
        boolean started = false;
        try {
            mConsoleTask.updateLineInfo(key, info + "Connecting...");

            String inputUrl = mCamInfo.getConfig().getInputUrl();
            if (!Strings.isNullOrEmpty(inputUrl) && inputUrl.startsWith(FakeFrameGrabber.PREFIX)) {
                grabber = mFakeFrameGrabberFactory.create(inputUrl);
            } else {
                grabber = FrameGrabberAdapter.of(new FFmpegFrameGrabber(inputUrl));
            }
            grabber.setOption("stimeout" , "5000000"); // microseconds cf https://www.ffmpeg.org/ffmpeg-protocols.html#rtsp
            grabber.setTimeout(5*1000); // milliseconds
            grabber.start();
            mPixelFormat = grabber.getPixelFormat();
            mFrameRate = grabber.getFrameRate();
            mLogger.log(TAG, "Grabber started with video format " + mPixelFormat
                    + ", framerate " + mFrameRate + " fps"
                    + ", size " + grabber.getImageWidth() + "x" + grabber.getImageHeight());

            final Rect sourceRect = computeSourceRect(grabber.getImageWidth(), grabber.getImageHeight(), OUTPUT_ASPECT_RATIO);

            // Note: Doc of grab() indicates it reuses the same Frame instance at every call
            // to avoid allocating memory. For an async/shared usage, it must be cloned first.
            // When the codec looses the connection, it still returns the same frame, however
            // its timestamp is unchanged.
            Frame frame;

            FpsMeasurer fpsMeasurer = mFpsMeasurerFactory.create();
            fpsMeasurer.setFrameRate(mFrameRate);
            started = true;

            while (!mQuit && (frame = grabber.grabImage()) != null) {
                fpsMeasurer.startTick();
                mConsoleTask.updateLineInfo(key,
                        String.format("%s%6.1f fps", info,fpsMeasurer.getFps()));

                CountDownLatch latch = mFrameLatch.get();
                if (latch != null && latch.getCount() > 0) {
                    Frame clone = cloneAndResize(frame, sourceRect, outputSize);
                    synchronized (mFrameLatch) {
                        mLastFrame.set(clone);
                        latch.countDown();
                    }
                }
            }

            grabber.flush();

        } catch (FrameGrabber.Exception e) {
            if (started) {
                mConsoleTask.updateLineInfo(key, info + "Error");
            }
            if (verboseLog) {
                mLogger.log(TAG, e.toString());
            }
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (FrameGrabber.Exception ignore) {}
                try {
                    grabber.release();
                } catch (FrameGrabber.Exception ignore) {}
            }
        }
    }

    private Rect computeSourceRect(int srcW, int srcH, double destAspectRatio) {
        double srcAspectRatio = (double) srcW / (double) srcH;

        if (srcAspectRatio <= destAspectRatio) {
            // Source is narrow, dest is wide ==> use full source width
            int dstH = (int) (srcW / destAspectRatio);
            int offsetY = (srcH - dstH) / 2;
            return new Rect(0, offsetY, srcW, dstH);
        } else {
            // Source is wide, dest is narrow ==> use full source height
            int dstW = (int) (srcH / destAspectRatio);
            int offsetX = (srcW - dstW) / 2;
            return new Rect(offsetX, 0, dstW, srcH);
        }
    }

    private Frame cloneAndResize(Frame frame, Rect sourceRect, Size destSize) {
        Mat source = mMatConverter.convert(frame).apply(sourceRect);
        Mat dest = new Mat();
        resize(source, dest, destSize, 1, 1, INTER_AREA);
        return mMatConverter.convert(dest);
    }
}
