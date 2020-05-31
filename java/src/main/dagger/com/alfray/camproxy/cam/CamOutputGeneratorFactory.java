package com.alfray.camproxy.cam;

import com.alfray.camproxy.util.ILogger;
import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
public final class CamOutputGeneratorFactory {
  private final Provider<ILogger> loggerProvider;

  @Inject
  public CamOutputGeneratorFactory(Provider<ILogger> loggerProvider) {
    this.loggerProvider = checkNotNull(loggerProvider, 1);
  }

  public CamOutputGenerator create(CamInfo camInfo) {
    return new CamOutputGenerator(checkNotNull(loggerProvider.get(), 1), checkNotNull(camInfo, 2));
  }

  private static <T> T checkNotNull(T reference, int argumentIndex) {
    if (reference == null) {
      throw new NullPointerException(
          "@AutoFactory method argument is null but is not marked @Nullable. Argument index: "
              + argumentIndex);
    }
    return reference;
  }
}
