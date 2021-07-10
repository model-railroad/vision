package com.alfray.trainmotion.cam;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

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
