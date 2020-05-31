package com.alfray.camproxy.dagger;

import com.alfray.camproxy.CamProxy;
import com.alfray.camproxy.CamProxy_MembersInjector;
import com.alfray.camproxy.util.ILogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.DoubleCheck;
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
public final class DaggerICamProxyComponent implements ICamProxyComponent {
  private Provider<ILogger> providesLoggerProvider;

  private DaggerICamProxyComponent() {

    initialize();
  }

  public static ICamProxyComponent.Factory factory() {
    return new Factory();
  }

  public static ICamProxyComponent create() {
    return new Factory().createComponent();
  }

  @SuppressWarnings("unchecked")
  private void initialize() {
    this.providesLoggerProvider = DoubleCheck.provider(LoggerModule_ProvidesLoggerFactory.create());
  }

  @Override
  public void inject(CamProxy camProxy) {
    injectCamProxy(camProxy);}

  @CanIgnoreReturnValue
  private CamProxy injectCamProxy(CamProxy instance) {
    CamProxy_MembersInjector.injectMLogger(instance, providesLoggerProvider.get());
    return instance;
  }

  private static final class Factory implements ICamProxyComponent.Factory {
    @Override
    public ICamProxyComponent createComponent() {
      return new DaggerICamProxyComponent();
    }
  }
}
