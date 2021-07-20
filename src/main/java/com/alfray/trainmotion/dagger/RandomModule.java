package com.alfray.trainmotion.dagger;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.Random;

@Module
public abstract class RandomModule {

    @Singleton
    @Provides
    public static Random provideRandom() {
        return new Random();
    }
}
