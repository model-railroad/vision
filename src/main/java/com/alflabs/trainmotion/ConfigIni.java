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

package com.alflabs.trainmotion;

import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.utils.FileOps;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Parses the config.ini.
 * <p/>
 * WARNING: Do NOT invoke the getters from dagger constructors! It's too early and the config file
 * has not been parsed yet.
 */
@Singleton
public class ConfigIni {
    private static final String TAG = ConfigIni.class.getSimpleName();

    public static final String DEFAULT_CONFIG_INI = "config.ini";

    private static final String KEY_CAM_URL = "cam%d_url";
    private static final String KEY_CAM_THRESHOLD = "cam%d_threshold";
    private static final String KEY_SPIKE_THRESHOLD = "spike_threshold";
    private static final String KEY_PlAYLIST_ID = "playlist_id";
    private static final String KEY_PlAYLIST_DIR = "playlist_dir";
    private static final String KEY_VOLUME_PERCENT = "volume_pct";
    private static final String KEY_WINDOW_TITLE = "window_title";
    private static final String KEY_WINDOW_MAXIMIZE = "window_maximize";
    private static final String KEY_ANALYTICS_ID = "analytics_id";
    private static final String KEY_DISPLAY_SCRIPT = "display_on_off_script";
    private static final String KEY_DISPLAY_OFF_HHMM = "display_off_hhmm";
    private static final String KEY_DISPLAY_ON_HHMM = "display_on_hhmm";
    private static final String KEY_KV_HOST_PORT = "kv_host_port";

    private final ILogger mLogger;
    private final FileOps mFileOps;
    private final Properties mProps = new Properties();
    private File mFile = new File("");

    @Inject
    public ConfigIni(ILogger logger, FileOps fileOps) {
        mLogger = logger;
        mFileOps = fileOps;
    }

    public void initialize(@Nonnull File file) {
        mProps.clear();
        mFile = file;
        try {
            mLogger.log(TAG, "Parsing " + file);
            Properties props = mFileOps.getProperties(file);
            mProps.putAll(props);
            mLogger.log(TAG, "Properties found: " + Arrays.toString(mProps.stringPropertyNames().stream().sorted().toArray()));
        } catch (IOException e) {
            mLogger.log(TAG, "Error parsing " + file + ": " + e);
        }
    }

    @Nonnull
    public File getFile() {
        return mFile;
    }

    /** Returns the URL configuration for cam1..cam3 if present. */
    @Nonnull
    public Optional<String> getCamUrlN(int index) {
        final String key = String.format(Locale.US, KEY_CAM_URL, index);
        if (mProps.containsKey(key)) {
            return Optional.of(mProps.getProperty(key));
        } else {
            return Optional.empty();
        }
    }

    /** Returns the threshold for cam1..cam3 if present. */
    public double getCamThresholdN(int index, double defaultThreshold) {
        final String key = String.format(Locale.US, KEY_CAM_THRESHOLD, index);
        String value = mProps.getProperty(key);
        if (!Strings.isNullOrEmpty(value)) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                mLogger.log(TAG, "Failed to parse value '" + value + "' for '" + key + "'");
            }
        }
        return defaultThreshold;
    }

    /** Returns the spike threshold if present. */
    public double getSpikeThreshold(double defaultThreshold) {
        final String key = KEY_SPIKE_THRESHOLD;
        String value = mProps.getProperty(key);
        if (!Strings.isNullOrEmpty(value)) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                mLogger.log(TAG, "Failed to parse value '" + value + "' for '" + key + "'");
            }
        }
        return defaultThreshold;
    }

    /** Returns the playlist_id or empty string if missing. */
    @Nonnull
    public String getPlaylistId() {
        return mProps.getProperty(KEY_PlAYLIST_ID, "");
    }

    /** Returns the playlist_dir or empty string if missing. */
    @Nonnull
    public String getPlaylistDir() {
        return mProps.getProperty(KEY_PlAYLIST_DIR, "");
    }

    /** Returns the volume percentage or the default value. */
    public int getVolumePct(int defaultValue) {
        String volPct = mProps.getProperty(KEY_VOLUME_PERCENT, "");
        if (!Strings.isNullOrEmpty(volPct)) {
            try {
                return Integer.parseInt(volPct);
            } catch (NumberFormatException e) {
                mLogger.log(TAG, "Failed to parse value '" + volPct + "' for '" + KEY_VOLUME_PERCENT + "'");
            }
        }
        return defaultValue;
    }

    public String getWindowTitle(String defaultValue) {
        return mProps.getProperty(KEY_WINDOW_TITLE, defaultValue);
    }

    public boolean getWindowMaximize() {
        return Boolean.parseBoolean(mProps.getProperty(KEY_WINDOW_MAXIMIZE, "false"));
    }

    @Nonnull
    public String getAnalyticsId() {
        return mProps.getProperty(KEY_ANALYTICS_ID, "").trim();
    }

    @Nonnull
    public String getKvHostPort() {
        return mProps.getProperty(KEY_KV_HOST_PORT, "").trim();
    }

    @Nonnull
    public String getDisplayScript() {
        return mProps.getProperty(KEY_DISPLAY_SCRIPT, "").trim();
    }

    /** Returns the display off <em>local</em> time, if the value can be parsed. */
    @Nonnull
    public Optional<LocalTime> getDisplayOffTime() {
        return parseLocalTime(KEY_DISPLAY_OFF_HHMM);
    }

    /** Returns the display on <em>local</em> time, if the value can be parsed. */
    @Nonnull
    public Optional<LocalTime> getDisplayOnTime() {
        return parseLocalTime(KEY_DISPLAY_ON_HHMM);
    }

    @Nonnull
    private Optional<LocalTime> parseLocalTime(@Nonnull String key) {
        String hhmm = mProps.getProperty(key, "").trim();
        try {
            return Optional.of(LocalTime.parse(hhmm));
        } catch (Throwable t) {
            mLogger.log(TAG, "Failed to parse value '" + hhmm + "' for '" + key + "'");
            return Optional.empty();
        }
    }
}
