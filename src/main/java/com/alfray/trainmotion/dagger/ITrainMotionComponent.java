package com.alfray.trainmotion.dagger;

import com.alfray.trainmotion.TrainMotion;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = { LoggerModule.class, JsonModule.class })
public interface ITrainMotionComponent {

    void inject(TrainMotion camProxy);

    @Component.Factory
    interface Factory {
        ITrainMotionComponent createComponent( /* @BindsInstance ISomeProvider someProvider */);
    }

}
