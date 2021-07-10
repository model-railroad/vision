package com.alfray.trainmotion;

import com.alfray.trainmotion.util.ILogger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

@Singleton
public class IniFileReader {
    private static final String TAG = IniFileReader.class.getSimpleName();

    public static final String DEFAULT_CONFIG_INI = "config.ini";

    private final ILogger mLogger;
    private final Properties mProps = new Properties();
    private File mFile = new File("");

    @Inject
    public IniFileReader(ILogger logger) {
        mLogger = logger;
    }

    public void readFile(@Nonnull File file) {
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
        final String key = "cam" + index;
        if (mProps.containsKey(key)) {
            return Optional.of(mProps.getProperty(key));
        } else {
            return Optional.empty();
        }
    }
}
