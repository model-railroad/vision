package com.alflabs.trainmotion.cam;

import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.utils.IClock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Singleton
public class FakeInputStreamProvider {
    private static final String TAG = FakeInputStreamProvider.class.getSimpleName();
    private static final int MAX_BUF_LEN = 4096;
    private final String[] mFilenames = new String[] { "cam_4.mp4", "cam_5.mp4", "cam_6.mp4", "cam_7.mp4"  };
    private final int[] mSpeedBytePerSec = new int[] { 189000, 120000, 157000, 184000 };
    private final File mDir;
    private final ILogger mLogger;
    private final IClock mClock;

    @Inject
    public FakeInputStreamProvider(ILogger logger, IClock clock) {
        mLogger = logger;
        mClock = clock;
        mDir = new File("src/test/resources/cam_records".replace('/', File.separatorChar));
    }

    public InputStream create() {
        return createFromFileIS();
    }

    private static int sIndex = 0;
    private InputStream createFromFileIS() {
        File f = new File(mDir, mFilenames[sIndex]);
        sIndex = (sIndex + 1) % mFilenames.length;
        mLogger.log(TAG, "STATIC Read from " + f);
        try {
            return new FileInputStream(f);
        } catch (FileNotFoundException e) {
            mLogger.log(TAG, e.toString());
            throw new RuntimeException(e);
        }
    }

    private InputStream createFromUrlConnection() {
        String uri = "rtsp://username:password@192.168.3.117:554/ipcam_h264.sdp";
        try {
            URL url = new URL(uri);
            URLConnection connection = url.openConnection();
            connection.connect();
            return connection.getInputStream();
        } catch (Throwable t) {
            mLogger.log(TAG, t.toString());
            throw new RuntimeException(t);
        }
    }

    private InputStream createFromWrapper() {
        InputStream is = new FInputStream(sIndex);
        sIndex = (sIndex + 1) % mFilenames.length;
        return is;
    }


    private class FInputStream extends InputStream {
        private final int mStart;
        private int mIndex;
        private int mSpeed;
        private FileInputStream mStream;
//        private FileInputStream mMarkStream;

        public FInputStream(int start) {
            mStart = start;
            mLogger.log(TAG, "Create for index " + start + " --> " + this);
        }

        void _start() throws FileNotFoundException {
            if (mStream == null) {
                File f = new File(mDir, mFilenames[mIndex]);
                mSpeed = mSpeedBytePerSec[mIndex];
                mIndex = (mIndex + 1) % mFilenames.length;
                mLogger.log(TAG, mStart + " Open file " + f);
                mStream = new FileInputStream(f);
            }
        }

        @Override
        public int available() {
            return MAX_BUF_LEN;
        }

        @Override
        public int read() throws IOException {
            _start();
            int b = mStream.read();
//            if (b == -1) {
//                closeCurrent();
//                _start();
//                b = mStream.read();
//            }
            mLogger.log(TAG, mStart + " -- Read 0: " + b);
            return b;
        }

        @Override
        public int read(byte[] bytes) throws IOException {
            int len = bytes.length;
            if (len > MAX_BUF_LEN) {
                len = MAX_BUF_LEN;
            }
            _start();
            int b = mStream.read(bytes, 0, len);
//            if (b == -1) {
//                closeCurrent();
//                _start();
//                b = mStream.read(bytes, 0, len);
//            }
            long delay = 0;
            if (b > 0 && mSpeed > 0) {
                delay = (1000L * b) / mSpeed;
                if (delay > 0) {
                    mClock.sleep(delay);
                }
            }
            mLogger.log(TAG, mStart + " -- Read 1 ( -- x " + len + "): " + b + " -- in " + delay + " ms");
            return b;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            if (len > MAX_BUF_LEN) {
                len = MAX_BUF_LEN;
            }
            _start();
            int b = mStream.read(bytes, off, len);
//            if (b == -1) {
//                closeCurrent();
//                _start();
//                b = mStream.read(bytes, off, len);
//            }

            long delay = 0;
            if (b > 0 && mSpeed > 0) {
                delay = (1000L * b) / mSpeed;
                if (delay > 0) {
                    mClock.sleep(delay);
                }
            }
            mLogger.log(TAG, mStart + " -- Read 2 (" + off + " x " + len + "): " + b + " -- in " + delay + " ms");
            return b;
        }

        @Override
        public long skip(long l) throws IOException {
            mLogger.log(TAG, mStart + " -- Skip " + l);
            if (l < Long.MAX_VALUE && mStream != null) {
                return mStream.skip(l);
            }
            return 0;
        }

        @Override
        public synchronized void reset() throws IOException {
            mLogger.log(TAG, mStart + " -- Reset");
            throw new IOException("mark/reset not supported");
//            closeCurrent();
//            mMarkStream.reset();
//            mStream = mMarkStream;
//            mMarkStream = null;
        }

        @Override
        public synchronized void mark(int readLimit) {
            mLogger.log(TAG, mStart + " -- Mark " + readLimit);
//            try {
//                _start();
//            } catch (FileNotFoundException e) {
//                mLogger.log(TAG, e.toString());
//            }
//            mLogger.log(TAG, mStart + " -- Mark " + readLimit);
//            if (mStream != null) {
//                if (mMarkStream != null && mMarkStream != mStream) {
//                    try {
//                        closeMark();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                mMarkStream = mStream;
//                mMarkStream.mark(readLimit);
//            }
        }

        @Override
        public boolean markSupported() {
            mLogger.log(TAG, mStart + " -- MarkSupported = " + false);
            return false;
//            try {
//                _start();
//            } catch (FileNotFoundException e) {
//                mLogger.log(TAG, e.toString());
//            }
//            boolean supported = mStream != null && mStream.markSupported();
//            mLogger.log(TAG, mStart + " -- MarkSupported = " + supported);
//            return supported;
        }

        @Override
        public void close() throws IOException {
            mLogger.log(TAG, mStart + " -- Close");
            closeCurrent();
//            closeMark();
            super.close();
        }

        private void closeCurrent() throws IOException {
//            if (mMarkStream != null && mMarkStream == mStream) {
//                mStream = null;
//            } else
            if (mStream != null) {
                mStream.close();
                mStream = null;
            }
        }

//        private void closeMark() throws IOException {
//            if (mMarkStream != null) {
//                mMarkStream.close();
//                mMarkStream = null;
//            }
//        }
    }
}
