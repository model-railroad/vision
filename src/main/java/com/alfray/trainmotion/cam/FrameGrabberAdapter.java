package com.alfray.trainmotion.cam;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import javax.annotation.Nonnull;

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
