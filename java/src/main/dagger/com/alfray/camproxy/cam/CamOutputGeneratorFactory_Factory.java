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
public final class CamOutputGeneratorFactory_Factory implements Factory<CamOutputGeneratorFactory> {
  private final Provider<ILogger> loggerProvider;

  public CamOutputGeneratorFactory_Factory(Provider<ILogger> loggerProvider) {
    this.loggerProvider = loggerProvider;
  }

  @Override
  public CamOutputGeneratorFactory get() {
    return newInstance(loggerProvider);
  }

  public static CamOutputGeneratorFactory_Factory create(Provider<ILogger> loggerProvider) {
    return new CamOutputGeneratorFactory_Factory(loggerProvider);
  }

  public static CamOutputGeneratorFactory newInstance(Provider<ILogger> loggerProvider) {
    return new CamOutputGeneratorFactory(loggerProvider);
  }
}
