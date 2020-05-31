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
public final class Cameras_Factory implements Factory<Cameras> {
  private final Provider<CamInfoFactory> camInfoFactoryProvider;

  private final Provider<ILogger> loggerProvider;

  public Cameras_Factory(Provider<CamInfoFactory> camInfoFactoryProvider,
      Provider<ILogger> loggerProvider) {
    this.camInfoFactoryProvider = camInfoFactoryProvider;
    this.loggerProvider = loggerProvider;
  }

  @Override
  public Cameras get() {
    return newInstance(camInfoFactoryProvider.get(), loggerProvider.get());
  }

  public static Cameras_Factory create(Provider<CamInfoFactory> camInfoFactoryProvider,
      Provider<ILogger> loggerProvider) {
    return new Cameras_Factory(camInfoFactoryProvider, loggerProvider);
  }

  public static Cameras newInstance(CamInfoFactory camInfoFactory, ILogger logger) {
    return new Cameras(camInfoFactory, logger);
  }
}
