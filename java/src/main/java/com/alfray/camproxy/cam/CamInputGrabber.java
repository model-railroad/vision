package com.alfray.camproxy.cam;

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
            CamInfo camInfo) {
        mFpsMeasurer = fpsMeasurer;
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
     * we have. The frames are cloned, and can be used in other threads independantly from the
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

        try {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(mCamInfo.getConfig().getInputUrl());
            grabber.setOption("stimeout" , "5000000"); // microseconds cf https://www.ffmpeg.org/ffmpeg-protocols.html#rtsp
            grabber.setTimeout(5*1000); // milliseconds
            grabber.start();
            mPixelFormat = grabber.getPixelFormat();
            mLogger.log(TAG, "Grabber started with video format " + mPixelFormat);

            // Note: Doc of grab() indicates it reuses the same Frame instance at every call
            // to avoid allocating memory. For an async/shared usage, it must be cloned first.
            Frame frame;

            while (!mQuit && (frame = grabber.grab()) != null) {
                mFpsMeasurer.tick();
                mFrameRate = grabber.getFrameRate();
                mLogger.log(TAG, "frame grabbed at " + grabber.getTimestamp()
                        + " -- " + ((int) (100*mFpsMeasurer.getFps())/100) + " fps"
                        + " vs " + grabber.getFrameRate() + " fps"
                        + ", size: "+ frame.imageWidth + "x" + frame.imageHeight
                        + ", image: " + (frame.image == null ? "NULL" : frame.image.length)
                        + "\r");

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
            grabber.stop();
            grabber.release();

        } catch (FrameGrabber.Exception e) {
            mLogger.log(TAG, e.toString());
        }
    }
}
