package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.ILogger;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avformat.av_oformat_next;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ420P;

@Singleton
public class HttpCamHandler extends AbstractHandler {
    private static final String TAG = HttpCamHandler.class.getSimpleName();

    // TODO make framerate dynamic via URL, e.g. /mjpeg/cam#[/fps]
    private static final int MPJPEG_FRAME_RATE = 10;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final DebugDisplay mDebugDisplay;
    private final Java2DFrameConverter mFrameConverter;

    /** mpjpeg = multipart m-jpeg muxer/video codec: https://ffmpeg.org/doxygen/2.8/mpjpeg_8c.html */
    private AVOutputFormat mMPJpegCodec;

    @Inject
    public HttpCamHandler(
            ILogger logger,
            Cameras cameras,
            DebugDisplay debugDisplay) {
        mLogger = logger;
        mCameras = cameras;
        mDebugDisplay = debugDisplay;

        mFrameConverter = new Java2DFrameConverter(); // hangs when created in a thread
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        initMjpegCodec();
    }

    private void initMjpegCodec() throws FrameRecorder.Exception {
        FFmpegFrameRecorder.tryLoad(); // must be done outside of any threads
        AVOutputFormat oformat = null;
        do {
            oformat = av_oformat_next(oformat);
            if (oformat != null) {
                // For debugging:
                // mLogger.log(TAG, "Format name: " + oformat.name().getString() + "," + oformat.long_name().getString() + ", video-codec: " + oformat.video_codec());

                if ("mpjpeg".equals(oformat.name().getString())) {
                    mMPJpegCodec = new AVOutputFormat((oformat));
                    break;
                }
            }
        } while (oformat != null);
    }

    @Override
    public void handle(
            String path,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        // handle(target) (renamed "path" here) is /...everything till ? or #
        mLogger.log(TAG, "Handle path: " + path + ", request: " + baseRequest);

        boolean success = false;
        if (baseRequest.getMethod().equals("GET")) {
            success = doGet(path, response);
        }

        if (!success) {
            sendText(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
        }
        baseRequest.setHandled(true);
    }

    private final Pattern PATH_IMG_NUMBER = Pattern.compile("/img/([1-9])");
    private final Pattern PATH_MJPEG_NUMBER = Pattern.compile("/mjpeg/([1-9])");

    private boolean doGet(String path, HttpServletResponse response) throws IOException {
        if (path.equals("/qqq")) {
            sendText(response, HttpServletResponse.SC_OK, "Quitting");
            mDebugDisplay.requestQuit();
            return true;
        }

        Matcher m = PATH_IMG_NUMBER.matcher(path);
        if (m.matches()) {
            try {
                CamInfo cam = mCameras.getByIndex(Integer.parseInt(m.group(1)));
                return sendImage(cam, response);
            } catch (NumberFormatException e) {
                mLogger.log(TAG, "Invalid camera number: " + path);
            }
        }

        m = PATH_MJPEG_NUMBER.matcher(path);
        if (m.matches()) {
            try {
                CamInfo cam = mCameras.getByIndex(Integer.parseInt(m.group(1)));
                return sendMjpeg(cam, response);
            } catch (NumberFormatException e) {
                mLogger.log(TAG, "Invalid camera number: " + path);
            }
        }

        return false;
    }

    private boolean sendText(HttpServletResponse response, int code, String msg) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        response.setStatus(code);
        response.getWriter().println(msg);
        return true;
    }

    private boolean sendImage(CamInfo cam, HttpServletResponse response) throws IOException {
        if (cam == null) return false;

        Frame frame = cam.getGrabber().refreshAndGetFrame();
        if (frame == null) return false;

        BufferedImage image  = mFrameConverter.convert(frame);
        if (image == null) return false;

        response.setContentType("image/jpeg");
        response.addHeader("Cache-Control", "no-store");
        response.setStatus(HttpServletResponse.SC_OK);
        ServletOutputStream outputStream = response.getOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        outputStream.flush();
        return true;
    }

    private boolean sendMjpeg(CamInfo cam, HttpServletResponse response) throws IOException {
        if (cam == null) return false;

        // mpjpeg = multi-part jpeg
        if (mMPJpegCodec == null) {
            mLogger.log(TAG, "Multipart MJPEG codec not listed in FFMpeg output codec list.");
            return false;
        }

        // Only start with at least one frame to get the initial size.
        Frame frame = cam.getGrabber().refreshAndGetFrame();
        if (frame == null) return false;

        response.setContentType(mMPJpegCodec.mime_type().getString());
        response.addHeader("Cache-Control", "no-store");
        response.setStatus(HttpServletResponse.SC_OK);


        mLogger.log(TAG, "MJPEG: Streaming on " + response.getOutputStream());
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                response.getOutputStream(),
                frame.imageWidth,
                frame.imageHeight);
        recorder.setFormat(mMPJpegCodec.name().getString());
        recorder.setVideoCodec(mMPJpegCodec.video_codec());
        recorder.setPixelFormat(AV_PIX_FMT_YUVJ420P);

        double frameRate = cam.getGrabber().getFrameRate();
        if (frameRate <= 0) {
            frameRate = MPJPEG_FRAME_RATE;
        }
        recorder.setFrameRate(frameRate);

        recorder.start();

        try {
            while (!mDebugDisplay.quitRequested()) {
                frame = cam.getGrabber().refreshAndGetFrame();
                if (frame != null) {
                    recorder.record(frame);
                } else {
                    // Option: lack of frame. Wait or abort the feed. Choose the former right now.
                    // break;
                }
                Thread.sleep(1000 / MPJPEG_FRAME_RATE);
            }
        } catch (Exception e) {
            // Expected:
            // jetty.io.EofException when HTTP stream reader is closed.
            // InterruptedException from Thread.sleep
            mLogger.log(TAG, "MJPEG: " + e.toString());
        }

        mLogger.log(TAG, "MJPEG: End Streaming");
        recorder.stop();

        return true;
    }
}
