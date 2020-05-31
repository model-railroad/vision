package com.alfray.camproxy;

import com.alfray.camproxy.cam.Cameras;
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

  private final Provider<Cameras> mCamerasProvider;

  public CamProxy_MembersInjector(Provider<ILogger> mLoggerProvider,
      Provider<CommandLineArgs> mCommandLineArgsProvider, Provider<Cameras> mCamerasProvider) {
    this.mLoggerProvider = mLoggerProvider;
    this.mCommandLineArgsProvider = mCommandLineArgsProvider;
    this.mCamerasProvider = mCamerasProvider;
  }

  public static MembersInjector<CamProxy> create(Provider<ILogger> mLoggerProvider,
      Provider<CommandLineArgs> mCommandLineArgsProvider, Provider<Cameras> mCamerasProvider) {
    return new CamProxy_MembersInjector(mLoggerProvider, mCommandLineArgsProvider, mCamerasProvider);}

  @Override
  public void injectMembers(CamProxy instance) {
    injectMLogger(instance, mLoggerProvider.get());
    injectMCommandLineArgs(instance, mCommandLineArgsProvider.get());
    injectMCameras(instance, mCamerasProvider.get());
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mLogger")
  public static void injectMLogger(CamProxy instance, ILogger mLogger) {
    instance.mLogger = mLogger;
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mCommandLineArgs")
  public static void injectMCommandLineArgs(CamProxy instance, CommandLineArgs mCommandLineArgs) {
    instance.mCommandLineArgs = mCommandLineArgs;
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mCameras")
  public static void injectMCameras(CamProxy instance, Cameras mCameras) {
    instance.mCameras = mCameras;
  }
}
