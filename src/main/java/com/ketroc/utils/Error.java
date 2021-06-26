package com.ketroc.utils;

public class Error {
    public static void onException(Exception e) {
        e.printStackTrace();
        Chat.tag("exception");
    }
}
