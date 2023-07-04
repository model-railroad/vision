/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alflabs.trainmotion.util;

import com.alflabs.utils.IClock;
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
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Send common hits to the Google Analytics Measurement Protocol v1.
 * <p/>
 * https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide
 */
@Singleton
public class Analytics extends ThreadLoop {
    private static final String TAG = Analytics.class.getSimpleName();

    private static final boolean VERBOSE_DEBUG = false;
    private static final boolean USE_GET = false; // default is POST
    private static final long IDLE_SLEEP_MS = 1000 / 10;
    private static final int MAX_ERROR_NUM = 3;

    private static final String GA_URL =
            "https://www.google-analytics.com/"
            + (VERBOSE_DEBUG ? "debug/" : "")
            + "collect";
    private static final String GA4_URL =
            "https://www.google-analytics.com/"
                    + (VERBOSE_DEBUG ? "debug/" : "")
                    + "mp/collect";

    private static final String UTF_8 = "UTF-8";
    private static final MediaType MEDIA_TYPE = MediaType.parse("text/plain");

    // App specific constants
    private static final String CATEGORY = "TrainMotion";    // Main filter for GA / DataStudio
    private static final String DATA_SOURCE = "trainmotion";
    private static final String USER_ID = "TrainMotion";     // should be made to vary per machine

    private final ILogger mLogger;
    private final IClock mClock;
    private final Random mRandom;
    private final OkHttpClient mOkHttpClient;
    private final ConcurrentLinkedDeque<Payload> mPayloads = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean mStopLoopOnceEmpty = new AtomicBoolean(false);
    private final CountDownLatch mLatchEndLoop = new CountDownLatch(1);
    private final ILocalDateTimeNowProvider mLocalDateTimeNow;
    // Note: The executor is a dagger singleton, shared with the JsonSender.
    private final ScheduledExecutorService mExecutor;

    @Nullable
    private String mAnalyticsId = null;
    private String mGA4ClientId = null;
    private String mGA4AppSecret = null;
    private boolean mIsGA4 = false;

    @Inject
    public Analytics(ILogger logger,
                     IClock clock,
                     Random random,
                     OkHttpClient okHttpClient,
                     ILocalDateTimeNowProvider localDateTimeNow,
                     @Named("SingleThreadExecutor") ScheduledExecutorService executor) {
        mLogger = logger;
        mClock = clock;
        mRandom = random;
        mOkHttpClient = okHttpClient;
        mLocalDateTimeNow = localDateTimeNow;
        mExecutor = executor;
    }

    /** Must be called before {@link #start()}. All events are ignored till this is set. */
    public void setAnalyticsId(@Nonnull String analyticsId) {
        // Use "#" as a comment and only take the first thing before, if any.
        analyticsId = analyticsId.replaceAll("[#\n\r].*", "");

        // GA ID format is "UA-Numbers-1" so accept only letters, numbers, hyphen. Ignore the rest.
        // For GA4, we use the format "GA4ID|ClientID|AppSecret".
        if (analyticsId.contains("|")) {
            // GA4
            analyticsId = analyticsId.replaceAll("[^A-Za-z0-9|-]", "");
            String[] fields = analyticsId.split("\\|");
            mAnalyticsId = fields.length > 0 ? fields[0] : null;
            mGA4ClientId = fields.length > 1 ? fields[1] : null;
            mGA4AppSecret = fields.length > 2 ? fields[2] : null;
            mIsGA4 = !Strings.isNullOrEmpty(mAnalyticsId)
                    && !Strings.isNullOrEmpty(mGA4ClientId)
                    && !Strings.isNullOrEmpty(mGA4AppSecret);
        } else {
            // Legacy GA2-3
            analyticsId = analyticsId.replaceAll("[^A-Z0-9-]", "");
            mAnalyticsId = analyticsId;
            mIsGA4 = false;
        }

        //noinspection ConstantConditions
        if (mAnalyticsId == null || mAnalyticsId.trim().isEmpty()) {
            mAnalyticsId = null;
            mLogger.log(TAG, "Analytics disabled (no ID)");
        } else {
            mLogger.log(TAG, "Analytics ID " + mAnalyticsId);
        }
    }

    @Override
    public void start() throws Exception {
        super.start("Analytics");
    }

    /**
     * Requests termination. Pending tasks will be executed, no new task is allowed.
     * Waiting time is 10 seconds max.
     * <p/>
     * Side effect: The executor is now a dagger singleton. This affects other classes that
     * use the same executor, e.g. JsonSender.
     */
    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mStopLoopOnceEmpty.set(true);
        mLatchEndLoop.await(10, TimeUnit.SECONDS);
        super.stop();
        mExecutor.shutdown();
        mExecutor.awaitTermination(10, TimeUnit.SECONDS);
        mLogger.log(TAG, "Stopped");
    }

    @Override
    protected void _runInThreadLoop() throws EndLoopException {
        final boolean isStopping = mStopLoopOnceEmpty.get();
        final boolean isNotStopping = !isStopping;

        if (mPayloads.isEmpty()) {
            if (isStopping) {
                throw new EndLoopException();
            }
        } else {
            int errors = 0;
            Payload payload;
            while ((payload = mPayloads.pollFirst()) != null) {
                if (!payload.send(mClock.elapsedRealtime())) {
                    if (isNotStopping) {
                        // If it fails, append the payload at the *end* of the queue to retry later
                        // after all newer events.
                        // Except if we fail when stopping, in that case we just drop the events.
                        mPayloads.offerLast(payload);
                        errors++;

                        // Don't hammer the server in case of failures.
                        if (errors >= MAX_ERROR_NUM) {
                            break;
                        }
                    }
                }
            }
        }

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Stats idle loop interrupted: " + e);
        }
    }

    @Override
    protected void _afterThreadLoop() {
        mLogger.log(TAG, "End Loop");
        mLatchEndLoop.countDown();
    }

    public String getAnalyticsId() {
        return mAnalyticsId;
    }

    public void sendEvent(
            @Nonnull String action,
            @Nonnull String label) {
        sendEvent(CATEGORY, action, label, null, USER_ID);
    }

    public void sendEvent(
            @Nonnull String action,
            @Nonnull String label,
            @Nonnull String value) {
        sendEvent(CATEGORY, action, label, value, USER_ID);
    }

    public void sendEvent(
            @Nonnull String category,
            @Nonnull String action,
            @Nonnull String label,
            @Nullable String value,
            @Nonnull String user_) {
        final String analyticsId = mAnalyticsId;
        if (analyticsId == null || analyticsId.isEmpty()) {
            mLogger.log(TAG, "Event Ignored -- No Tracking ID");
            return;
        }

        try {
            int random = mRandom.nextInt();
            if (random < 0) {
                random = -random;
            }

            String user = Strings.isNullOrEmpty(user_) ? USER_ID : user_;
            String cid = UUID.nameUUIDFromBytes(user.getBytes()).toString();

            // Events keys:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide#event
            // GA4:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4

            String payload;
            if (mIsGA4) {
                // TBD revisit later with a proper GA4 implementation.
                // Nothing ever goes wrong generating JSON using a String.format, right?
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                String timeWithSeconds = mLocalDateTimeNow.getNow().format(formatter);
                String timeWithMinutes = timeWithSeconds.substring(0, timeWithSeconds.length() - 2);

                payload = String.format("{" +
                                "'client_id':'%s'" +                // GA4 client id
                                ",'events':[{'name':'%s'" +         // event action
                                ",'params':{'items':[]" +
                                ",'event_category':'%s'" +          // event category
                                ",'event_label':'%s'" +             // event label
                                ",'date_sec':'%s'" +                // date with seconds
                                ",'date_min':'%s'",                 // date with minutes
                        mGA4ClientId,
                        action,
                        category,
                        label,
                        timeWithSeconds,
                        timeWithMinutes
                );
                if (!Strings.isNullOrEmpty(value)) {
                    try {
                        int intVal = Integer.parseInt(value);
                        payload += String.format(",'value':%d,'currency':'USD'", intVal);
                    } catch (Exception _ignore) {
                        // no-op
                    }
                }
                payload += "}}]}";
            } else {
                payload = String.format(
                        "v=1" +
                                "&tid=%s" +         // tracking id
                                "&ds=%s" +          // data source
                                "&cid=%s" +         // anonymous cliend id
                                "&t=event" +        // hit type == event
                                "&ec=%s" +          // event category
                                "&ea=%s" +          // event action
                                "&el=%s" +          // event label
                                "&z=%d",            // cache buster
                        URLEncoder.encode(analyticsId, UTF_8),
                        URLEncoder.encode(DATA_SOURCE, UTF_8),
                        URLEncoder.encode(cid, UTF_8),
                        URLEncoder.encode(category, UTF_8),
                        URLEncoder.encode(action, UTF_8),
                        URLEncoder.encode(label, UTF_8),
                        random);

                if (!Strings.isNullOrEmpty(value)) {
                    payload += String.format("&ev=%s", URLEncoder.encode(value, UTF_8));
                }
            }

            mPayloads.offerFirst(new Payload(
                    mClock.elapsedRealtime(),
                    payload,
                    String.format("Event [c:%s a:%s l:%s v:%s u:%s]", category, action, label, value, user)
            ));
        } catch (Exception e) {
            mLogger.log(TAG, "Event Encoding ERROR: " + e);
        }
    }

    public void sendPage(
            @Nonnull String url_,
            @Nonnull String path,
            @Nonnull String user_) {
        final String analyticsId = mAnalyticsId;
        if (analyticsId == null || analyticsId.isEmpty()) {
            mLogger.log(TAG, "Page Ignored -- No Tracking ID");
            return;
        }
        if (mIsGA4) {
            // Deprecated for GA4
            mLogger.log(TAG, "Page Ignored with GA4");
            return;
        }

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

            // Page keys:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide#page

            String payload = String.format(
                    "v=1" +
                            "&tid=%s" +         // tracking id
                            "&ds=%s" +          // data source
                            "&cid=%s" +         // anonymous cliend id
                            "&t=pageview" +     // hit type == pageview
                            "&dl=%s" +          // document location
                            "&z=%d",            // cache buster
                    URLEncoder.encode(analyticsId, UTF_8),
                    URLEncoder.encode(DATA_SOURCE, UTF_8),
                    URLEncoder.encode(cid, UTF_8),
                    URLEncoder.encode(d_url, UTF_8),
                    random);

            mPayloads.offerFirst(new Payload(
                    mClock.elapsedRealtime(),
                    payload,
                    String.format("PageView [d:%s u:%s]", d_url, user)
            ));
        } catch (Exception e) {
            mLogger.log(TAG, "Page Encoding ERROR: " + e);
        }
    }


    private class Payload {
        private final long mCreatedTS;
        private final String mPayload;
        private final String mDebugLog;

        public Payload(long createdTS, String payload, String debugLog) {
            mCreatedTS = createdTS;
            mPayload = payload;
            mDebugLog = debugLog;
        }

        /** Must be executed in background thread. */
        public boolean send(long nowTS) {
            long deltaTS = nowTS - mCreatedTS;

            // Queue Time:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#qt
            // GA4 has timestamp_micros at the outer level:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference?client_type=firebase#payload
            String payload;
            if (mIsGA4) {
                payload = mPayload.replaceFirst("\\{",
                        String.format("{'timestamp_micros':%d,", mCreatedTS * 1000 /* ms to μs */)
                        );
            } else {
                payload = String.format("%s&qt=%d" /* queue_time */, mPayload, deltaTS);
            }

            try {
                Response response = mIsGA4 ? sendPayloadGA4(payload) : sendPayloadV1(payload);

                int code = response.code();
                mLogger.log(TAG, String.format("%s delta: %d ms, code: %d",
                        mDebugLog, deltaTS, code));

                if (VERBOSE_DEBUG) {
                    mLogger.log(TAG, "Event body: " + response.body().string());
                }

                response.close();
                return code < 400;

            } catch (Exception e) {
                mLogger.log(TAG, "Send ERROR: " + e);
            }

            return false;
        }

        /** Must be executed in background thread. Caller must call Response.close(). */
        private Response sendPayloadV1(String payload) throws IOException {
            if (VERBOSE_DEBUG) {
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

        /** Must be executed in background thread. Caller must call Response.close(). */
        private Response sendPayloadGA4(String payload) throws IOException {
            if (VERBOSE_DEBUG) {
                mLogger.log(TAG, "GA4 Event Payload: " + payload);
            }

            String url = String.format("%s?api_secret=%s&measurement_id=%s",
                    GA4_URL, mGA4AppSecret, mAnalyticsId);

            Request.Builder builder = new Request.Builder().url(url);

            // GA4 always uses POST
            RequestBody body = RequestBody.create(MEDIA_TYPE, payload);
            builder.post(body);
            Request request = builder.build();
            return mOkHttpClient.newCall(request).execute();
        }
    }
}
