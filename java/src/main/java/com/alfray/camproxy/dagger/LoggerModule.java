package com.alfray.camproxy.dagger;

import com.alfray.camproxy.util.ILogger;
import com.alfray.camproxy.util.SoutLogger;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class LoggerModule {

    @Provides
    @Singleton
    static ILogger providesLogger() {
        return new SoutLogger();
    }
}
