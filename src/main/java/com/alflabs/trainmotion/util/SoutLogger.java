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

public class SoutLogger implements ILogger {

    public SoutLogger() {
    }

    @Override
    public void log(String msg) {
        int n = msg.length();
        if (n > 0 && (msg.charAt(n-1) == '\r' || msg.charAt(n-1) == '\n')) {
            System.out.print(msg);
        } else {
            System.out.println(msg);
        }
    }

    @Override
    public void log(String tag, String msg) {
        log(tag + ": " + msg);
    }
}
