package com.alfray.camproxy;

import com.alfray.camproxy.cam.CamerasProcessor;
import com.alfray.camproxy.util.ILogger;
import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
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
public final class CamProxy_MembersInjector implements MembersInjector<CamProxy> {
  private final Provider<ILogger> mLoggerProvider;

  private final Provider<CommandLineArgs> mCommandLineArgsProvider;

  private final Provider<CamerasProcessor> mCamerasProcessorProvider;

  public CamProxy_MembersInjector(Provider<ILogger> mLoggerProvider,
      Provider<CommandLineArgs> mCommandLineArgsProvider,
      Provider<CamerasProcessor> mCamerasProcessorProvider) {
    this.mLoggerProvider = mLoggerProvider;
    this.mCommandLineArgsProvider = mCommandLineArgsProvider;
    this.mCamerasProcessorProvider = mCamerasProcessorProvider;
  }

  public static MembersInjector<CamProxy> create(Provider<ILogger> mLoggerProvider,
      Provider<CommandLineArgs> mCommandLineArgsProvider,
      Provider<CamerasProcessor> mCamerasProcessorProvider) {
    return new CamProxy_MembersInjector(mLoggerProvider, mCommandLineArgsProvider, mCamerasProcessorProvider);}

  @Override
  public void injectMembers(CamProxy instance) {
    injectMLogger(instance, mLoggerProvider.get());
    injectMCommandLineArgs(instance, mCommandLineArgsProvider.get());
    injectMCamerasProcessor(instance, mCamerasProcessorProvider.get());
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mLogger")
  public static void injectMLogger(CamProxy instance, ILogger mLogger) {
    instance.mLogger = mLogger;
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mCommandLineArgs")
  public static void injectMCommandLineArgs(CamProxy instance, CommandLineArgs mCommandLineArgs) {
    instance.mCommandLineArgs = mCommandLineArgs;
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mCamerasProcessor")
  public static void injectMCamerasProcessor(CamProxy instance,
      CamerasProcessor mCamerasProcessor) {
    instance.mCamerasProcessor = mCamerasProcessor;
  }
}
