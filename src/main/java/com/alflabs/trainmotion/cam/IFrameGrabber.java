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

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

@Deprecated
public interface IFrameGrabber {
    void setOption(String key, String value);
    void setTimeout(int timeout);
    void start() throws FrameGrabber.Exception;
    int getPixelFormat();
    double getFrameRate();
    int getImageWidth();
    int getImageHeight();
    Frame grabImage() throws FrameGrabber.Exception;
    void flush() throws FrameGrabber.Exception;
    void stop() throws FrameGrabber.Exception;
    void release() throws FrameGrabber.Exception;
}
