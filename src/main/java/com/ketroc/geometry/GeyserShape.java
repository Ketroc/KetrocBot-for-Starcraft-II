package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;

public class GeyserShape extends Rectangle {
    public GeyserShape(Point2d geyserPos) {
        super(geyserPos, 1.975f);
    }

    public GeyserShape(UnitInPool geyser) {
        this(geyser.unit());
    }

    public GeyserShape(Unit geyser) {
        this(geyser.getPosition().toPoint2d());
    }

}
