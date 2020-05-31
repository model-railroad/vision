package com.alfray.camproxy;

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

  public CamProxy_MembersInjector(Provider<ILogger> mLoggerProvider) {
    this.mLoggerProvider = mLoggerProvider;
  }

  public static MembersInjector<CamProxy> create(Provider<ILogger> mLoggerProvider) {
    return new CamProxy_MembersInjector(mLoggerProvider);}

  @Override
  public void injectMembers(CamProxy instance) {
    injectMLogger(instance, mLoggerProvider.get());
  }

  @InjectedFieldSignature("com.alfray.camproxy.CamProxy.mLogger")
  public static void injectMLogger(CamProxy instance, ILogger mLogger) {
    instance.mLogger = mLogger;
  }
}
