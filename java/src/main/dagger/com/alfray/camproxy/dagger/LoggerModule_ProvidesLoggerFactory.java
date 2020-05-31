package com.alfray.camproxy.dagger;

import com.alfray.camproxy.util.ILogger;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class LoggerModule_ProvidesLoggerFactory implements Factory<ILogger> {
  @Override
  public ILogger get() {
    return providesLogger();
  }

  public static LoggerModule_ProvidesLoggerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ILogger providesLogger() {
    return Preconditions.checkNotNull(LoggerModule.providesLogger(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final LoggerModule_ProvidesLoggerFactory INSTANCE = new LoggerModule_ProvidesLoggerFactory();
  }
}
