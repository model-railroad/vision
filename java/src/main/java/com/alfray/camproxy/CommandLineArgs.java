package com.alfray.camproxy;

import com.alfray.camproxy.util.ILogger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.stream.Stream;

@Singleton
public class CommandLineArgs {
    private static final String TAG = CommandLineArgs.class.getSimpleName();

    public static final String OPT_HELP = "h";
    public static final String OPT_HTTP_PORT = "p";
    public static final String OPT_DEBUG_DISPLAY = "d";
    public static final String OPT_USER_VALUE = "u";

    private final ILogger mLogger;
    private final Options mOptions = new Options();
    private CommandLine mLine;

    @Inject
    public CommandLineArgs(ILogger logger) {
        mLogger = logger;

        mOptions.addOption(OPT_HELP, "help", false, "This usage help.");
        mOptions.addOption(OPT_DEBUG_DISPLAY, "debug", false, "Debug Display.");
        mOptions.addOption(Option.builder(OPT_HTTP_PORT)
                .longOpt("port")
                .hasArg()
                .type(Integer.class)
                .argName("port")
                .desc("Web server port")
                .build());
        mOptions.addOption(Option.builder(OPT_USER_VALUE)
                .longOpt("user")
                .hasArg()
                .argName("username")
                .desc("Default $U name")
                .build());
        Stream.of(1, 2, 3).forEach(i ->
                mOptions.addOption(Option.builder(Integer.toString(i))
                        .longOpt("pass" + i)
                        .hasArg()
                        .argName("password-" + i)
                        .desc("Password $P" + i)
                        .build()));
    }

    public void parse(@Nonnull String[] arguments) {
        DefaultParser parser = new DefaultParser();
        boolean error = false;
        try {
            mLine = parser.parse(mOptions, arguments);
        } catch (ParseException e) {
            mLogger.log(TAG, e.getLocalizedMessage());
            error = true;
        }

        if (error || mLine == null || mLine.hasOption(OPT_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("cam-proxy", mOptions);
            System.exit(1);
        }
    }

    public boolean hasOption(@Nonnull String optName) {
        return mLine != null && mLine.hasOption(optName);
    }

    public int getIntOption(@Nonnull String optName, int defVal) {
        final String defStr = Integer.toString(defVal);
        return mLine == null ? defVal : Integer.parseInt(mLine.getOptionValue(optName, defStr));
    }

    @Nonnull
    public String resolve(@Nonnull String input) {
        input = input.replaceAll("\\$U", mLine.getOptionValue(OPT_USER_VALUE, "camop"));

        for (int i = 1; i <= 3; i++) {
            if (mLine.hasOption(Integer.toString(i))) {
                input = input.replaceAll("\\$P" + i, mLine.getOptionValue(Integer.toString(i)));
            }
        }

        return input;
    }

}
