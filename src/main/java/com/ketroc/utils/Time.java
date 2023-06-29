package com.ketroc.utils;

import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;

public class Time {
    public static final double FRAMES_PER_SECOND = 22.4;
    public static final int NUM_FRAMES_PER_MINUTE = 1344;

    public static int toSeconds(long frames) {
        return (int)(frames / FRAMES_PER_SECOND);
    }

    public static long toFrames(int seconds) {
        long frames = (long) (seconds * FRAMES_PER_SECOND);
        frames -= frames % Launcher.STEP_SIZE;
        return frames;
    }

    public static int toSeconds(String time) {
        String[] arrTime = time.split(":");
        return Integer.parseInt(arrTime[0])*60 + Integer.parseInt(arrTime[1]);
    }

    public static long toFrames(String time) {
        return toFrames(toSeconds(time));
    }

    public static String toClock(int seconds) {
        return seconds/60 + ":" + String.format("%02d", seconds%60);
    }

    public static String toClock(long frames) {
        return toClock(toSeconds(frames));
    }

    public static String nowClock() {
        return toClock(nowFrames());
    }

    public static int nowSeconds() {
        return toSeconds(nowFrames());
    }

    public static long nowFrames() {
        return Bot.OBS.getGameLoop();
    }

    public static boolean at(long frame) {
        return nowFrames() >= frame && nowFrames() < frame + Launcher.STEP_SIZE;
    }

    public static boolean isFrameSkip(int i) {
        return nowFrames() % i == 0;
    }

    //returns true if within first numFrames of any numMinutes period
    public static boolean periodic(float numMinutes, int numFrames) {
        float periodFrame = nowFrames() % (NUM_FRAMES_PER_MINUTE * numMinutes);
        return periodFrame < numFrames;
    }

    //returns true if within first played frame of any numMinutes period
    public static boolean periodic(float numMinutes) {
        return periodic(numMinutes, Launcher.STEP_SIZE);
    }

    public static boolean after(long frame) {
        return Time.nowFrames() > frame;
    }

    public static boolean before(long frame) {
        return Time.nowFrames() < frame;
    }
}
