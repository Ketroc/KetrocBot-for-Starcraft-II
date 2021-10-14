package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;

public class MineralShape extends Rectangle {
    public MineralShape(Point2d mineralPos) {
        super(mineralPos.getY()+0.975f, mineralPos.getY()-0.975f,
                mineralPos.getX()-1.475f, mineralPos.getX()+1.475f);
    }

    public MineralShape(UnitInPool mineral) {
        this(mineral.unit());
    }

    public MineralShape(Unit mineral) {
        this(mineral.getPosition().toPoint2d());
    }

}
