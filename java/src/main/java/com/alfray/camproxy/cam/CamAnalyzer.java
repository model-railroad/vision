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
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_video.BackgroundSubtractorMOG2;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.opencv.global.opencv_core.cvCreateImage;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_ELLIPSE;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_OPEN;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.morphologyEx;
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
    private CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private Frame mLastFrame;
    private double mFrameRate;
    private OpenCVFrameConverter.ToMat mMatConverter;
    private BackgroundSubtractorMOG2 mSubtractor;
    private IplImage mOutputImage;
    private Mat mOutput;
    private Mat mKernel;

    public CamAnalyzer(
            @Provided ILogger logger,
            @Provided DebugDisplay debugDisplay,
            CamInfo camInfo) {
        mDebugDisplay = debugDisplay;
        TAG = "CamAn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
    }

    /** Get a clone of the last output, if any. */
    @Nullable
    public Frame getLastFrame() {
        if (mOutput != null) {
            try {
                mCountDownLatch.await(500, TimeUnit.MILLISECONDS);
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

        mKernel = getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));

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

        final long sleepMs = 1000 / ANALYZER_FPS;

        try {
            while (!mQuit) {
                long startMs = System.currentTimeMillis();
                String info = "";
                Frame frame = mCamInfo.getGrabber().refreshAndGetFrame(sleepMs, TimeUnit.MILLISECONDS);
                long computeMs = System.currentTimeMillis();
                if (frame != null) {
                    info = processFrame(frame);
                }

                computeMs = System.currentTimeMillis() - computeMs;
                mDebugDisplay.updateLineInfo(2, String.format("Diff: %s [%d ms]", info, computeMs));

                long deltaMs = System.currentTimeMillis() - startMs;
                if (deltaMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
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
        int nz = opencv_core.countNonZero(mOutput);
        int npx = frame.imageWidth * frame.imageHeight;
        double noisePercent1 = 100.0 * nz / npx;

        // Remove noise on the output ==> very effective for us
//        morphologyEx(mOutput, mOutput, MORPH_OPEN, mKernel);
//        nz = opencv_core.countNonZero(mOutput);
        double noisePercent2 = 100.0 * nz / npx;

        // Median blur of "salf & pepper" removal
        medianBlur(mOutput, mOutput, 5);
        nz = opencv_core.countNonZero(mOutput);
        double noisePercent3 = 100.0 * nz / npx;

        mCountDownLatch.countDown();

        return String.format("%.2f %% > %.2f %% > %.2f %%",
                        noisePercent1,
                        noisePercent2,
                        noisePercent3);
    }
}
