package com.alfray.camproxy.cam;

import com.alfray.camproxy.CommandLineArgs;
import com.alfray.camproxy.util.DebugDisplay;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.IStartStop;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class HttpServ implements IStartStop {
    private static final String TAG = HttpServ.class.getSimpleName();
    private final ILogger mLogger;
    private final Cameras mCameras;
    private final DebugDisplay mDebugDisplay;
    private final Server mServer;
    private final Java2DFrameConverter mFrameConverter;

    @Inject
    public HttpServ(
            ILogger logger,
            Cameras cameras,
            DebugDisplay debugDisplay,
            CommandLineArgs commandLineArgs) {
        mLogger = logger;
        mCameras = cameras;
        mDebugDisplay = debugDisplay;

        mFrameConverter = new Java2DFrameConverter(); // hangs when created in a thread

        int port = commandLineArgs.getIntOption(CommandLineArgs.OPT_HTTP_PORT, 8080);
        mServer = new Server(port);
        mServer.setHandler(new Handler());
    }

    public void start() throws Exception {
        mLogger.log(TAG, "Start");
        mServer.start();
    }

    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mServer.stop();
    }

    public class Handler extends AbstractHandler {
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

            return false;
        }

        private void sendText(HttpServletResponse response, int code, String msg) throws IOException {
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(code);
            response.getWriter().println(msg);
        }

        private boolean sendImage(CamInfo cam, HttpServletResponse response) throws IOException {
            if (cam != null) {
                Frame frame = cam.getGrabber().getLastFrame().get();
                if (frame != null) {
                    BufferedImage image  = mFrameConverter.convert(frame);
                    if (image != null) {
                        response.setContentType("image/jpeg");
                        response.addHeader("Cache-Control", "no-store");
                        response.setStatus(HttpServletResponse.SC_OK);
                        ServletOutputStream outputStream = response.getOutputStream();
                        ImageIO.write(image, "jpg", outputStream);
                        outputStream.flush();
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
