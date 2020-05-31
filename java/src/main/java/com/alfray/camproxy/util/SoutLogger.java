package com.alfray.camproxy.util;

public class SoutLogger implements ILogger {

    public SoutLogger() {
    }

    @Override
    public void log(String msg) {
        System.out.println(msg);
    }

    @Override
    public void log(String tag, String msg) {
        log(tag + ": " + msg);
    }
}
