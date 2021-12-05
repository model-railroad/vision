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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.stream.Stream;

/**
 * Parses the command-line.
 * <p/>
 * WARNING: Do NOT invoke the getters from dagger constructors! It's too early and the command line
 * has not been parsed yet.
 */
@Singleton
public class CommandLineArgs {
    private static final String TAG = CommandLineArgs.class.getSimpleName();

    public static final String OPT_VERBOSE_LOG = "v";
    public static final String OPT_HELP = "h";
    public static final String OPT_USER_VALUE = "u";
    public static final String OPT_WEB_ROOT = "w";
    public static final String OPT_SIZE_WIDTH = "s";    // can't be w/width or p/pixels...
    public static final String OPT_CONFIG_INI = "c";
    public static final String OPT_MEDIA_DIR = "m";
    public static final String OPT_STATS_PATH = "a";    // can't be s/stats

    private final ILogger mLogger;
    private final Options mOptions = new Options();
    private CommandLine mLine;
    private boolean mParsed;

    @Inject
    public CommandLineArgs(ILogger logger) {
        mLogger = logger;

        mOptions.addOption(OPT_HELP, "help", false, "This usage help.");
        mOptions.addOption(OPT_VERBOSE_LOG, "verbose", false, "Verbose log.");
        mOptions.addOption(Option.builder(OPT_SIZE_WIDTH)
                .longOpt("size")
                .hasArg()
                .type(Integer.class)
                .argName("pixels")
                .desc("Size/width of the 16:9 camera feed (analysis+output).")
                .build());
        mOptions.addOption(Option.builder(OPT_WEB_ROOT)
                .longOpt("web-root")
                .hasArg()
                .argName("path")
                .desc("Absolute directory for web root (default: use jar embedded web root).")
                .build());
        mOptions.addOption(Option.builder(OPT_USER_VALUE)
                .longOpt("user")
                .hasArg()
                .argName("username")
                .desc("Default $U name.")
                .build());
        mOptions.addOption(Option.builder(OPT_CONFIG_INI)
                .longOpt("config")
                .hasArg()
                .argName("config.ini")
                .desc("Path for config file.")
                .build());
        mOptions.addOption(Option.builder(OPT_MEDIA_DIR)
                .longOpt("media")
                .hasArg()
                .argName("media/")
                .desc("Path for playlist media directory (default: use config file playlist_dir).")
                .build());
        mOptions.addOption(Option.builder(OPT_STATS_PATH)
                .longOpt("stats")
                .hasArg()
                .argName("stats.json")
                .desc("Path for JSON stats output (.json or .json.gz). Also enables stats collection.")
                .build());
        Stream.of(1, 2, 3).forEach(i ->
                mOptions.addOption(Option.builder(Integer.toString(i))
                        .longOpt("pass" + i)
                        .hasArg()
                        .argName("password-" + i)
                        .desc("Password $P" + i)
                        .build()));
    }

    public void initialize(@Nonnull String[] arguments) {
        mParsed = true;
        DefaultParser parser = new DefaultParser();
        boolean error = false;
        try {
            mLine = parser.parse(mOptions, arguments);
        } catch (ParseException e) {
            mLogger.log(TAG, e.getLocalizedMessage());
            error = true;
        }

        if (error) {
            mLogger.log(TAG, "Command line has error(s).");
        }
        mLogger.log(TAG, "Command line: " + mLine.getArgList());

        if (error || mLine == null || mLine.hasOption(OPT_HELP)) {
            showHelpAndExit();
        }
    }

    public void showHelpAndExit() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("train-motion", mOptions);
        System.exit(1);
    }

    /**
     * Indicates whether this boolean option has been defined.
     * <p/>
     * WARNING: Do NOT invoke from a dagger constructor! It's too early and the command line
     * has not been parsed yet.
     */
    public boolean hasOption(@Nonnull String optName) {
        if (!mParsed) throw new RuntimeException("Command-line has not been parsed yet.");
        return mLine != null && mLine.hasOption(optName);
    }

    /**
     * Returns a string option, with a suitable default.
     * <p/>
     * WARNING: Do NOT invoke from a dagger constructor! It's too early and the command line
     * has not been parsed yet.
     */
    @Nullable
    public String getStringOption(@Nonnull String optName, @Nullable String defStr) {
        if (!mParsed) throw new RuntimeException("Command-line has not been parsed yet.");
        return mLine == null ? defStr : mLine.getOptionValue(optName, defStr);
    }

    /**
     * Returns an integer option, with a suitable default.
     * <p/>
     * WARNING: Do NOT invoke from a dagger constructor! It's too early and the command line
     * has not been parsed yet.
     */
    public int getIntOption(@Nonnull String optName, int defVal) {
        if (!mParsed) throw new RuntimeException("Command-line has not been parsed yet.");
        final String defStr = Integer.toString(defVal);
        return mLine == null ? defVal : Integer.parseInt(mLine.getOptionValue(optName, defStr));
    }

    @Nonnull
    public String resolve(@Nonnull String input) {
        input = input.replaceAll("\\$U", mLine.getOptionValue(OPT_USER_VALUE, "<missing-user>"));
        if (input.contains("<missing-user>")) {
            throw new RuntimeException("Command-line missing required --user parameter");
        }

        for (int i = 1; i <= 3; i++) {
            input = input.replaceAll("\\$P" + i, mLine.getOptionValue(Integer.toString(i), "<missing>"));
            if (input.contains("<missing>")) {
                throw new RuntimeException("Command-line missing required -" + i + " parameter");
            }
        }

        return input;
    }

}
