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

package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.dagger.DaggerITrainMotionTestComponent;
import com.alflabs.trainmotion.dagger.ITrainMotionTestComponent;
import com.alflabs.utils.FakeClock;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;

public class HighlighterTest {
    @Inject FakeClock mClock;
    @Inject HighlighterFactory mHighlighterFactory;

    private final AtomicBoolean mIsMotion = new AtomicBoolean(false);
    private Highlighter mHighlighter;

    public interface _injector {
        void inject(HighlighterTest test);
    }

    @Before
    public void setUp() {
        ITrainMotionTestComponent component = DaggerITrainMotionTestComponent.factory().createComponent();
        component.inject(this);

        mHighlighter = mHighlighterFactory.create(/* index */ 1, mIsMotion::get);
    }

    @Test
    public void testDetector_OnFor2sec() {
        // init off
        assertThat(mHighlighter.isHighlighted()).isFalse();

        mClock.setNow(100);
        mIsMotion.set(false);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isFalse();

        // on
        mClock.setNow(1000);
        mIsMotion.set(true);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        // off after 2 sec --> still on for another 1.5 sec on + 0.5 sec off
        mClock.add(2000);
        mIsMotion.set(false);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        // check for the extra 1/2 sec on
        mClock.add(500);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        // check before/after the +500 ms off mark
        mClock.add(500-1);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        mClock.add(1);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isFalse();
    }


    @Test
    public void testDetector_OnFor3sec() {
        // init off
        assertThat(mHighlighter.isHighlighted()).isFalse();

        mClock.setNow(100);
        mIsMotion.set(false);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isFalse();

        // on
        mClock.setNow(1000);
        mIsMotion.set(true);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        // off after 3 sec --> still on for 1/2 sec
        mClock.add(3000);
        mIsMotion.set(false);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        // check before/after the +500 ms mark
        mClock.add(500-1);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isTrue();

        mClock.add(1);
        mHighlighter.update();
        assertThat(mHighlighter.isHighlighted()).isFalse();
    }
}
