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
