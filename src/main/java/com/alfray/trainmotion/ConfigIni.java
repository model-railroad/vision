package com.alfray.trainmotion;

import com.alfray.trainmotion.util.ILogger;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.BitSet;
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

    private static final String KEY_CAM = "cam%d";
    private static final String KEY_PlAYLIST_ID = "playlist_id";
    private static final String KEY_PlAYLIST_DIR = "playlist_dir";
    private static final String KEY_VOLUME_PERCENT = "volume_pct";
    private static final String KEY_WINDOW_TITLE = "window_title";
    private static final String KEY_WINDOW_MAXIMIZE = "window_maximize";
    private static final String KEY_ANALYTICS_ID = "analytics_id";

    private final ILogger mLogger;
    private final Properties mProps = new Properties();
    private File mFile = new File("");

    @Inject
    public ConfigIni(ILogger logger) {
        mLogger = logger;
    }

    public void initialize(@Nonnull File file) {
        mProps.clear();
        mFile = file;
        try {
            mLogger.log(TAG, "Parsing " + file);
            FileInputStream stream = new FileInputStream(file);
            mProps.load(stream);
            mLogger.log(TAG, "Properties found: " + mProps.stringPropertyNames().stream().sorted().toArray());
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
    public Optional<String> getCamN(int index) {
        final String key = String.format(Locale.US, KEY_CAM, index);
        if (mProps.containsKey(key)) {
            return Optional.of(mProps.getProperty(key));
        } else {
            return Optional.empty();
        }
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
                mLogger.log(TAG, "Failed to parse volume percentage from '" + volPct + "'");
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
}
