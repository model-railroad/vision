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
