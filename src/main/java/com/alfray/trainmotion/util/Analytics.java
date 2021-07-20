package com.alfray.trainmotion.util;

import com.google.common.base.Strings;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Analytics implements IStartStop {
    private static final String TAG = Analytics.class.getSimpleName();

    private static final boolean DEBUG = true;
    private static final boolean USE_GET = false; // default is POST

    private static final String GA_URL =
            "https://www.google-analytics.com/"
            + (DEBUG ? "debug/" : "")
            + "collect";

    private static final String UTF_8 = "UTF-8";
    private static final MediaType MEDIA_TYPE = MediaType.parse("text/plain");

    private final ILogger mLogger;
    private final Random mRandom;
    private final OkHttpClient mOkHttpClient;
    // Note: The executor is a dagger singleton, shared with the JsonSender.
    private final ScheduledExecutorService mExecutor;

    @Nullable
    private String mAnalyticsId = null;

    @Inject
    public Analytics(ILogger logger,
                     Random random,
                     OkHttpClient okHttpClient,
                     @Named("SingleThreadExecutor") ScheduledExecutorService executor) {
        mLogger = logger;
        mRandom = random;
        mOkHttpClient = okHttpClient;
        mExecutor = executor;
    }

    @Override
    public void start() throws Exception {
    }

    public void setAnalyticsId(@Nonnull String analyticsId) {
        //noinspection ConstantConditions
        if (analyticsId == null || analyticsId.trim().isEmpty()) {
            mAnalyticsId = null;
            mLogger.log(TAG, "Analytics disabled (no ID)");
        } else {
            mAnalyticsId = analyticsId.trim();
            mLogger.log(TAG, "Analytics ID " + mAnalyticsId);
        }
    }

    /**
     * Requests termination. Pending tasks will be executed, no new task is allowed.
     * Waiting time is 10 seconds max.
     * <p/>
     * Side effect: The executor is now a dagger singleton. This affects other classes that
     * use the same executor, e.g. {@link JsonSender}.
     */
    @Override
    public void stop() throws Exception {
        mExecutor.shutdown();
        mExecutor.awaitTermination(10, TimeUnit.SECONDS);
        mLogger.log(TAG, "Shutdown");
    }

    public String getAnalyticsId() {
        return mAnalyticsId;
    }

    public void sendEvent(
            @Nonnull String category,
            @Nonnull String action,
            @Nonnull String label,
            @Nonnull String user_) {
        if (mAnalyticsId == null || mAnalyticsId.isEmpty()) {
            mLogger.log(TAG, "No Tracking ID");
            return;
        }

        mExecutor.execute(() -> {
            try {
                int random = mRandom.nextInt();
                if (random < 0) {
                    random = -random;
                }

                String user = user_;
                if (user.length() > 0 && Character.isDigit(user.charAt(0))) {
                    user = "user" + user;
                }

                String cid = UUID.nameUUIDFromBytes(user.getBytes()).toString();

                String payload = String.format(
                        "v=1&tid=%s&ds=consist&cid=%s&t=event&ec=%s&ea=%s&el=%s&z=%d",
                        URLEncoder.encode(mAnalyticsId, UTF_8),
                        URLEncoder.encode(cid, UTF_8),
                        URLEncoder.encode(category, UTF_8),
                        URLEncoder.encode(action, UTF_8),
                        URLEncoder.encode(label, UTF_8),
                        random);

                Response response = sendPayload(payload);

                mLogger.log(TAG, String.format("Event [%s %s %s %s] code: %d",
                        category, action, label, user, response.code()));

                if (DEBUG) {
                    mLogger.log(TAG, "Event body: " + response.body().string());
                }

                response.close();

            } catch (Exception e) {
                mLogger.log(TAG, "Event ERROR: " + e);
            }
        });
    }

    public void sendPage(
            @Nonnull String url_,
            @Nonnull String path,
            @Nonnull String user_) {
        if (mAnalyticsId == null) {
            mLogger.log(TAG, "No Tracking ID");
            return;
        }

        mExecutor.execute(() -> {
            try {
                int random = mRandom.nextInt();
                if (random < 0) {
                    random = -random;
                }

                String user = user_;
                if (user.length() > 0 && Character.isDigit(user.charAt(0))) {
                    user = "user" + user;
                }

                String cid = UUID.nameUUIDFromBytes(user.getBytes()).toString();

                String d_url = url_ + path;

                String payload = String.format(
                        "v=1&tid=%s&ds=consist&cid=%s&t=pageview&dl=%s&z=%d",
                        URLEncoder.encode(mAnalyticsId, UTF_8),
                        URLEncoder.encode(cid, UTF_8),
                        URLEncoder.encode(d_url, UTF_8),
                        random);

                Response response = sendPayload(payload);

                mLogger.log(TAG, String.format("PageView [%s %s] code: %d",
                        d_url, user, response.code()));

                if (DEBUG) {
                    mLogger.log(TAG, "Event body: " + response.body().string());
                }

                response.close();

            } catch (Exception e) {
                mLogger.log(TAG, "Page ERROR: " + e);
            }
        });
    }

    // Must be executed in background thread. Caller must call Response.close().
    private Response sendPayload(String payload) throws IOException {
        if (DEBUG) {
            mLogger.log(TAG, "Event Payload: " + payload);
        }

        String url = GA_URL;
        if (USE_GET) {
            url += "?" + payload;
        }

        Request.Builder builder = new Request.Builder().url(url);

        if (!USE_GET) {
            RequestBody body = RequestBody.create(MEDIA_TYPE, payload);
            builder.post(body);
        }

        Request request = builder.build();
        return mOkHttpClient.newCall(request).execute();
    }
}
