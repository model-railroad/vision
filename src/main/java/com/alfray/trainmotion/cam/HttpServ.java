package com.alfray.trainmotion.cam;

import com.alfray.trainmotion.CommandLineArgs;
import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.IStartStop;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

@Singleton
public class HttpServ implements IStartStop {
    private static final String TAG = HttpServ.class.getSimpleName();

    private final ILogger mLogger;
    private final CommandLineArgs mCommandLineArgs;
    private final HttpCamHandler mHttpCamHandler;
    private Server mServer;

    @Inject
    public HttpServ(
            ILogger logger,
            CommandLineArgs commandLineArgs,
            HttpCamHandler httpCamHandler) {
        mLogger = logger;

        mCommandLineArgs = commandLineArgs;
        mHttpCamHandler = httpCamHandler;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");

        int port = mCommandLineArgs.getIntOption(CommandLineArgs.OPT_HTTP_PORT, 8080);
        mServer = new Server(port);

        String webRoot = mCommandLineArgs.getStringOption(CommandLineArgs.OPT_WEB_ROOT, null);
        HandlerList handlers = new HandlerList(
                mHttpCamHandler,
                createResourceHandler(webRoot));

        mServer.setHandler(handlers);
        mServer.start();
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mServer.stop();
    }

    private ResourceHandler createResourceHandler(@Nullable String webRoot) throws MalformedURLException {

        // ResourceService can define setPathInfoOnly:
        // - when true, query is / + request path info (everything from url / to ?)
        // - when false, query is request servlet path + path info.
        // Since I do not have any servlet path, this is a no-op.
        //
        // The constructed path ( /something ) is then processed by a ResourceFactory.
        // If the hanlder.setBaseResource is set, it defines the root of the content.
        // Otherwise a ContextHandler.getCurrentContext() is used (which we do not have here).

        ResourceService res = new ResourceService() {
            @Override
            protected void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
                mLogger.log(TAG, "Not Found: " + request);
                super.notFound(request, response);
            }
        };

        res.setPathInfoOnly(false);
        res.setDirAllowed(false);

        ResourceHandler handler = new ResourceHandler(res);
        URL url;
        if (webRoot != null) {
            File file = new File(webRoot);
            url = file.toURI().toURL();
        } else {
            url = this.getClass().getResource("/web");
        }
        handler.setBaseResource(Resource.newResource(url));

        mLogger.log(TAG, "Web Root Resource: " + handler.getBaseResource());
        return handler;
    }

}
