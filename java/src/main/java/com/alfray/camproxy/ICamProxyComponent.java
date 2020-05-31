package com.alfray.camproxy;

import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component
public interface ICamProxyComponent {

    void inject(CamProxy camProxy);

    @Component.Factory
    interface Factory {
        ICamProxyComponent createComponent( /* @BindsInstance ISomeProvider someProvider */);
    }

}
