package com.ketroc.terranbot;

import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.Time;

public class GameResult {
    public static boolean wasNydusRushed;

    public static void setNydusRushed() {
        if (!wasNydusRushed && Time.nowFrames() < Time.toFrames("7:00")) {
            wasNydusRushed = true;
        }
    }
}
