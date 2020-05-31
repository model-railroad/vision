package com.alfray.camproxy.cam;

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
public final class CamInfoFactory {
  private final Provider<CamInputGrabberFactory> camInputGrabberFactoryProvider;

  private final Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider;

  @Inject
  public CamInfoFactory(
      Provider<CamInputGrabberFactory> camInputGrabberFactoryProvider,
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    this.camInputGrabberFactoryProvider = checkNotNull(camInputGrabberFactoryProvider, 1);
    this.camOutputGeneratorFactoryProvider = checkNotNull(camOutputGeneratorFactoryProvider, 2);
  }

  public CamInfo create(int index, CamConfig config) {
    return new CamInfo(
        checkNotNull(camInputGrabberFactoryProvider.get(), 1),
        checkNotNull(camOutputGeneratorFactoryProvider.get(), 2),
        index,
        checkNotNull(config, 4));
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
