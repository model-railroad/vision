package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
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
 * Example:
 * https://opencv-python-tutroals.readthedocs.io/en/latest/py_tutorials/py_video/py_bg_subtraction/py_bg_subtraction.html
 */
@AutoFactory
public class CamAnalyzer extends ThreadLoop {
    private final DebugDisplay mDebugDisplay;
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
    private IplImage mOutputImage;
    private Mat mOutput;

    public CamAnalyzer(
            @Provided ILogger logger,
            @Provided DebugDisplay debugDisplay,
            CamInfo camInfo) {
        mDebugDisplay = debugDisplay;
        TAG = "CamAn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
        mMotionThreshold = camInfo.getConfig().getMotionThreshold();
    }

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
    public void stop() throws InterruptedException {
        mLogger.log(TAG, "Stop");
        super.stop();
    }

    @Override
    protected void _runInThreadLoop() {
        mLogger.log(TAG, "Thread loop begin");

        double targetFps = 0;
        long sleepMs = 1000 / ANALYZER_FPS;
        final String key = String.format("%db", mCamInfo.getIndex());

        try {
            while (!mQuit) {
                long startMs = System.currentTimeMillis();
                String info = "";
                Frame frame = mCamInfo.getGrabber().refreshAndGetFrame(sleepMs, TimeUnit.MILLISECONDS);
                long computeMs = System.currentTimeMillis();
                if (targetFps <= 0) {
                    // We only need to process frames at 1/2 or 1/3 the original
                    // so slow down if 1/3 is less than our default 10 fps value.
                    targetFps = (int)(mCamInfo.getGrabber().getFrameRate() / 3);
                    if (targetFps > 0 && targetFps < ANALYZER_FPS) {
                        sleepMs = (long) (1000 / targetFps);
                    }
                }
                if (frame != null) {
                    info = processFrame(frame);
                }

                computeMs = System.currentTimeMillis() - computeMs;
                mDebugDisplay.updateLineInfo(key, String.format(" %s [%d ms]", info, computeMs));

                long deltaMs = System.currentTimeMillis() - startMs;
                deltaMs = sleepMs - deltaMs;
                if (deltaMs > 0) {
                    try {
                        Thread.sleep(deltaMs);
                    } catch (InterruptedException e) {
                        mLogger.log(TAG, e.toString());
                    }
                }
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
