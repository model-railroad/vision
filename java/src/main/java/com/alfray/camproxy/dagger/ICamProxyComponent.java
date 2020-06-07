package com.alfray.camproxy.dagger;

import com.alfray.camproxy.CamProxy;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = { LoggerModule.class, JsonModule.class })
public interface ICamProxyComponent {

    void inject(CamProxy camProxy);

    @Component.Factory
    interface Factory {
        ICamProxyComponent createComponent( /* @BindsInstance ISomeProvider someProvider */);
    }

}
