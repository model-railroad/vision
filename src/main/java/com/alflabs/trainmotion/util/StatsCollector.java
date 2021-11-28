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

import com.alflabs.trainmotion.CommandLineArgs;
import com.alflabs.utils.IClock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class StatsCollector extends ThreadLoop {
    private static final String TAG = StatsCollector.class.getSimpleName();
    private static final int NUM_CHANNELS = 3;
    private static final int STATS_FPS = 5;
    private static final long COLLECT_ELAPSED_MILLIS = 1000L / STATS_FPS;
    private static final long DUMP_DELAY_MILLIS = 60 * 1000;
    private static final int NUM_RECORDS_BUFFER = STATS_FPS * 60; // 1 minute of records

    private final IClock mClock;
    private final ILogger mLogger;
    private final CommandLineArgs mCommandLineArgs;
    private final Data mAccumulator = new Data(0);
    private final ConcurrentLinkedDeque<Data> mPendingData = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean mEnableWriting = new AtomicBoolean(true);
    private long mLastCollectMs;
    private String mStatsPath;
    private BufferedOutputStream mOutput;
    private boolean mUseJson;

    @Inject
    public StatsCollector(IClock clock,
                          ILogger logger,
                          CommandLineArgs commandLineArgs) {
        mClock = clock;
        mLogger = logger;
        mCommandLineArgs = commandLineArgs;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");

        mStatsPath = mCommandLineArgs.getStringOption(CommandLineArgs.OPT_STATS_PATH, null);

        if (mStatsPath != null) {
            mUseJson = mStatsPath.endsWith(".json");
            mOutput = new BufferedOutputStream(new FileOutputStream(mStatsPath));

            super.start("Thread-Stats");
        }
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        super.stop();
        BufferedOutputStream stream = mOutput;
        if (stream != null) {
            mOutput = null;
            try {
                stream.flush();
                stream.close();
                mLogger.log(TAG, "Closed");
            } catch (Exception e) {
                mLogger.log(TAG, "Error closing stream: " + e);
            }
        }
    }

    public void toggleWriting() {
        mEnableWriting.set(!mEnableWriting.get());
        mLogger.log(TAG, "Writing " + (mEnableWriting.get() ? "Enabled" : "Disabled"));
    }

    synchronized
    public void collect(int camIndex, double noise1, double noise2, boolean motion) {
        long now = mClock.elapsedRealtime();

        if (motion) {
            noise1 *= -1;
            noise2 *= -1;
        }
        mAccumulator.set(camIndex - 1, (int)(noise1 * 100), (int)(noise2 * 100));

        if (mLastCollectMs != 0) {
            long deltaMs = now - mLastCollectMs;
            if (deltaMs > COLLECT_ELAPSED_MILLIS) {
                Data dup = mAccumulator.dup(now);
                mPendingData.add(dup);
                mLastCollectMs = now;
            }
        } else {
            mLastCollectMs = now;
        }
    }

    @Override
    protected void _runInThreadLoop() {
        mLogger.log(TAG, "Running");

        if (mUseJson) {
            try {
                mOutput.write("[\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignore) {}
        }

        long firstMs = 0;
        boolean canRun = true;
        while (canRun && !mQuit && mOutput != null) {
            try {
                Thread.sleep(DUMP_DELAY_MILLIS);
            } catch (InterruptedException e) {
                canRun = false;
            } catch (Exception e) {
                mLogger.log(TAG, "Dump loop interrupted: " + e);
                canRun = false;
            }

            if (!mEnableWriting.get()) {
                // Limit capacity to last NUM_RECORDS_BUFFER records
                int s = mPendingData.size() - NUM_RECORDS_BUFFER;
                if (s > 0) {
                    mLogger.log(TAG, "Ignore " + s + " records"); // DEBUG
                    for (int i = 0; i < s; i++) {
                        mPendingData.pollFirst();
                    }
                }
                continue;
            }

            BufferedOutputStream stream = mOutput;
            if (stream == null) break;

            Data data;
            int n = 0;
            long[] startTS = new long[3];
            long[] endTS = new long[3];
            while ((data = mPendingData.pollFirst()) != null) {
                long ts = data.mTimestamp;
                if (firstMs != 0) {
                    ts -= firstMs;
                } else {
                    firstMs = ts;
                    ts = 0;
                }

                int[] d = data.mData;
                if (mUseJson) {
                    // Json format for chrome://tracing
                    // https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#
                    // Counter: {"name": "ctr", "ph": "C", "ts":  0, "args": {"cats":  0, "dogs": 7}},
                    // Event:   {"name": "evt", "ph": "i", "ts": 1234523.3, "pid": 2343, "tid": 2347, "s": "g"}
                    // TS in microseconds (millis * 1000)

                    ts *= 1000; // millis to micros

                    String s = String.format(Locale.US,
                            "" +
                                    "{ \"name\":\"cam1\", \"ph\":\"C\", \"ts\": %d, \"pid\": 1, \"args\":{ \"pct\": %d, \"avg\": %d }},\n" +
                                    "{ \"name\":\"cam2\", \"ph\":\"C\", \"ts\": %d, \"pid\": 2, \"args\":{ \"pct\": %d, \"avg\": %d }},\n" +
                                    "{ \"name\":\"cam3\", \"ph\":\"C\", \"ts\": %d, \"pid\": 3, \"args\":{ \"pct\": %d, \"avg\": %d }},\n",
                            ts, Math.abs(d[0]), Math.abs(d[1]),
                            ts, Math.abs(d[2]), Math.abs(d[3]),
                            ts, Math.abs(d[4]), Math.abs(d[5]));

                    for (int i = 0; i <3; i++) {
                        final int i2 = i * 2;
                        final boolean motion = (d[i2] < 0) || (d[i2 +1] < 0);
                        final long start = startTS[i];
                        if (motion) {
                            if (start == 0) {
                                startTS[i] = ts;
                            }
                            endTS[i] = ts;
                        } else {
                            if (start != 0) {
                                final int i1 = i + 1;
                                s += String.format(Locale.US, "{ \"name\":\"cam%d_hl\", \"ph\":\"X\", \"ts\": %d, \"dur\": %d, \"pid\": %d, \"tid\": %d },\n",
                                        i1, startTS[i], endTS[i] - startTS[i], i1, i1);
                                startTS[i] = 0;
                            }
                        }
                    }
                    try {
                        stream.write(s.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        mLogger.log(TAG, "Error write json stream: " + e);
                        canRun = false;
                    }

                } else {
                    // Raw binary or text formats (easier to write parsers for later)
                    String s = String.format(Locale.US, "%s %s\n", ts, Arrays.toString(d));
                    try {
                        stream.write(s.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        mLogger.log(TAG, "Error write json stream: " + e);
                        canRun = false;
                    }
                }
                n++;
            }
            mLogger.log(TAG, "Write " + n + " records"); // DEBUG

            try {
                stream.flush();
            } catch (IOException e) {
                mLogger.log(TAG, "Error flush stream: " + e);
                canRun = false;
            }
        }
        mLogger.log(TAG, "End Loop");
    }

    private static class Data {
        private final long mTimestamp;
        private final int[] mData = new int[2 * NUM_CHANNELS];

        public Data(long timestamp) {
            mTimestamp = timestamp;
        }

        public void set(int index, int val1, int val2) {
            index *= 2;
            mData[index] = val1;
            mData[++index] = val2;
        }

        public Data dup(long newTimestamp) {
            Data dest = new Data(newTimestamp);
            System.arraycopy(this.mData, 0, dest.mData, 0, this.mData.length);
            return dest;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "mTimestamp=" + mTimestamp +
                    ", mData=" + Arrays.toString(mData) +
                    '}';
        }
    }
}
