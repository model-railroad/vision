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
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.utils.IClock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * Collects stats about the cam analyzer levels that trigger motion detection.
 * <p/>
 * The stats are output in a JSON format compatible with chrome://tracing or Perfetto.
 * That file is rather large, yet neatly compresses to only ~5% via gzip.
 * <p/>
 * The 3 CamAnalyzer threads call collect() from their respective threads and store that in
 * a thread-safe queue of "motion runs". A motion run is defined as a preamble with motion off
 * (where only the most recent N stats are kept), followed by all the stats while motion is on,
 * followed by at most N stats with motion off (thus an off/on/off run pattern).
 * <p/>
 * The collector thread dumps the completed motion runs to the output file periodically.
 */
@Singleton
public class StatsCollector extends ThreadLoop {
    private static final String TAG = StatsCollector.class.getSimpleName();
    private static final int STATS_FPS = 5;
    private static final long IDLE_SLEEP_MS = 1000 / STATS_FPS;
    private static final int NUM_RUN_BUFFERS = STATS_FPS * 10; // 10 seconds

    private final IClock mClock;
    private final ILogger mLogger;
    private final Cameras mCameras;
    private final CommandLineArgs mCommandLineArgs;
    private final Map<Integer, MotionRun> mMotionRuns = Collections.synchronizedMap(new HashMap<>());
    private final ConcurrentLinkedDeque<MotionRun> mCompletedRuns = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean mWriteBeforeClosing = new AtomicBoolean(false);
    private final Map<Long, String> mWriteEntries = new TreeMap<>();
    private final CountDownLatch mLatchEndLoop = new CountDownLatch(1);
    private String mStatsPath;
    private FilterOutputStream mOutput;

    @Inject
    public StatsCollector(IClock clock,
                          ILogger logger,
                          Cameras cameras,
                          CommandLineArgs commandLineArgs) {
        mClock = clock;
        mLogger = logger;
        mCameras = cameras;
        mCommandLineArgs = commandLineArgs;
    }

    @Override
    public void start() throws Exception {
        mLogger.log(TAG, "Start");

        mStatsPath = mCommandLineArgs.getStringOption(CommandLineArgs.OPT_STATS_PATH, null);

        if (mStatsPath != null) {
            FileOutputStream fos = new FileOutputStream(mStatsPath);
            if (mStatsPath.endsWith(".gz")) {
                mOutput = new GZIPOutputStream(fos);
            } else {
                mOutput = new BufferedOutputStream(fos);
            }
            super.start("Thread-Stats");
        }
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mWriteBeforeClosing.set(true);
        mCameras.forEachCamera(camInfo -> collect(camInfo.getIndex(), 0, 0, false));
        mLatchEndLoop.await(10, TimeUnit.SECONDS);
        super.stop();
        mLogger.log(TAG, "Stopped");
    }

    synchronized
    public void collect(int camIndex, double noise1, double noise2, boolean motion) {
        long now = mClock.elapsedRealtime();

        MotionRun run = mMotionRuns.get(camIndex);

        if (mWriteBeforeClosing.get()) {
            if (run != null) {
                mMotionRuns.put(camIndex, null);
                mCompletedRuns.offerLast(run);
            }
            return;
        }

        if (run != null && motion && run.hasEnded()) {
            mCompletedRuns.offerLast(run);
            run = null;
        }

        if (run == null) {
            run = new MotionRun(camIndex);
            mMotionRuns.put(camIndex, run);
        }

        MotionRun complete = run.collect(now, (int) (noise1 * 100), (int) (noise2 * 100), motion);
        if (complete != null) {
            mCompletedRuns.offerLast(complete);
            mMotionRuns.put(camIndex, new MotionRun(camIndex));
        }
    }

    @Override
    protected void _beforeThreadLoop() {
        mLogger.log(TAG, "Running");

        try {
            mOutput.write("[\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {}
    }

    @Override
    protected void _runInThreadLoop() {
        mWriteEntries.clear();

        if (mCompletedRuns.isEmpty()) return;
        // mLogger.log(TAG, "Processing " + mCompletedRuns.size() + " motion runs to write"); // DEBUG

        FilterOutputStream stream = mOutput;
        if (stream == null) return;
        MotionRun run;

        while ((run = mCompletedRuns.pollFirst()) != null) {
            int id = run.mCamId;
            appendLevels(id, mWriteEntries, run.mBefore);
            appendLevels(id, mWriteEntries, run.mDuring);
            appendLevels(id, mWriteEntries, run.mAfter);
            appendMotionSpan(id, mWriteEntries, run);
        }

        for (String entry : mWriteEntries.values()) {
            if (!writeEntry(stream, entry)) {
                break;
            }
        }

        try {
            stream.flush();
        } catch (IOException e) {
            mLogger.log(TAG, "Error flush stream: " + e);
        }

        mLogger.log(TAG, "Wrote " + mWriteEntries.size() + " records");

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Stats idle loop interrupted: " + e);
        }
    }

    @Override
    protected void _afterThreadLoop() {
        mLogger.log(TAG, "End Loop");

        FilterOutputStream stream = mOutput;
        if (stream != null) {
            mOutput = null;
            try {
                stream.flush();
                stream.close();
            } catch (Exception e) {
                mLogger.log(TAG, "Error closing stream: " + e);
            }
        }

        mLatchEndLoop.countDown();
    }

    private void appendLevels(int id, Map<Long, String> entries, Deque<Level> levels) {
        for (Level level : levels) {
            final long ts = level.mNowTS;
            if (ts == 0) continue;

            String s;
            s = String.format(Locale.US,
                    "{ \"name\":\"pct%d\", \"ph\":\"C\", \"ts\": %d, \"pid\": %d, \"args\":{ \"pct\": %d }},\n",
                    id,
                    ts * 1000,
                    id,
                    level.mLevel1);

            s += String.format(Locale.US,
                    "{ \"name\":\"avg%d\", \"ph\":\"C\", \"ts\": %d, \"pid\": %d, \"args\":{ \"avg\": %d }},\n",
                    id,
                    ts * 1000,
                    id,
                    level.mLevel2);
            entries.put(ts, s);
        }
        levels.clear();
    }

    private void appendMotionSpan(int id, Map<Long, String> entries, MotionRun run) {
        final long startTS = run.mStartTS;
        final long endTS = run.mEndTS;
        if (startTS == 0) return;
        String s = String.format(Locale.US,
                "{ \"name\":\"cam%d_hl\", \"ph\":\"X\", \"ts\": %d, \"dur\": %d, \"pid\": %d, \"tid\": %d },\n",
                id,
                startTS * 1000,
                (endTS - startTS) * 1000,
                id, id);
        String existing = entries.get(startTS);
        if (existing != null) {
            s += existing;
        }
        entries.put(startTS, s);
    }

    private boolean writeEntry(FilterOutputStream stream, String s) {
        try {
            stream.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            mLogger.log(TAG, "Error write json stream: " + e);
            return false;
        }
        return true;
    }

    private static class MotionRun {
        private final int mCamId;
        private final Deque<Level> mBefore = new LinkedList<>();
        private final Deque<Level> mDuring = new LinkedList<>();
        private final Deque<Level> mAfter = new LinkedList<>();
        private long mStartTS;
        private long mEndTS;


        private MotionRun(int camId) {
            mCamId = camId;
        }

        public boolean hasEnded() {
            return mEndTS > 0 && !mAfter.isEmpty();
        }

        /**
         * Returns null when accumulating.
         * Returns this when the run is full and a new run should be started.
         */
        public MotionRun collect(long nowTS, int level1, int level2, boolean hasMotion) {
            // 3 states: before motion, during motion, after motion.
            Level level = new Level(nowTS, level1, level2);
            if (!hasMotion && mStartTS == 0) {
                // State 1: before motion.

                if (mBefore.size() >= NUM_RUN_BUFFERS) {
                    mBefore.pollFirst();
                }
                mBefore.offerLast(level);

            } else if (hasMotion) {
                // State 2: during motion.

                if (mStartTS == 0) {
                    mStartTS = nowTS;
                }
                mEndTS = nowTS;

                mDuring.offerLast(level);

            } else if (!hasMotion && mEndTS != 0) {
                // State 3: after motion.

                mAfter.offerLast(level);
                if (mAfter.size() >= NUM_RUN_BUFFERS) {
                    return this;
                }
            }

            return null;
        }
    }

    private static class Level {
        private final long mNowTS;
        private final int mLevel1;
        private final int mLevel2;

        public Level(long nowTS, int level1, int level2) {
            mNowTS = nowTS;
            mLevel1 = level1;
            mLevel2 = level2;
        }
    }
}
