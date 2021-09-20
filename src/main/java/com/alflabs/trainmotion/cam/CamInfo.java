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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import javax.annotation.Nonnull;

/** Live information about each camera: configuration, video grabber, analyzer. */
@AutoFactory
public class CamInfo {
    private final int mIndex;
    private final CamConfig mConfig;
    private final CamAnalyzer mAnalyzer;
    private final CamInputGrabber mGrabber;

    /** New camera info. Index is 1-based. */
    CamInfo(
            @Provided CamAnalyzerFactory camAnalyzerFactory,
            @Provided CamInputGrabberFactory camInputGrabberFactory,
            int index,
            @Nonnull CamConfig config) {
        mIndex = index;
        mConfig = config;
        mGrabber = camInputGrabberFactory.create(this);
        mAnalyzer = camAnalyzerFactory.create(this);
    }

    /** The 1-base index for this camera. */
    public int getIndex() {
        return mIndex;
    }

    @Nonnull
    public CamConfig getConfig() {
        return mConfig;
    }

    @Nonnull
    public CamInputGrabber getGrabber() {
        return mGrabber;
    }

    public CamAnalyzer getAnalyzer() {
        return mAnalyzer;
    }
}
