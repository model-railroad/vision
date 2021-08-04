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

package com.alflabs.trainmotion.cam;

import javax.annotation.Nonnull;

/** Configuration data for one camera input stream. */
public class CamConfig {
    private final String mInputUrl;
    private final double mMotionThreshold;

    public CamConfig(@Nonnull String inputUrl, double motionThreshold) {
        mInputUrl = inputUrl;
        mMotionThreshold = motionThreshold;
    }

    @Nonnull
    public String getInputUrl() {
        return mInputUrl;
    }

    public double getMotionThreshold() {
        return mMotionThreshold;
    }
}
