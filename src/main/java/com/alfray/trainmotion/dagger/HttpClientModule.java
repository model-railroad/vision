package com.alfray.trainmotion.dagger;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;

import javax.inject.Singleton;

@Module
public abstract class HttpClientModule {

    @Singleton
    @Provides
    public static OkHttpClient provideOkHttpClient() {
        return new OkHttpClient();
    }
}
