package com.alfray.camproxy.util;

public abstract class ThreadLoop {
    protected Thread mThread;
    protected volatile boolean mQuit;

    public void start() throws Exception {
        if (mThread == null) {
            mThread = new Thread(this::_runInThread);
            mQuit = false;
            mThread.start();
        }
    }

    public void stop() throws InterruptedException {
        if (mThread != null) {
            Thread t = mThread;
            mThread = null;
            mQuit = true;
            t.interrupt();
            t.join();
        }
    }

    private void _runInThread() {
        while (!mQuit) {
            _runInThreadLoop();
        }
    }

    protected abstract void _runInThreadLoop();

}
