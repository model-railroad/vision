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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.inject.Inject;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;

public class AnalyticsTest {
    public @Rule MockitoRule mRule = MockitoJUnit.rule();

    @Inject Random mRandom;
    @Inject Analytics mAnalytics;
    @Inject OkHttpClient mOkHttpClient;

    public interface _injector {
        void inject(AnalyticsTest test);
    }

    @Before
    public void setUp() {
        ITrainMotionTestComponent component = DaggerITrainMotionTestComponent.factory().createComponent();
        component.inject(this);
    }

    @After
    public void tearDown() throws Exception {
        mAnalytics.stop();
    }

    @Test
    public void testSetTrackingId() {
        assertThat(mAnalytics.getAnalyticsId()).isNull();

        mAnalytics.setAnalyticsId("___ UID -string 1234 'ignored- 5 # Comment \nBlah");
        assertThat(mAnalytics.getAnalyticsId()).isEqualTo("UID-1234-5");
    }

    @Test
    public void testSendEvent() throws Exception {
        mAnalytics.setAnalyticsId("UID-1234-5");
        mAnalytics.start();
        mAnalytics.sendEvent("CAT", "ACT", "LAB", "VAL", "USR");
        mAnalytics.stop(); // forces pending tasks to execute

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mOkHttpClient).newCall(requestCaptor.capture());
        Request req = requestCaptor.getValue();
        assertThat(req).isNotNull();
        assertThat(req.url().toString()).isEqualTo("https://www.google-analytics.com/collect");
        assertThat(req.method()).isEqualTo("POST");
        Buffer bodyBuffer = new Buffer();
        //noinspection ConstantConditions
        req.body().writeTo(bodyBuffer);
        assertThat(bodyBuffer.readUtf8()).startsWith(
                "v=1&tid=UID-1234-5&ds=trainmotion&cid=2b6cc9c3-0eaa-39c1-8909-1ea928529cbd" +
                        "&t=event&ec=CAT&ea=ACT&el=LAB&z=42&ev=VAL&qt=");
    }
}
