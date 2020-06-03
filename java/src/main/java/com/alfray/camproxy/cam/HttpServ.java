package com.alfray.camproxy.cam;

import com.alfray.camproxy.CommandLineArgs;
import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.IStartStop;
import org.eclipse.jetty.server.Server;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HttpServ implements IStartStop {
    private static final String TAG = HttpServ.class.getSimpleName();

    private final ILogger mLogger;
    private final Server mServer;

    @Inject
    public HttpServ(
            ILogger logger,
            CommandLineArgs commandLineArgs,
            HttpCamHandler httpCamHandler) {
        mLogger = logger;

        int port = commandLineArgs.getIntOption(CommandLineArgs.OPT_HTTP_PORT, 8080);
        mServer = new Server(port);
        mServer.setHandler(httpCamHandler);
    }

    public void start() throws Exception {
        mLogger.log(TAG, "Start");
        mServer.start();
    }

    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mServer.stop();
    }
}
