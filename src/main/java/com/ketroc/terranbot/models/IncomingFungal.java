package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.Time;

public class IncomingFungal {
    Point2d position;
    long untilGameFrame;

    public IncomingFungal(Point2d position) {
        this.position = position;
        untilGameFrame = Time.nowFrames() + Strategy.FUNGAL_FRAMES;
    }

    public boolean isExpired() {
        return Time.nowFrames() >= untilGameFrame;
    }
}
