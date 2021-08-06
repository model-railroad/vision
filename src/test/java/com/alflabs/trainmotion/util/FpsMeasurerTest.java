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

import com.alflabs.trainmotion.dagger.DaggerITrainMotionTestComponent;
import com.alflabs.trainmotion.dagger.ITrainMotionTestComponent;
import com.alflabs.utils.FakeClock;
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
