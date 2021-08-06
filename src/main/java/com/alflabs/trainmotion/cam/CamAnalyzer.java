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

import com.alflabs.trainmotion.display.ConsoleTask;
import com.alflabs.trainmotion.util.FpsMeasurer;
import com.alflabs.trainmotion.util.FpsMeasurerFactory;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.ThreadLoop;
import com.alflabs.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.CvSize;
import org.bytedeco.opencv.opencv_core.IplImage;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_video.BackgroundSubtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.opencv.global.opencv_core.cvCreateImage;
import static org.bytedeco.opencv.global.opencv_imgproc.medianBlur;
import static org.bytedeco.opencv.global.opencv_video.createBackgroundSubtractorMOG2;

/**
 * Thread loop analyzing one camera input to detect motion in the video stream.
 * <p/>
 * Example:
 * https://opencv-python-tutroals.readthedocs.io/en/latest/py_tutorials/py_video/py_bg_subtraction/py_bg_subtraction.html
 */
@AutoFactory
public class CamAnalyzer extends ThreadLoop implements IMotionDetector {
    private final IClock mClock;
    private final ConsoleTask mConsoleTask;
    private final FpsMeasurerFactory mFpsMeasurerFactory;
    private final String TAG;

    // The analyzer does not need to run at the full input/output feed fps.
    private static final int ANALYZER_FPS = 10;

    private final ILogger mLogger;
    private final CamInfo mCamInfo;
    private final double mMotionThreshold;
    private final AtomicBoolean mMotionDetected = new AtomicBoolean();

    private CountDownLatch mCountDownLatch = new CountDownLatch(1);
    private OpenCVFrameConverter.ToMat mMatConverter;
    private BackgroundSubtractor mSubtractor;
    @SuppressWarnings("FieldCanBeLocal") // Must remain scoped as a field to keep allocated
    private IplImage mOutputImage;
    private Mat mOutput;

    CamAnalyzer(
            @Provided IClock clock,
            @Provided ILogger logger,
            @Provided ConsoleTask consoleTask,
            @Provided FpsMeasurerFactory fpsMeasurerFactory,
            CamInfo camInfo) {
        mClock = clock;
        mConsoleTask = consoleTask;
        mFpsMeasurerFactory = fpsMeasurerFactory;
        TAG = "CamAn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
        mMotionThreshold = camInfo.getConfig().getMotionThreshold();
    }

    @Override
    public boolean isMotionDetected() {
        return mMotionDetected.getAndSet(false);
    }

    /** Get a clone of the last output, if any. */
    @Nullable
    public Frame getLastFrame() {
        if (mOutput != null) {
            try {
                mCountDownLatch.await(2*1000/ANALYZER_FPS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignore) {}
            mCountDownLatch = new CountDownLatch(1);

            return mMatConverter.convert(mOutput).clone();
        }
        return null;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");

        // Most JavaCV objects must be allocated on the main thread
        // and after the dagger constructor.
        mMatConverter = new OpenCVFrameConverter.ToMat();

        // Defaults from https://docs.opencv.org/master/de/de1/group__video__motion.html
        // and same in org\bytedeco\opencv\global\opencv_video.java :
        int 	history = 500;           // default: 500
        double 	varThreshold = 16;      // default: 16
        boolean detectShadows = false;  // default: true
        mSubtractor = createBackgroundSubtractorMOG2(history, varThreshold, detectShadows);

        super.start();
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        super.stop();
    }

    @Override
    protected void _runInThreadLoop() {
        mLogger.log(TAG, "Thread loop begin");

        double targetFps = 0;
        final String key = String.format("%db", mCamInfo.getIndex());

        FpsMeasurer fpsMeasurer = mFpsMeasurerFactory.create();
        fpsMeasurer.setFrameRate(ANALYZER_FPS);
        long loopMs = fpsMeasurer.getLoopMs();
        long extraMs = -1;
        try {
            while (!mQuit) {
                fpsMeasurer.startTick();
                String info = "";
                Frame frame = mCamInfo.getGrabber().refreshAndGetFrame(loopMs, TimeUnit.MILLISECONDS);
                if (targetFps <= 0 && frame != null) {
                    // We only need to process frames at 1/2 or 1/3 the original
                    // so slow down if 1/3 is less than our default 10 fps value.
                    targetFps = Math.min(ANALYZER_FPS, (int)(mCamInfo.getGrabber().getFrameRate() / 3));
                    fpsMeasurer.setFrameRate(targetFps);
                    loopMs = fpsMeasurer.getLoopMs();
                }
                long computeMs = System.currentTimeMillis();
                if (frame != null) {
                    info = processFrame(frame);
                }

                computeMs = mClock.elapsedRealtime() - computeMs;
                mConsoleTask.updateLineInfo(key,
                        String.format(" %s [%2d%+4d ms]", info, computeMs, extraMs));

                extraMs = fpsMeasurer.endWait();
            }
        } finally {
            mSubtractor.close();
        }
    }

    @Nonnull
    private String processFrame(@Nonnull Frame frame) {
        Mat source = mMatConverter.convert(frame);
        if (source == null) return "";

        if (mOutput == null) {
            // TODO use Mat(Size, type=CV_8UC1).
            CvSize size = new CvSize().width(frame.imageWidth).height(frame.imageHeight);
            mOutputImage = cvCreateImage(size, 8, 1);
            mOutput = new Mat(mOutputImage);
        }

        // Apply background substractor
        mSubtractor.apply(source, mOutput);

        // Compute "score" for this output frame
        // int nz = opencv_core.countNonZero(mOutput);
        int npx = frame.imageWidth * frame.imageHeight;
        // double noisePercent1 = 100.0 * nz / npx;


        // Median blur for "salt & pepper" removal
        medianBlur(mOutput, mOutput, 5);
        int nz = opencv_core.countNonZero(mOutput);
        double noisePercent2 = 100.0 * nz / npx;

        mCountDownLatch.countDown();

        boolean hasMotion = noisePercent2 >= mMotionThreshold;
        mMotionDetected.set(hasMotion);

        return String.format("%s %.2f >= %.2f%%",
                hasMotion ? "/\\" : "..",
                noisePercent2,
                mMotionThreshold
                );
    }
}
