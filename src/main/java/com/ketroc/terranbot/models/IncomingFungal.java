package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;

public class IncomingFungal {
    Point2d position;
    long untilGameFrame;

    public IncomingFungal(Point2d position) {
        this.position = position;
        untilGameFrame = Bot.OBS.getGameLoop() + Strategy.FUNGAL_FRAMES;
    }

    public boolean isExpired() {
        return Bot.OBS.getGameLoop() >= untilGameFrame;
    }
}
