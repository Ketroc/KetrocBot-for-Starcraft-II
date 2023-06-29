package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;

public class DefenseUnitPosition {
    private Point2d pos; //TODO: this doesn't handle lib range upgrade
    private UnitInPool unit;
    private Base base;

    public DefenseUnitPosition(Point2d pos, UnitInPool unit, Base base) {
        this.pos = pos;
        this.unit = unit;
        this.base = base;
    }

    public Base getBase() {
        return base;
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

    public void setUnit(UnitInPool newUnit, Base base) {
        if ((this.unit == null && newUnit != null && (newUnit.unit().getType() == Units.TERRAN_MISSILE_TURRET || newUnit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED)) || //turret/tank added
                (this.unit != null && newUnit == null && (this.unit.unit().getType() == Units.TERRAN_MISSILE_TURRET || this.unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED))) { //turret/tank cancelled/destroyed
            this.unit = newUnit;
            base.getMineralPatches().forEach(mineralPatch -> mineralPatch.initMiningPositions());
            base.getGases().forEach(gas -> gas.initMiningPositions());
        }
        else {
            this.unit = newUnit;
        }
    }
}
