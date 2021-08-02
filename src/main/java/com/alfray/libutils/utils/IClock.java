/*
 * Project: Lib Utils
 * Copyright (C) 2017 alf.labs gmail com,
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

package com.alfray.libutils.utils;

public interface IClock {

    /**
     * Returns {@code SystemClock#elapsedRealtime()}:
     * On Android, returns milliseconds since boot, including time spent in sleep.
     */
    long elapsedRealtime();

    /**
     * Returns {@code SystemClock#uptimeMillis()}:
     * On Android, returns milliseconds since boot, not counting time spent in deep sleep.
     */
    long uptimeMillis();

    /**
     * Calls {@link Thread#sleep(long)}, ignoring interrupted exceptions
     * (if you really need the interrupted state, then use {@link #sleepWithInterrupt(long)}).
     *
     * @see Thread#sleep(long)
     */
    void sleep(long sleepTimeMs);

    /**
     * Calls {@link Thread#sleep(long)}.
     * This passed the {@link InterruptedException} along as needed.
     *
     * @see Thread#sleep(long)
     */
    void sleepWithInterrupt(long sleepTimeMs) throws InterruptedException;
}
