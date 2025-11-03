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

import com.alflabs.trainmotion.ConfigIni;
import com.alflabs.trainmotion.display.ConsoleTask;
import com.alflabs.trainmotion.display.StringInfo;
import com.alflabs.trainmotion.util.FpsMeasurer;
import com.alflabs.trainmotion.util.FpsMeasurerFactory;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.StatsCollector;
import com.alflabs.trainmotion.util.ThreadLoop;
import com.alflabs.utils.IClock;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.CvSize;
import org.bytedeco.opencv.opencv_core.IplImage;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_video.BackgroundSubtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
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
    /// U+23FA Black Circle For Record Unicode Character
    private static final String STR_CAM_ACTIVE = "⏺";
    /// U+233D APL FUNCTIONAL SYMBOL CIRCLE STILE
    private static final String STR_CAM_INACTIVE = "⌽";
    private final IClock mClock;
    private final ConfigIni mConfigIni;
    private final ConsoleTask mConsoleTask;
    private final StatsCollector mStatsCollector;
    private final FpsMeasurerFactory mFpsMeasurerFactory;
    private final String TAG;

    // The analyzer does not need to run at the full input/output feed fps.
    private static final int ANALYZER_FPS = 5;
    // Delta threshold used to remove spikes
    private static final double NOISE_SPIKE_DELTA_DEFAULT = 10;


    private final ILogger mLogger;
    private final CamInfo mCamInfo;
    private final double mMotionThreshold;
    private final AtomicBoolean mMotionDetected = new AtomicBoolean();
    private final BlockingDeque<Frame> mPlayerFrameQueue = new LinkedBlockingDeque<>(1);
    private final BlockingDeque<Frame> mMaskFrameQueue = new LinkedBlockingDeque<>(1);
    private final double[] mNoiseBuffer = new double[10];

    private OpenCVFrameConverter.ToMat mMatConverter;
    private Java2DFrameConverter mBufImageConverter;
    private BackgroundSubtractor mSubtractor;
    @SuppressWarnings("FieldCanBeLocal") // Must remain scoped as a field to keep allocated
    private IplImage mOutputImage;
    private Mat mOutput;
    private double mLastNoisePercent;
    private int mNoiseBufferIndex;
    private double mNoiseSpikeThreshold;
    private double mNoiseAverage;
    private String mKey;
    private FpsMeasurer mFpsMeasurer;

    CamAnalyzer(
            @Provided IClock clock,
            @Provided ILogger logger,
            @Provided ConfigIni configIni,
            @Provided ConsoleTask consoleTask,
            @Provided StatsCollector statsCollector,
            @Provided FpsMeasurerFactory fpsMeasurerFactory,
            CamInfo camInfo) {
        mClock = clock;
        mConfigIni = configIni;
        mConsoleTask = consoleTask;
        mStatsCollector = statsCollector;
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

    public double getNoiseLevel() {
        return mNoiseAverage;
    }

    /**
     * Get a clone of the last output of the analyzer (aka the mask frame), if any.
     * Note that this is only used to displaying the mask for informational/debug purposes.
     */
    @Nullable
    public Frame getMaskFrame() {
        Frame frame = null;
        try {
            frame = mMaskFrameQueue.poll(2 * 1000 / ANALYZER_FPS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignore) {}

        return frame;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");

        mNoiseSpikeThreshold = mConfigIni.getSpikeThreshold(NOISE_SPIKE_DELTA_DEFAULT);

        // Most JavaCV objects must be allocated on the main thread
        // and after the dagger constructor.
        mMatConverter = new OpenCVFrameConverter.ToMat();
        mBufImageConverter = new Java2DFrameConverter();

        // Defaults from https://docs.opencv.org/master/de/de1/group__video__motion.html
        // and same in org\bytedeco\opencv\global\opencv_video.java :
        int 	history = 500;           // default: 500
        double 	varThreshold = 16;      // default: 16
        boolean detectShadows = false;  // default: true
        mSubtractor = createBackgroundSubtractorMOG2(history, varThreshold, detectShadows);

        super.start("Thread-" + TAG);
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        super.stop();
    }

    public void offerPlayerImage(BufferedImage image) {
        if (mPlayerFrameQueue.isEmpty()) {
            Frame frame = mBufImageConverter.convert(image);
            mPlayerFrameQueue.offer(frame);
        }
    }

    @Override
    protected void _beforeThreadLoop() {
        mLogger.log(TAG, "Thread loop begin");
        mKey = String.format("%db", mCamInfo.getIndex());
        mFpsMeasurer = mFpsMeasurerFactory.create();
        mFpsMeasurer.setFrameRate(ANALYZER_FPS);
    }

    @Override
    protected void _runInThreadLoop() {
        final long loopMs = mFpsMeasurer.getLoopMs();

        mFpsMeasurer.startTick();
        StringInfo info = StringInfo.EMPTY;

        Frame frame = null;
        try {
            frame = mPlayerFrameQueue.poll(loopMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}

        if (mQuit) {
            return;
        }

        long computeMs = System.currentTimeMillis();
        if (frame != null) {
            info = processFrame(frame);
        }

        computeMs = mClock.elapsedRealtime() - computeMs;
        mConsoleTask.updateLineInfo(/* B */ mKey,
                info.withMsg(String.format(" %s [%2d ms]", info.mMsg, computeMs)));

        mFpsMeasurer.endWait();
    }

    @Override
    protected void _afterThreadLoop() {
        mLogger.log(TAG, "Loop end");
        mSubtractor.close();
    }

    @Nonnull
    private StringInfo processFrame(@Nonnull Frame frame) {
        Mat source = mMatConverter.convert(frame);
        if (source == null) return StringInfo.EMPTY;

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

        // Instant noise, unfiltered.
        // Compute the delta with the last measurement.
        // If larger than the spike threshold, ignore it.
        double deltaPercent = noisePercent2 - mLastNoisePercent;
        mLastNoisePercent = noisePercent2;

        double average;
        boolean hasMotion;
        if (deltaPercent < mNoiseSpikeThreshold) {
            // Filter noise with a 10-sample average window
            int index = mNoiseBufferIndex;
            mNoiseBuffer[index] = noisePercent2;
            int windowLen = mNoiseBuffer.length;
            mNoiseBufferIndex = (index + 1) % windowLen;
            average = 0;
            for (int i = 0; i < windowLen; i++) {
                average += mNoiseBuffer[i];
            }
            average /= windowLen;
            mNoiseAverage = average;

            hasMotion = average >= mMotionThreshold;
            mMotionDetected.set(hasMotion);
        } else {
            average = mNoiseAverage;
            hasMotion = mMotionDetected.get();
        }

        mStatsCollector.collect(mCamInfo.getIndex(), noisePercent2, average, hasMotion);

        if (mMaskFrameQueue.isEmpty()) {
            // Prepare the next frame for the mask display, if one is requested.
            Frame maskFrame = mMatConverter.convert(mOutput).clone();
            mMaskFrameQueue.offer(maskFrame);
        }

        return new StringInfo(
                String.format("%s %5.2f >= %.2f%%",
                hasMotion ? STR_CAM_ACTIVE : STR_CAM_INACTIVE,
                noisePercent2,
                mMotionThreshold
                ),
                hasMotion ? StringInfo.Flag.Active : StringInfo.Flag.Default);
    }
}
