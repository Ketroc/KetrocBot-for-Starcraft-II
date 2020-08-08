package com.ketroc.terranbot;

import com.ketroc.terranbot.bots.Bot;

public class GameResult {
    public static boolean wasNydusRushed;

    public static void setNydusRushed() {
        if (!wasNydusRushed && Bot.OBS.getGameLoop() < 15000) {
            wasNydusRushed = true;
        }
    }
}
