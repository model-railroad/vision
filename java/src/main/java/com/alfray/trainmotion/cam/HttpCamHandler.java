package com.alfray.trainmotion.cam;

import com.alfray.trainmotion.util.DebugDisplay;
import com.alfray.trainmotion.util.FpsMeasurer;
import com.alfray.trainmotion.util.ILogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.javacpp.BytePointer;
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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avformat.av_oformat_next;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_INFO;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_WARNING;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ420P;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

@Singleton
public class HttpCamHandler extends AbstractHandler {
    private static final String TAG = HttpCamHandler.class.getSimpleName();

    // TODO make framerate dynamic via URL, e.g. /mjpeg/cam#[/fps]
    private static final int MPJPEG_FRAME_RATE = 15;
    private static final int H264_FRAME_RATE = 15;

    private final ILogger mLogger;
    private final Cameras mCameras;
    private final DebugDisplay mDebugDisplay;
    private final ObjectMapper mJsonMapper;

    private Java2DFrameConverter mFrameConverter;

    /** mpjpeg = multipart m-jpeg muxer/video codec: https://ffmpeg.org/doxygen/2.8/mpjpeg_8c.html */
    private AVOutputFormat mMPJpegCodec;
    private AVOutputFormat mMp4Codec;
    private AVOutputFormat mH264Codec;

    @Inject
    public HttpCamHandler(
            ILogger logger,
            Cameras cameras,
            ObjectMapper jsonMapper,
            DebugDisplay debugDisplay) {
        mLogger = logger;
        mCameras = cameras;
        mJsonMapper = jsonMapper;
        mDebugDisplay = debugDisplay;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // Most JavaCV objects must be allocated on the main thread
        // and after the dagger constructor.
        mFrameConverter = new Java2DFrameConverter(); // hangs when created in a thread
        initCodecs();
    }

    private void initCodecs() throws FrameRecorder.Exception {
        FFmpegFrameRecorder.tryLoad(); // must be done outside of any threads
        AVOutputFormat oformat = null;
        do {
            oformat = av_oformat_next(oformat);
            if (oformat != null) {
                // For debugging:
                // mLogger.log(TAG, "Format name: " + _toString(oformat.name()) + "," + _toString(oformat.long_name()) + ", video-codec: " + oformat.video_codec());

                if ("mpjpeg".equals(_toString(oformat.name()))) {
                    mMPJpegCodec = new AVOutputFormat((oformat));
                    mLogger.log(TAG, String.format("Codec found: %s (%s) %s",
                            _toString(oformat.name()),
                            _toString(oformat.long_name()),
                            _toString(oformat.mime_type())));
                }
                if ("h264".equals(_toString(oformat.name()))) {
                    mH264Codec = new AVOutputFormat((oformat));
                    mLogger.log(TAG, String.format("Codec found: %s (%s) %s",
                            _toString(oformat.name()),
                            _toString(oformat.long_name()),
                            _toString(oformat.mime_type())));
                }
                if ("mp4".equals(_toString(oformat.name()))) {
                    mMp4Codec = new AVOutputFormat((oformat));
                    mLogger.log(TAG, String.format("Codec found: %s (%s) %s",
                            _toString(oformat.name()),
                            _toString(oformat.long_name()),
                            _toString(oformat.mime_type())));
                }
            }
        } while (oformat != null);
    }

    private static String _toString(BytePointer str) {
        if (str != null) {
            return str.getString();
        }
        return null;
    }

    @Override
    public void handle(
            String path,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        // handle(target) (renamed "path" here) is /...everything till ? or #

        boolean success = false;
        if (baseRequest.getMethod().equals("GET")) {
            success = doGet(path, response);
        }

        if (!success) {
            mLogger.log(TAG, "Not Handled: path: " + path + ", request: " + baseRequest);
        }

        baseRequest.setHandled(success);
    }

    private final Pattern PATH_IMG_NUMBER = Pattern.compile("/img/([1-9])");
    private final Pattern PATH_MJPEG_NUMBER = Pattern.compile("/mjpeg/([1-9])");
    private final Pattern PATH_H264_NUMBER = Pattern.compile("/h264/([1-9])");

    private boolean doGet(String path, HttpServletResponse response) throws IOException {
        if (path.equals("/qqq")) {
            sendText(response, HttpServletResponse.SC_OK, "Quitting");
            mDebugDisplay.requestQuit();
            return true;
        }

        if (path.equals("/status")) {
            sendStatus(response);
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

        m = PATH_H264_NUMBER.matcher(path);
        if (m.matches()) {
            try {
                CamInfo cam = mCameras.getByIndex(Integer.parseInt(m.group(1)));
                return sendMp4(cam, response);
            } catch (NumberFormatException e) {
                mLogger.log(TAG, "Invalid camera number: " + path);
            }
        }

        return false;
    }

    private void sendStatus(HttpServletResponse response) throws IOException {
        Map<String, Boolean> status = new TreeMap<>();

        mCameras.forEachCamera(cam -> {
            status.put("cam" + cam.getIndex(), cam.getAnalyzer().isMotionDetected());
        });

        String text = mJsonMapper.writeValueAsString(status).replaceAll("\r", "");
        sendText(response, HttpServletResponse.SC_OK, text);
    }

    private boolean sendText(HttpServletResponse response, int code, String msg) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        response.setStatus(code);
        response.getWriter().println(msg);
        return true;
    }

    private boolean sendImage(CamInfo cam, HttpServletResponse response) throws IOException {
        if (cam == null) return false;

        Frame frame = cam.getGrabber().refreshAndGetFrame(200 /*ms*/);
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
        // Note: Always returns a feed. If there's no real camera or it has no data yet,
        // returns a feed with only mEmptyFrame till we have actual data.

        // mpjpeg = multi-part jpeg
        if (mMPJpegCodec == null) {
            mLogger.log(TAG, "Multipart MJPEG codec not listed in FFMpeg output codec list.");
            return false;
        }

        final String key = String.format("%dc", cam == null ? 0 : cam.getIndex());

        // Only start with at least one frame to get the initial size.
        Frame frame = null;
        if (cam != null) {
            frame = cam.getGrabber().refreshAndGetFrame(500 /*ms*/);
        }

        int frameWidth = frame != null ? frame.imageWidth : CamInputGrabber.DEFAULT_WIDTH;
        int frameHeight = frame != null ? frame.imageHeight : CamInputGrabber.DEFAULT_HEIGHT;
        Frame useFrame = createFrame(frameWidth, frameHeight);

        response.setContentType(_toString(mMPJpegCodec.mime_type()));
        response.addHeader("Cache-Control", "no-store");
        response.setStatus(HttpServletResponse.SC_OK);

        mLogger.log(TAG, "MJPEG: Streaming on " + response.getOutputStream());

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                response.getOutputStream(),
                frameWidth,
                frameHeight);
        try {
            recorder.setFormat(_toString(mMPJpegCodec.name()));
            recorder.setVideoCodec(mMPJpegCodec.video_codec());
            recorder.setPixelFormat(AV_PIX_FMT_YUVJ420P); // expected for MJPEG
            recorder.setVideoQuality(3);

            // Known issue: logs shows
            // "[swscaler ...] deprecated pixel format used, make sure you did set range correctly"
            // emitted in libswcaler/utils.c when jpeg srcFormat != jpeg dstFormat
            // https://ffmpeg.org/doxygen/3.1/libswscale_2utils_8c_source.html  -- line 1179.
            // https://ffmpeg.org/doxygen/2.8/log_8c_source.html -- line 358 for av_log().
            // It seems a new swscaler init is done at very frame in record() below, even when passing
            // the grabber pixel format.
            // It logs with level AV_LOG_WARNING. A workaround is to change level to ERROR.
            // (can't do it just when recording the frame as there will be other threads
            // serving the other cameras at the same time, and we don't want to sync that block).
            av_log_set_level(AV_LOG_ERROR);

            double frameRate = MPJPEG_FRAME_RATE;
            int grabberPxlFmt = AV_PIX_FMT_NONE;
            if (cam != null) {
                frameRate = cam.getGrabber().getFrameRate();
                grabberPxlFmt = cam.getGrabber().getPixelFormat();
            }
            if (frameRate <= 0) {
                frameRate = MPJPEG_FRAME_RATE;
            }
            recorder.setFrameRate(frameRate);

            recorder.start();

            final long sleepMs = (long) (1000 / frameRate);
            FpsMeasurer fpsMeasurer = new FpsMeasurer();
            fpsMeasurer.setFrameRate(frameRate);
            try {
                long extraMs = -1;
                while (!mDebugDisplay.quitRequested()) {
                    fpsMeasurer.startTick();
                    if (cam != null) {
                        frame = cam.getGrabber().refreshAndGetFrame(sleepMs);
                    }

                    // Use previous frame till we get a new one.
                    if (frame != null) {
                        useFrame = frame;
                    }

                    recorder.record(useFrame);

                    mDebugDisplay.updateLineInfo(key,
                            String.format(" >M %4.1ff%+3d", fpsMeasurer.getFps(), extraMs));

                    extraMs = fpsMeasurer.endWait();
                }
            } catch (Exception e) {
                // Expected:
                // jetty.io.EofException when HTTP stream reader is closed.
                // InterruptedException from Thread.sleep
                mLogger.log(TAG, "MJPEG: " + e.toString());
            }

            mLogger.log(TAG, "MJPEG: End Streaming");
        } finally {
            av_log_set_level(AV_LOG_WARNING);
            recorder.stop();
            recorder.release();
        }

        return true;
    }

    private boolean sendMp4(CamInfo cam, HttpServletResponse response) throws IOException {
        // Note: Always returns a feed. If there's no real camera or it has no data yet,
        // returns a feed with only mEmptyFrame till we have actual data.

        if (mMp4Codec == null || mH264Codec == null) {
            mLogger.log(TAG, "MP4/H264 codec not listed in FFMpeg output codec list.");
            return false;
        }

        final String key = String.format("%dc", cam == null ? 0 : cam.getIndex());

        // Only start with at least one frame to get the initial size.
        Frame frame = null;
        if (cam != null) {
            frame = cam.getGrabber().refreshAndGetFrame(500 /*ms*/);
        }

        int frameWidth = frame != null ? frame.imageWidth : CamInputGrabber.DEFAULT_WIDTH;
        int frameHeight = frame != null ? frame.imageHeight : CamInputGrabber.DEFAULT_HEIGHT;
        Frame useFrame = createFrame(frameWidth, frameHeight);

        response.setContentType(_toString(mMp4Codec.mime_type()));
        response.addHeader("Cache-Control", "no-store");
        response.setStatus(HttpServletResponse.SC_OK);

        mLogger.log(TAG, "H264: Streaming on " + response.getOutputStream());

        av_log_set_level(AV_LOG_INFO); // for debugging
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                response.getOutputStream(),
                frameWidth,
                frameHeight);
        try {
            // Note: The following fails to initialize.
            recorder.setFormat(_toString(mMp4Codec.name()));
            // recorder.setFormat(_toString(mH264Codec.name()));
            recorder.setVideoCodec(mH264Codec.video_codec());
            recorder.setVideoBitrate(1000000);
            recorder.setVideoOption("preset", "veryfast");

            double frameRate = H264_FRAME_RATE;
            if (cam != null) {
                frameRate = cam.getGrabber().getFrameRate();
            }
            if (frameRate <= 0) {
                frameRate = H264_FRAME_RATE;
            }
            recorder.setFrameRate(frameRate);

            recorder.start();

            final long sleepMs = (long) (1000 / frameRate);
            FpsMeasurer fpsMeasurer = new FpsMeasurer();
            fpsMeasurer.setFrameRate(frameRate);
            try {
                long extraMs = -1;
                while (!mDebugDisplay.quitRequested()) {
                    fpsMeasurer.startTick();
                    if (cam != null) {
                        frame = cam.getGrabber().refreshAndGetFrame(sleepMs);
                    }

                    // Use previous frame till we get a new one.
                    if (frame != null) {
                        useFrame = frame;
                    }

                    recorder.record(useFrame);

                    mDebugDisplay.updateLineInfo(key,
                            String.format(" >H %4.1ff%+3d", fpsMeasurer.getFps(), extraMs));

                    extraMs = fpsMeasurer.endWait();
                }
            } catch (Exception e) {
                // Expected:
                // jetty.io.EofException when HTTP stream reader is closed.
                // InterruptedException from Thread.sleep
                mLogger.log(TAG, "H264: " + e.toString());
            }

            mLogger.log(TAG, "H264: End Streaming");
        } finally {
            recorder.stop();
            recorder.release();
        }

        return true;
    }

    private Frame createFrame(int width, int height) {
        Frame frame = new Frame(width, height, Frame.DEPTH_UBYTE, 1);

        ByteBuffer buffer = (ByteBuffer) frame.image[0];
        for (int i = 0, n = buffer.limit(); i < n; i++) {
            buffer.put((byte) 0x1F);
        }

        return frame;
    }
}
