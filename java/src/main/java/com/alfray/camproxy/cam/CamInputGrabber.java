package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.FpsMeasurer;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

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

    public AtomicReference<Frame> getLastFrame() {
        return mLastFrame;
    }

    @Override
    public void start() {
        mLogger.log(TAG, "Start");
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
            grabber.start();

            Frame frame;
            while (!mQuit && (frame = grabber.grab()) != null) {
                mFpsMeasurer.tick();
                mLogger.log(TAG, "frame grabbed at " + grabber.getTimestamp() + " -- " + mFpsMeasurer.getFps() + " fps");
                mLastFrame.set(frame);
            }
        } catch (FrameGrabber.Exception e) {
            mLogger.log(TAG, e.toString());
        }
    }
}
