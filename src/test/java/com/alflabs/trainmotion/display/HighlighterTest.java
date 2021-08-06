package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.TrainMotion;
import com.alflabs.trainmotion.cam.CamConfig;
import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.CamInfoFactory;
import com.alflabs.trainmotion.cam.IMotionDetector;
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
