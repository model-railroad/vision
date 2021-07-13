package com.alfray.trainmotion.util;

public abstract class ThreadLoop implements IStartStop {
    protected Thread mThread;
    protected volatile boolean mQuit;

    @Override
    public void start() throws Exception {
        if (mThread == null) {
            mThread = new Thread(this::_runInThread);
            mQuit = false;
            mThread.start();
        }
    }

    @Override
    public void stop() throws Exception {
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
