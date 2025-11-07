/*
 * Project: Train-Motion
 * Copyright (C) 2023 alf.labs gmail com,
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

import com.alflabs.annotations.NonNull;
import com.alflabs.annotations.Null;
import com.alflabs.kv.IKeyValue;
import com.alflabs.kv.KeyValueClient;
import com.alflabs.rx.IPublisher;
import com.alflabs.rx.IStream;
import com.alflabs.rx.Publishers;
import com.alflabs.rx.Streams;
import com.alflabs.trainmotion.ConfigIni;
import com.alflabs.utils.IClock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controls the connection to a KeyValueClient if enabled in settings.
 */
@Singleton
public class KVController extends ThreadLoop {
    private static final String TAG = KVController.class.getSimpleName();
    private static final long IDLE_SLEEP_MS = 60 * 1000L;  // 1 minute
    private static final int KV_SERVER_PORT = 20006; // Default port for KV Server.

    private final IClock mClock;
    private final ILogger mLogger;
    private final ConfigIni mConfigIni;
    private Optional<InetSocketAddress> mSocketAddress = Optional.empty();
    private final AtomicReference<KeyValueClient> mKVClient = new AtomicReference<>();
    private final AtomicBoolean mKVConnected = new AtomicBoolean(false);
    private final AtomicBoolean mKVEnabled = new AtomicBoolean(false);

    /** Stream that broadcasts whether the client is connected. */
    private final IStream<Boolean> mConnectedStream = Streams.stream();
    private final IPublisher<Boolean> mConnectedPublisher = Publishers.latest();
    /** Stream that re-broadcasts the key changes from the KV client. */
    private final IStream<String> mKeyChangedStream = Streams.stream();
    private final IPublisher<String> mKeyChangedPublisher = Publishers.publisher();

    @Inject
    public KVController(
            IClock clock,
            ILogger logger,
            ConfigIni configIni) {
        mClock = clock;
        mLogger = logger;
        mConfigIni = configIni;
        mConnectedStream.publishWith(mConnectedPublisher);
        mKeyChangedStream.publishWith(mKeyChangedPublisher);
    }

    @Null
    public IKeyValue getKeyValueClient() {
        return mKVClient.get();
    }

    public boolean isEnabled() {
        return mKVEnabled.get();
    }

    public boolean isConnected() {
        return mKVConnected.get();
    }

    /** Stream that broadcasts whether the client is connected. */
    @NonNull
    public IStream<Boolean> getConnectedStream() {
        return mConnectedStream;
    }

    /** Stream that re-broadcasts the key changes from the KV client. */
    @NonNull
    public IStream<String> getKeyChangedStream() {
        return mKeyChangedStream;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");
        mKVConnected.set(false);
        mConnectedPublisher.publish(false);
        mKVClient.set(null);

        String host = "";
        int port = KV_SERVER_PORT;
        try {
            String[] hostPort = mConfigIni.getKvHostPort().split(":");
            if (hostPort.length > 1) {
                port = Integer.parseInt(hostPort[1]);
            }
            if (hostPort.length > 0) {
                host = hostPort[0].trim();
            }
            if (host.isEmpty()) {
                // We don't start if the host is not defined in settings.
                mLogger.log(TAG, "KVClient: No address host:port defined.");
                return;
            }
            mSocketAddress = Optional.of(new InetSocketAddress(InetAddress.getByName(host), port));

            // The "KV connection" check service is now enabled since we have a seemingly
            // valid configuration, whether we can connect to it or not.
            mKVEnabled.set(true);
        } catch (Throwable t) {
            mLogger.log(TAG, "KVClient: Failed to initialize for address "
                    + host + ":" + port + ": "
                    + t);
            // Don't start if the address is not valid.
            return;
        }

        super.start("Thread-KVClient");
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");

        mKVClient.updateAndGet(kvClient -> {
            if (kvClient != null) {
                kvClient.stopAsync();
            }
            return null;
        });

        super.stop();
        mLogger.log(TAG, "Stopped");
    }

    @Override
    protected void _runInThreadLoop() throws EndLoopException {

        if (!mSocketAddress.isPresent()) {
            throw new EndLoopException();
        }

        try {
            // This creates a client but does not connect to it yet.
            KeyValueClient kvClient = new KeyValueClient(
                    mClock,
                    mLoggerAdapter,
                    mSocketAddress.get(),
                    mStatsListener);
            mKVClient.set(kvClient);
            kvClient.getChangedStream().subscribe((stream, key) -> mKeyChangedPublisher.publish(key));

            // Try to connect and stay connected.
            if (kvClient.startSync()) {
                mLogger.log(TAG, "KVClient: Connected.");
                mKVConnected.set(true);
                mConnectedPublisher.publish(true);
                kvClient.join();
            }

        } catch (Throwable t) {
            mLogger.log(TAG, "KVClient: Connection failed: "+ t);
        }
        // Not connected anymore.
        mLogger.log(TAG, "KVClient: Disconnected.");
        mKVConnected.set(false);
        mConnectedPublisher.publish(false);
        mKVClient.set(null);

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Idle loop interrupted: " + e);
        }
    }

    private final com.alflabs.utils.ILogger mLoggerAdapter = new com.alflabs.utils.ILogger() {
        @Override
        public void d(String tag, String message) {
            mLogger.log(tag, message);
        }

        @Override
        public void d(String tag, String message, Throwable tr) {
            mLogger.log(tag, message + " : " + tr.toString());
        }
    };

    private final KeyValueClient.IStatsListener mStatsListener = new KeyValueClient.IStatsListener() {
        @Override
        public void addBandwidthTXBytes(int count) {
            // no-op
        }

        @Override
        public void addBandwidthRXBytes(int count) {
            // no-op
        }

        @Override
        public void setMessage(String msg) {
            // no-op
        }

        @Override
        public void HBLatencyRequestSent() {
            // no-op
        }

        @Override
        public void HBLatencyReplyReceived() {
            // no-op
        }
    };
}
