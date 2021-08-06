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

package com.alflabs.trainmotion.util;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;

public class ThreadLoopTest {

    @Before
    public void setUp() {
    }

    @Test
    public void testStartStop_NoQuit() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicInteger iterations = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(42);
        ThreadLoop threadLoop = new ThreadLoop() {
            @Override
            public void start() throws Exception {
                super.start();
                started.set(true);
            }

            @Override
            public void stop() throws Exception {
                stopped.set(true);
                super.stop();
            }

            @Override
            protected void _runInThreadLoop() {
                iterations.incrementAndGet();
                latch.countDown();
                try {
                    Thread.sleep(1 /*ms*/);
                } catch (InterruptedException ignore) {}
            }
        };

        threadLoop.start();
        latch.await();
        threadLoop.stop();
        assertThat(iterations.get()).isEqualTo(42);
        assertThat(started.get()).isTrue();
        assertThat(stopped.get()).isTrue();
    }

    @Test
    public void testStartAndQuit() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicInteger iterations = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        ThreadLoop threadLoop = new ThreadLoop() {
            @Override
            public void start() throws Exception {
                super.start();
                started.set(true);
            }

            @Override
            public void stop() throws Exception {
                stopped.set(true);
                super.stop();
            }

            @Override
            protected void _runInThreadLoop() {
                if (iterations.incrementAndGet() == 42) {
                    mQuit = true;
                    latch.countDown();
                }
            }
        };

        threadLoop.start();
        latch.await();
        threadLoop.stop();
        assertThat(iterations.get()).isEqualTo(42);
        assertThat(started.get()).isTrue();
        assertThat(stopped.get()).isTrue();
    }
}
