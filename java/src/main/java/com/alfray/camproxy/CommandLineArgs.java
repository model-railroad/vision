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

    private final ILogger mLogger;
    private final Options mOptions = new Options();
    private CommandLine mLine;

    @Inject
    public CommandLineArgs(ILogger logger) {
        mLogger = logger;

        mOptions.addOption("h", "help", false, "This usage help.");
        mOptions.addOption(Option.builder("u")
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

        if (error || mLine == null || mLine.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("cam-proxy", mOptions);
            System.exit(1);
        }
    }

    @Nonnull
    public String resolve(@Nonnull String input) {
        input = input.replaceAll("\\$U", mLine.getOptionValue("u", "camop"));

        for (int i = 1; i <= 3; i++) {
            if (mLine.hasOption(Integer.toString(i))) {
                input = input.replaceAll("\\$P" + i, mLine.getOptionValue(Integer.toString(i)));
            }
        }

        return input;
    }

}
