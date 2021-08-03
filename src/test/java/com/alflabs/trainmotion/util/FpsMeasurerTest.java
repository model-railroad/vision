package com.alflabs.trainmotion.util;

import com.alflabs.libutils.utils.FakeClock;
import com.alflabs.trainmotion.dagger.DaggerITrainMotionTestComponent;
import com.alflabs.trainmotion.dagger.ITrainMotionTestComponent;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;

import static com.google.common.truth.Truth.assertThat;

public class FpsMeasurerTest {

    @Inject FakeClock mClock;
    @Inject FpsMeasurerFactory mFpsMeasurerFactory;

    public interface _injector {
        void inject(FpsMeasurerTest test);
    }

    @Before
    public void setUp() {
        ITrainMotionTestComponent component = DaggerITrainMotionTestComponent.factory().createComponent();
        component.inject(this);
    }

    @Test
    public void testRateLimit10Hz() {
        FpsMeasurer measurer = mFpsMeasurerFactory.create();
        assertThat(measurer.getFps()).isEqualTo(0);
        assertThat(measurer.getLoopMs()).isEqualTo(0);

        // Setup
        measurer.setFrameRate(10.0);

        long loopMs = measurer.getLoopMs();
        assertThat(loopMs).isEqualTo(100 /* ms */);

        // Start of iteration
        assertThat(mClock.elapsedRealtime()).isEqualTo(1000);
        measurer.startTick();
        // we don't know the actual FPS till we finish at least one iteration.
        assertThat(measurer.getFps()).isEqualTo(0);
        // ... do some work ...
        mClock.add(42 /* ms */);
        // End the iteration
        measurer.endWait();
        assertThat(mClock.elapsedRealtime()).isEqualTo(1000+100);

        // Start of 2nd iteration
        measurer.startTick();
        // FPS achieved in the previous iteration; we actualled reached our target FPS
        assertThat(measurer.getFps()).isEqualTo(10);
        // this iteration lasts 4x longer than a normal loop time.
        mClock.add(400);
        measurer.endWait();
        assertThat(mClock.elapsedRealtime()).isEqualTo(1000+100+400);

        // Start of 3rd iteration
        measurer.startTick();
        // 2nd iteration was too slow so we didn't reach the desired FPS, our actual FPS was 1/4th
        assertThat(measurer.getFps()).isEqualTo(10.0 / 4);
    }
}
