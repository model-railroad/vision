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

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.Random;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Module
public abstract class MockRandomModule {
    @Singleton
    @Provides
    public static Random provideRandom() {
        Random m = mock(Random.class);
        when(m.nextInt()).thenReturn(42);
        when(m.nextInt(anyInt())).thenAnswer(invocation -> {
            int maxRange = (int) invocation.getArgument(0);
            return 17 % maxRange;
        });
        return m;
    }
}
