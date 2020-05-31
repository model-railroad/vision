package com.alfray.camproxy;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Logger {

    @Inject
    public Logger() {
    }

    public void log(String msg) {
        System.out.println(msg);
    }

    public void log(String tag, String msg) {
        log(tag + ": " + msg);
    }
}
