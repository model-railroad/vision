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

package com.alflabs.trainmotion.dagger;

import com.alflabs.trainmotion.PlaylistTest;
import com.alflabs.trainmotion.display.HighlighterTest;
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.trainmotion.util.AnalyticsTest;
import com.alflabs.trainmotion.util.FpsMeasurerTest;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
        FakeClockModule.class,
        FakeExecutorModule.class,
        FakeFileOpModule.class,
        MockHttpClientModule.class,
        JsonModule.class,
        LoggerModule.class,
        MockRandomModule.class,
        })
public interface ITrainMotionTestComponent extends
        AnalyticsTest._injector,
        FpsMeasurerTest._injector,
        PlaylistTest._injector,
        HighlighterTest._injector
    {

    @Component.Factory
    interface Factory {
        ITrainMotionTestComponent createComponent();
    }
}
