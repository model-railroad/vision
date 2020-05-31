package com.alfray.camproxy.cam;

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
public final class CamInfoFactory_Factory implements Factory<CamInfoFactory> {
  private final Provider<CamInputGrabberFactory> camInputGrabberFactoryProvider;

  private final Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider;

  public CamInfoFactory_Factory(Provider<CamInputGrabberFactory> camInputGrabberFactoryProvider,
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    this.camInputGrabberFactoryProvider = camInputGrabberFactoryProvider;
    this.camOutputGeneratorFactoryProvider = camOutputGeneratorFactoryProvider;
  }

  @Override
  public CamInfoFactory get() {
    return newInstance(camInputGrabberFactoryProvider, camOutputGeneratorFactoryProvider);
  }

  public static CamInfoFactory_Factory create(
      Provider<CamInputGrabberFactory> camInputGrabberFactoryProvider,
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    return new CamInfoFactory_Factory(camInputGrabberFactoryProvider, camOutputGeneratorFactoryProvider);
  }

  public static CamInfoFactory newInstance(
      Provider<CamInputGrabberFactory> camInputGrabberFactoryProvider,
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    return new CamInfoFactory(camInputGrabberFactoryProvider, camOutputGeneratorFactoryProvider);
  }
}
