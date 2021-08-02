/*
 * Project: Conductor
 * Copyright (C) 2019 alf.labs gmail com,
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

package com.alfray.trainmotion.dagger;

import com.alfray.libutils.utils.FakeClock;
import com.alfray.libutils.utils.IClock;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class FakeClockModule {

    /** It is permanently 1:42 PM here. */
    public static final int HOUR = 13;
    /** It is permanently 1:42 PM here. */
    public static final int MINUTES = 42;
    /** It is permanently 1:42:43 PM here. */
    public static final int SECONDS = 43;

    @Singleton
    @Provides
    public static FakeClock provideFakeClock() {
        return new FakeClock(1000);
    }

    @Singleton
    @Provides
    public static IClock provideClock(FakeClock clock) {
        return clock;
    }

    // Lifted from Conductor. This is not needed in this project.
    //    @Singleton
    //    @Provides
    //    public static ILocalDateTimeNowProvider provideLocalDateTime() {
    //        return () -> {
    //            // It is permanently 1:42 PM here
    //            return LocalDateTime.of(1901, 2, 3, HOUR, MINUTES, SECONDS);
    //        };
    //    }
}
