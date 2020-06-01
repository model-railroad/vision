package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.FpsMeasurer;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<BufferedImage> mLastImage = new AtomicReference<>();
    private final AtomicReference<Frame> mLastFrame = new AtomicReference<>();

    public CamInputGrabber(
            @Provided ILogger logger,
            @Provided FpsMeasurer fpsMeasurer,
            CamInfo camInfo) {
        mFpsMeasurer = fpsMeasurer;
        TAG = "CamIn-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
    }

    public AtomicReference<BufferedImage> getLastImage() {
        return mLastImage;
    }

    public AtomicReference<Frame> getLastFrame() {
        return mLastFrame;
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
            // grabber.setPixelFormat(AV_PIX_FMT_RGB24);
            grabber.start();
            mLogger.log(TAG, "Grabber started");

            // Note: Doc of grab() indicates it reuses the same Frame instance at every call
            // to avoid allocating memory. For an async/shared usage, it must be cloned first.
            Frame frame;

            while (!mQuit && (frame = grabber.grab()) != null) {
                mFpsMeasurer.tick();
                mLogger.log(TAG, "frame grabbed at " + grabber.getTimestamp() + " -- " + mFpsMeasurer.getFps() + " fps"
                        + ", size: "+ frame.imageWidth + "x" + frame.imageHeight
                        + ", image: " + (frame.image == null ? "NULL" : frame.image.length));


                mLastFrame.set(frame.clone());
            }

            grabber.flush();
            grabber.stop();
            grabber.release();

        } catch (FrameGrabber.Exception e) {
            mLogger.log(TAG, e.toString());
        }
    }
}
