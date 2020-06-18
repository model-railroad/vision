package com.alfray.trainmotion.dagger;

import com.alfray.trainmotion.util.ILogger;
import com.alfray.trainmotion.util.SoutLogger;
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
