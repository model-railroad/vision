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

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import javax.annotation.Nonnull;

/** {@link IFrameGrabber} interface wrapped around a {@link FFmpegFrameGrabber}. */
public class FrameGrabberAdapter implements IFrameGrabber {

    private final FFmpegFrameGrabber mFrameGrabber;

    public FrameGrabberAdapter(@Nonnull FFmpegFrameGrabber frameGrabber) {
        mFrameGrabber = frameGrabber;
    }

    public static IFrameGrabber of(@Nonnull FFmpegFrameGrabber frameGrabber) {
        return new FrameGrabberAdapter(frameGrabber);
    }

    @Override
    public void setOption(String key, String value) {
        mFrameGrabber.setOption(key, value);
    }

    @Override
    public void setTimeout(int timeout) {
        mFrameGrabber.setTimeout(timeout);
    }

    @Override
    public void start() throws FrameGrabber.Exception {
        mFrameGrabber.start();
    }

    @Override
    public int getPixelFormat() {
        return mFrameGrabber.getPixelFormat();
    }

    @Override
    public double getFrameRate() {
        return mFrameGrabber.getFrameRate();
    }

    @Override
    public int getImageWidth() {
        return mFrameGrabber.getImageWidth();
    }

    @Override
    public int getImageHeight() {
        return mFrameGrabber.getImageHeight();
    }

    @Override
    public Frame grabImage() throws FrameGrabber.Exception {
        return mFrameGrabber.grabImage();
    }

    @Override
    public void flush() throws FrameGrabber.Exception {
        mFrameGrabber.flush();
    }

    @Override
    public void stop() throws FrameGrabber.Exception {
        mFrameGrabber.stop();
    }

    @Override
    public void release() throws FrameGrabber.Exception {
        mFrameGrabber.release();
    }
}
