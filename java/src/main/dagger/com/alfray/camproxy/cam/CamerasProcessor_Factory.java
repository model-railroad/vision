package com.alfray.camproxy.cam;

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
public final class CamerasProcessor_Factory implements Factory<CamerasProcessor> {
  private final Provider<ILogger> loggerProvider;

  public CamerasProcessor_Factory(Provider<ILogger> loggerProvider) {
    this.loggerProvider = loggerProvider;
  }

  @Override
  public CamerasProcessor get() {
    return newInstance(loggerProvider.get());
  }

  public static CamerasProcessor_Factory create(Provider<ILogger> loggerProvider) {
    return new CamerasProcessor_Factory(loggerProvider);
  }

  public static CamerasProcessor newInstance(ILogger logger) {
    return new CamerasProcessor(logger);
  }
}
