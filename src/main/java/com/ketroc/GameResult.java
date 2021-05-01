package com.ketroc;

import com.ketroc.utils.Time;

public class GameResult {
    public static boolean wasNydusRushed;

    public static void setNydusRushed() {
        if (!wasNydusRushed && Time.nowFrames() < Time.toFrames("7:00")) {
            wasNydusRushed = true;
        }
    }
}
