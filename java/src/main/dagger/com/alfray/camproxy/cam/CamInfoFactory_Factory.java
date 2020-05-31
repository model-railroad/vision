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
  private final Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider;

  public CamInfoFactory_Factory(
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    this.camOutputGeneratorFactoryProvider = camOutputGeneratorFactoryProvider;
  }

  @Override
  public CamInfoFactory get() {
    return newInstance(camOutputGeneratorFactoryProvider);
  }

  public static CamInfoFactory_Factory create(
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    return new CamInfoFactory_Factory(camOutputGeneratorFactoryProvider);
  }

  public static CamInfoFactory newInstance(
      Provider<CamOutputGeneratorFactory> camOutputGeneratorFactoryProvider) {
    return new CamInfoFactory(camOutputGeneratorFactoryProvider);
  }
}
