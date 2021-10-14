package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;

import java.util.Optional;

public class DefenseUnitPositions {
    private Point2d pos; //TODO: this doesn't handle lib range upgrade
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

    public UnitInPool getUnit() {
        return unit;
    }

    public void setUnit(UnitInPool unit, Base base) {
        if ((this.unit == null && unit != null && unit.unit().getType() == Units.TERRAN_MISSILE_TURRET) || //turret added
                (this.unit != null && unit == null && this.unit.unit().getType() == Units.TERRAN_MISSILE_TURRET)) { //turret cancelled/destroyed
            base.getMineralPatches().forEach(mineralPatch -> mineralPatch.initMiningPositions());
        }
        this.unit = unit;
    }
}
