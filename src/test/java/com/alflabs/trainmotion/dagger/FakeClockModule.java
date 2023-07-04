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

package com.alflabs.trainmotion.dagger;

import com.alflabs.trainmotion.util.ILocalDateTimeNowProvider;
import com.alflabs.utils.FakeClock;
import com.alflabs.utils.IClock;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.time.LocalDateTime;

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

    @Singleton
    @Provides
    public static ILocalDateTimeNowProvider provideLocalDateTime(IClock clock) {
        return () -> {
            long nowMs = clock.elapsedRealtime();
            // If nowMs is in the range 23:59:59:999 and 01:01:01:999, we use
            // it to set the fake local date time. The max choice means actual epoch
            // values cannot be matched, and the min choice means FakeClock(1000) isn't
            // matched either.
            // To set this value, call clock.setNow(hhmmss999)
            if (nowMs % 1000 == 999) {
                long s = (nowMs /     1000) % 100;
                long m = (nowMs /   100000) % 100;
                long h = (nowMs / 10000000) % 100;
                if (s >= 1 && s <= 59
                        && m >= 1 && m <= 59
                        && h >= 1 && h <= 23) {
                    return LocalDateTime.of(1901, 2, 3, (int) h, (int) m, (int) s);
                }
            }

            // Otherwise by default it is permanently 1:42 PM here
            return LocalDateTime.of(1901, 2, 3, HOUR, MINUTES, SECONDS);
        };
    }
}
