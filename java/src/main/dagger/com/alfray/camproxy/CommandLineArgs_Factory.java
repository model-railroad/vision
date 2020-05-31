package com.alfray.camproxy;

import com.alfray.camproxy.util.ILogger;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class CommandLineArgs_Factory implements Factory<CommandLineArgs> {
  private final Provider<ILogger> loggerProvider;

  public CommandLineArgs_Factory(Provider<ILogger> loggerProvider) {
    this.loggerProvider = loggerProvider;
  }

  @Override
  public CommandLineArgs get() {
    return newInstance(loggerProvider.get());
  }

  public static CommandLineArgs_Factory create(Provider<ILogger> loggerProvider) {
    return new CommandLineArgs_Factory(loggerProvider);
  }

  public static CommandLineArgs newInstance(ILogger logger) {
    return new CommandLineArgs(logger);
  }
}
