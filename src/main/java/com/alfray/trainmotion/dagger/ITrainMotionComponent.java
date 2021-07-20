package com.alfray.trainmotion.dagger;

import com.alfray.trainmotion.TrainMotion;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
        ExecutorModule.class,
        HttpClientModule.class,
        JsonModule.class,
        LoggerModule.class,
        RandomModule.class,
        })
public interface ITrainMotionComponent {

    void inject(TrainMotion camProxy);

    @Component.Factory
    interface Factory {
        ITrainMotionComponent createComponent( /* @BindsInstance ISomeProvider someProvider */);
    }

}
