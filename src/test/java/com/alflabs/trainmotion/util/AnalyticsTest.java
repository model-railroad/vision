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
import com.google.common.base.Charsets;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnalyticsTest {
    public @Rule MockitoRule mRule = MockitoJUnit.rule();

    @Inject Random mRandom;
    @Inject Analytics mAnalytics;
    @Inject OkHttpClient mOkHttpClient;
    @Mock private ILocalDateTimeNowProvider mLocalDateTimeNowProvider;


    public interface _injector {
        void inject(AnalyticsTest test);
    }

    @Before
    public void setUp() {
        // Otherwise by default it is permanently 1:42 PM here
        when(mLocalDateTimeNowProvider.getNow()).thenReturn(
                LocalDateTime.of(1901, 2, 3, 13, 42, 43));

        ITrainMotionTestComponent component = DaggerITrainMotionTestComponent.factory().createComponent();
        component.inject(this);
    }

    @After
    public void tearDown() throws Exception {
        mAnalytics.stop();
    }

    @Test
    public void ua_SetTrackingId_FromString() {
        assertThat(mAnalytics.getAnalyticsId()).isNull();

        mAnalytics.setAnalyticsId("___ UID -string 1234 'ignored- 5 # Comment \nBlah");
        assertThat(mAnalytics.getAnalyticsId()).isEqualTo("UID-1234-5");
    }

    @Test
    public void ua_SendEvent() throws Exception {
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

    @Test
    public void ga4_SetTrackingId_FromString() throws IOException {
        assertThat(mAnalytics.getAnalyticsId()).isNull();

        mAnalytics.setAnalyticsId(" G-1234ABCD | 987654321 | XyzAppSecretZyX # Comment \nBlah");
        assertThat(mAnalytics.getAnalyticsId()).isEqualTo("G-1234ABCD");
    }

    @Test
    public void ga4_SendEvent() throws Exception {
        mAnalytics.setAnalyticsId(" G-1234ABCD | 987654321 | XyzAppSecretZyX ");
        mAnalytics.start();
        mAnalytics.sendEvent("CAT", "ACT", "LAB", "72", "USR");
        mAnalytics.stop(); // forces pending tasks to execute

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        Mockito.verify(mOkHttpClient).newCall(requestCaptor.capture());
        Request req = requestCaptor.getValue();
        assertThat(req).isNotNull();
        assertThat(req.url().toString()).isEqualTo("https://www.google-analytics.com/mp/collect?api_secret=XyzAppSecretZyX&measurement_id=G-1234ABCD");
        assertThat(req.method()).isEqualTo("POST");
        Buffer bodyBuffer = new Buffer();
        //noinspection ConstantConditions
        req.body().writeTo(bodyBuffer);
        assertThat(bodyBuffer.readUtf8()).isEqualTo(
                "{'timestamp_micros':1000000,'client_id':'987654321'," +
                        "'events':[{'name':'ACT','params':{'items':[]," +
                        "'event_category':'CAT','event_label':'LAB'," +
                        "'date_sec':'19010203134243','date_min':'190102031342'," +
                        "'value':72,'currency':'USD'}}]}");
    }
}
