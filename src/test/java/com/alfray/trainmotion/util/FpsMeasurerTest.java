package com.alfray.trainmotion.util;

import com.alfray.libutils.utils.FakeClock;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class FpsMeasurerTest {

    private FakeClock mClock;
    private FpsMeasurerFactory mFpsMeasurerFactory;

    @Before
    public void setUp() {
        mClock = new FakeClock(1000);
        mFpsMeasurerFactory = new FpsMeasurerFactory(() -> mClock);
    }

    @Test
    public void testRateLimit10Hz() {
        FpsMeasurer measurer = mFpsMeasurerFactory.create();

        // Setup
        measurer.setFrameRate(10.0);
        long loopMs = measurer.getLoopMs();
        assertThat(loopMs).isEqualTo(100 /* ms */);

        // Start of iteration
        assertThat(mClock.elapsedRealtime()).isEqualTo(1000);
        measurer.startTick();
        // ... do some work ...
        mClock.add(42 /* ms */);
        // End the iteration
        measurer.endWait();
        assertThat(mClock.elapsedRealtime()).isEqualTo(1000+100);
    }
}
