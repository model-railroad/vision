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
public final class CamInputGrabberFactory_Factory implements Factory<CamInputGrabberFactory> {
  private final Provider<ILogger> loggerProvider;

  public CamInputGrabberFactory_Factory(Provider<ILogger> loggerProvider) {
    this.loggerProvider = loggerProvider;
  }

  @Override
  public CamInputGrabberFactory get() {
    return newInstance(loggerProvider);
  }

  public static CamInputGrabberFactory_Factory create(Provider<ILogger> loggerProvider) {
    return new CamInputGrabberFactory_Factory(loggerProvider);
  }

  public static CamInputGrabberFactory newInstance(Provider<ILogger> loggerProvider) {
    return new CamInputGrabberFactory(loggerProvider);
  }
}
