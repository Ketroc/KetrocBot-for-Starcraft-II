package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;

import java.util.Optional;

public class DefenseUnitPositions {
    private Point2d pos;
    private UnitInPool unit;

    public DefenseUnitPositions(Point2d pos, UnitInPool unit) {
        this.pos = pos;
        this.unit = unit;
    }

    public Point2d getPos() {
        return pos;
    }

    public void setPos(Point2d pos) {
        this.pos = pos;
    }

    public Optional<UnitInPool> getUnit() {
        return Optional.ofNullable(unit);
    }

    public void setUnit(UnitInPool unit) {
        this.unit = unit;
    }
}
