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

import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** List of all camera input streams. */
@Singleton
public class Cameras implements IStartStop {
    private static final String TAG = Cameras.class.getSimpleName();

    private final List<CamInfo> mCamInfos = new ArrayList<>();
    private final CamInfoFactory mCamInfoFactory;
    private final ILogger mLogger;

    @Inject
    public Cameras(
            CamInfoFactory camInfoFactory,
            ILogger logger) {
        mCamInfoFactory = camInfoFactory;
        mLogger = logger;
    }

    public void add(@Nonnull CamConfig camConfig) {
        CamInfo info = mCamInfoFactory.create(1 + mCamInfos.size(), camConfig);
        mCamInfos.add(info);
        mLogger.log(TAG, "Add Cam #" + info.getIndex() + " for " + info.getConfig().getInputUrl());
    }

    public int count() {
        return mCamInfos.size();
    }

    public void forEachCamera(@Nonnull Consumer<CamInfo> consumer) {
        mCamInfos.forEach(consumer);
    }

    @Nullable
    public CamInfo getByIndex(int index) {
        for (CamInfo camInfo : mCamInfos) {
            if (camInfo.getIndex() == index) {
                return camInfo;
            }
        }
        return null;
    }

    @Override
    public void start() throws Exception {
        for (CamInfo camInfo : mCamInfos) {
            camInfo.getGrabber().start();
            camInfo.getAnalyzer().start();
        }
    }

    @Override
    public void stop() {
        for (CamInfo camInfo : mCamInfos) {
            try {
                camInfo.getAnalyzer().stop();
            } catch (Exception e) {
                mLogger.log(TAG, "Stopping analyzer-" + camInfo.getIndex() + ": " + e);
            }
            try {
                camInfo.getGrabber().stop();
            } catch (Exception e) {
                mLogger.log(TAG, "Stopping grab-" + camInfo.getIndex() + ": " + e);
            }
        }
    }
}
