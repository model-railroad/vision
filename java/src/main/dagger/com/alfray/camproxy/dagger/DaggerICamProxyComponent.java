package com.alfray.camproxy.dagger;

import com.alfray.camproxy.CamProxy;
import com.alfray.camproxy.CamProxy_MembersInjector;
import com.alfray.camproxy.CommandLineArgs;
import com.alfray.camproxy.CommandLineArgs_Factory;
import com.alfray.camproxy.cam.CamerasProcessor;
import com.alfray.camproxy.cam.CamerasProcessor_Factory;
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

  private Provider<CommandLineArgs> commandLineArgsProvider;

  private Provider<CamerasProcessor> camerasProcessorProvider;

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
    this.commandLineArgsProvider = DoubleCheck.provider(CommandLineArgs_Factory.create(providesLoggerProvider));
    this.camerasProcessorProvider = DoubleCheck.provider(CamerasProcessor_Factory.create(providesLoggerProvider));
  }

  @Override
  public void inject(CamProxy camProxy) {
    injectCamProxy(camProxy);}

  @CanIgnoreReturnValue
  private CamProxy injectCamProxy(CamProxy instance) {
    CamProxy_MembersInjector.injectMLogger(instance, providesLoggerProvider.get());
    CamProxy_MembersInjector.injectMCommandLineArgs(instance, commandLineArgsProvider.get());
    CamProxy_MembersInjector.injectMCamerasProcessor(instance, camerasProcessorProvider.get());
    return instance;
  }

  private static final class Factory implements ICamProxyComponent.Factory {
    @Override
    public ICamProxyComponent createComponent() {
      return new DaggerICamProxyComponent();
    }
  }
}
