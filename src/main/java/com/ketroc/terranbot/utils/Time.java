package com.ketroc.terranbot.utils;

import com.ketroc.terranbot.bots.Bot;

public class Time {
    public static final double FRAMES_PER_SECOND = 22.4;

    public static int toSeconds(long frames) {
        return (int)(frames / FRAMES_PER_SECOND);
    }

    public static long toFrames(int seconds) {
        return (long)(seconds * FRAMES_PER_SECOND);
    }

    public static int toSeconds(String time) {
        String[] arrTime = time.split(":");
        return Integer.parseInt(arrTime[0])*60 + Integer.parseInt(arrTime[1]);
    }

    public static long toFrames(String time) {
        return toFrames(toSeconds(time));
    }

    public static String toTime(int seconds) {
        return seconds/60 + ":" + String.format("%02d", seconds%60);
    }

    public static String toTime(long frames) {
        return toTime(toSeconds(frames));
    }

    public static String getTime() {
        return toTime(getFrame());
    }

    public static int getSeconds() {
        return toSeconds(getFrame());
    }

    public static long getFrame() {
        return Bot.OBS.getGameLoop();
    }



}
