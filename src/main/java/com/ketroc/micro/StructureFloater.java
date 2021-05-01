package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.*;

public class StructureFloater extends BasicUnitMicro {
    public boolean doLand;
    public StructureFloater(Unit structure) {
        super(Bot.OBS.getUnit(structure.getTag()), Position.toNearestHalfPoint(structure.getPosition().toPoint2d()), MicroPriority.SURVIVAL);
        if (!structure.getFlying().orElse(false)) {
            ActionHelper.unitCommand(structure, Abilities.LIFT, false);
        }
        doLand = true;
    }

    public StructureFloater(UnitInPool structure, Point2d targetPos, boolean doLand) {
        super(structure, targetPos, MicroPriority.SURVIVAL);
        if (!structure.unit().getFlying().orElse(false)) {
            ActionHelper.unitCommand(structure.unit(), Abilities.LIFT, false);
        }
        this.doLand = doLand;
    }

    @Override
    public void onArrival() {
        if (!doLand) {
            return;
        }
        if (unit.unit().getFlying().orElse(true)) {
            if (UnitUtils.getOrder(unit.unit()) != Abilities.LAND) {
                ActionHelper.unitCommand(unit.unit(), Abilities.LAND, targetPos, false);
            }
        }
        else {
            removeMe = true;
        }
    }

    @Override
    public void onDeath() {
        removeMe = true;
        switch ((Units)unit.unit().getType()) {
            case TERRAN_ORBITAL_COMMAND_FLYING: case TERRAN_COMMAND_CENTER_FLYING:
                Placement.possibleCcPosList.add(targetPos);
                break;
            case TERRAN_BARRACKS_FLYING:
                LocationConstants._3x3Structures.add(targetPos);
                break;
            case TERRAN_FACTORY_FLYING:
                LocationConstants.FACTORIES.add(targetPos);
                break;
            case TERRAN_STARPORT_FLYING:
                LocationConstants.STARPORTS.add(targetPos);
                break;
        }
    }
}
