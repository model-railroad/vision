package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.FpsMeasurer;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import javax.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;

/**
 * Uses FFMpeg FFmpegFrameGrabber (via JavaCV) to grab frames from the source camera feed.
 * Currently supported: any URL that works for FFMpeg. E.g. RTSP with U/P and MJPEG or MP4.
 *
 * This "aggressively" reconnects as soon as the feed disconnects.
 */
@AutoFactory
public class CamInputGrabber extends ThreadLoop {
    private final DebugDisplay mDebugDisplay;
    private final String TAG;

    private final FpsMeasurer mFpsMeasurer;
    private final ILogger mLogger;
    private final CamInfo mCamInfo;
    private final AtomicReference<Frame> mLastFrame = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> mFrameLatch = new AtomicReference<>(new CountDownLatch(1));
    private double mFrameRate;
    private int mPixelFormat = AV_PIX_FMT_NONE;

    public CamInputGrabber(
            @Provided ILogger logger,
            @Provided FpsMeasurer fpsMeasurer,
            @Provided DebugDisplay debugDisplay,
            CamInfo camInfo) {
        mFpsMeasurer = fpsMeasurer;
        mDebugDisplay = debugDisplay;
        TAG = "CamIn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
    }

    /** Returns the frame rate from the FFMpeg frame grabber. */
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
    public Frame refreshAndGetFrame() {
        return refreshAndGetFrame(200, TimeUnit.MILLISECONDS); // 5 fps
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
        FFmpegFrameGrabber.tryLoad(); // must be initialized on main thread
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
        mFpsMeasurer.reset();

        final int key = mCamInfo.getIndex() * 2 - 1;
        final String info = "Cam" + mCamInfo.getIndex() + ": ";

        FFmpegFrameGrabber grabber = null;
        try {
            mDebugDisplay.updateLineInfo(key, info + "Connecting...");

            grabber = new FFmpegFrameGrabber(mCamInfo.getConfig().getInputUrl());
            grabber.setOption("stimeout" , "5000000"); // microseconds cf https://www.ffmpeg.org/ffmpeg-protocols.html#rtsp
            grabber.setTimeout(5*1000); // milliseconds
            grabber.start();
            mPixelFormat = grabber.getPixelFormat();
            mFrameRate = grabber.getFrameRate();
            mLogger.log(TAG, "Grabber started with video format " + mPixelFormat
                    + ", framerate " + mFrameRate + " fps"
                    + ", size " + grabber.getImageWidth() + "x" + grabber.getImageHeight());

            // Note: Doc of grab() indicates it reuses the same Frame instance at every call
            // to avoid allocating memory. For an async/shared usage, it must be cloned first.
            // When the codec looses the connection, it still returns the same frame, however
            // its timestamp is unchanged.
            Frame frame;
            long timestamp = -1;

            while (!mQuit && (frame = grabber.grabImage()) != null) {
                if (timestamp != frame.timestamp) {
                    mFpsMeasurer.tick();
                }

                mDebugDisplay.updateLineInfo(key, info + String.format("%6.1f fps", mFpsMeasurer.getFps()));
                timestamp = frame.timestamp;

                CountDownLatch latch = mFrameLatch.get();
                if (latch != null && latch.getCount() > 0) {
                    Frame clone = frame.clone();
                    synchronized (mFrameLatch) {
                        mLastFrame.set(clone);
                        latch.countDown();
                    }
                }
            }

            grabber.flush();

        } catch (FrameGrabber.Exception e) {
            mLogger.log(TAG, e.toString());
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
}
