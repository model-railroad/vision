package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.ThreadLoop;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * Several options here:
 * 1- Configure a single FFServer instance and stream to that instance, one per camera.
 *    See https://trac.ffmpeg.org/wiki/ffserver
 *    and FFMPEGFrameRecorder example https://github.com/bytedeco/javacv/issues/410
 *
 * 2- Generate JPEGs on-demand on the last input frame, and serve them via a socket or local
 *    web server. This would be easier to start, and would be ideal for debugging too.
 *
 * 3- Use FFMPEGFrameRecorder to generate an MPJPEG stream, and serve it either via a socket
 *    or a local web server.
 */
@AutoFactory
public class CamOutputGenerator extends ThreadLoop {
    private final DebugDisplay mDebugDisplay;
    private final String TAG;
    private final ILogger mLogger;
    private final CamInfo mCamInfo;
    private final Java2DFrameConverter mFrameConverter;

    public CamOutputGenerator(
            @Provided ILogger logger,
            @Provided DebugDisplay debugDisplay,
            CamInfo camInfo) {
        mDebugDisplay = debugDisplay;
        TAG = "CamOut-" + camInfo.getIndex();
        mLogger = logger;
        mCamInfo = camInfo;
        mFrameConverter = new Java2DFrameConverter(); // hangs when created in a thread
    }

    @Override
    public void start() throws Exception {
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

        try {
            if (generateJpeg()) {
                Thread.sleep(3600 * 1000);
            } else {
                Thread.sleep(5 * 1000);
            }
        } catch (InterruptedException e) {
            mLogger.log(TAG, "Interrupted");
        }
    }

    private boolean generateJpeg() {
        Frame frame = mCamInfo.getGrabber().getLastFrame().get();
        if (frame == null) {
            return false;
        }

        try {
            mLogger.log(TAG, "[JPEG] frame: " + frame);
            mDebugDisplay.queue(frame);

            BufferedImage image  = mFrameConverter.convert(frame);

            mLogger.log(TAG, "[JPEG] image: " + image);

            File file = new File("E:\\Temp\\temp\\1.jpg");
            ImageIO.write(image, "jpg", file);
            mLogger.log(TAG, "JPEG generated: " + file.getPath());
            mDebugDisplay.requestQuit();
            return true;
        } catch (Exception e) {
            mLogger.log(TAG, e.toString());
        }

        return false;
    }
}
