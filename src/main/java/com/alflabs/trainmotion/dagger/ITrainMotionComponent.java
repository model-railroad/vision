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

import com.alflabs.trainmotion.TrainMotion;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
        ClockModule.class,
        ExecutorModule.class,
        HttpClientModule.class,
        JsonModule.class,
        LoggerModule.class,
        RandomModule.class,
        })
public interface ITrainMotionComponent {

    void inject(TrainMotion camProxy);

    @Component.Factory
    interface Factory {
        ITrainMotionComponent createComponent( /* @BindsInstance ISomeProvider someProvider */);
    }

}
