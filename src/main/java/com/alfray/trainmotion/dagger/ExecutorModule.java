package com.alfray.trainmotion.dagger;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Module
public abstract class ExecutorModule {

    @Singleton
    @Provides
    @Named("SingleThreadExecutor")
    public static ScheduledExecutorService provideScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
