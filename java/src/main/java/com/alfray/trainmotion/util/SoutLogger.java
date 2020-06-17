package com.alfray.trainmotion.util;

public class SoutLogger implements ILogger {

    public SoutLogger() {
    }

    @Override
    public void log(String msg) {
        int n = msg.length();
        if (n > 0 && (msg.charAt(n-1) == '\r' || msg.charAt(n-1) == '\n')) {
            System.out.print(msg);
        } else {
            System.out.println(msg);
        }
    }

    @Override
    public void log(String tag, String msg) {
        log(tag + ": " + msg);
    }
}
