package com.alfray.camproxy.cam;

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
public final class CamInfoFactory {
  private final Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider;

  @Inject
  public CamInfoFactory(Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    this.camOutputGeneratorFactoryProvider = checkNotNull(camOutputGeneratorFactoryProvider, 1);
  }

  public CamInfo create(int index, CamConfig config) {
    return new CamInfo(
        checkNotNull(camOutputGeneratorFactoryProvider.get(), 1), index, checkNotNull(config, 3));
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
