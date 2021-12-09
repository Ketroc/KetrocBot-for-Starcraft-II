package com.ketroc.utils;

public class Error {
    public static void onException(Throwable e) {
        e.printStackTrace();
        Chat.tag("exception");
    }
}
