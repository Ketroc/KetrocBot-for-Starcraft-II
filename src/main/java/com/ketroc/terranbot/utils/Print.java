package com.ketroc.terranbot.utils;

public class Print {
    public static void print(Object message) {
        System.out.println(Time.nowClock() + ": " + message.toString());
    }
}
