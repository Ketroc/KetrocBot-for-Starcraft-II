package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.List;

public class FlyingCC {
    // **************
    // *** FIELDS ***
    // **************

    public static List<FlyingCC> flyingCCs = new ArrayList<>();
    public UnitInPool unit;
    public Point2d destination;
    public boolean makeMacroOC;

    // ********************
    // *** CONSTRUCTORS ***
    // ********************

    public FlyingCC(UnitInPool unit, Point2d destination, boolean makeMacroOC) {
        this.unit = unit;
        this.destination = destination;
        this.makeMacroOC = makeMacroOC;
        PlacementMap.makeAvailable5x5(unit.unit().getPosition().toPoint2d());
        PlacementMap.makeUnavailable5x5(destination);
    }

    // ***************
    // *** METHODS ***
    // ***************

    public boolean isMoving() {
        return unit.unit().getFlying().orElse(false) && ActionIssued.getCurOrder(unit).isPresent();
    }
    public boolean hasLanded() {
        return unit.unit().getType() == Units.TERRAN_COMMAND_CENTER;
    }
    public boolean hasDied() {
        return !unit.isAlive();
    }

    public void keepCCMoving() {
        if (unit.unit().getFlying().orElse(false) && ActionIssued.getCurOrder(unit).isEmpty()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.LAND, destination, false);
        }
    }

    // **********************
    // *** STATIC METHODS ***
    // **********************

    public static void addFlyingCC(UnitInPool cc, Point2d destination, boolean makeMacroOC) {
        flyingCCs.add(new FlyingCC(cc, destination, makeMacroOC));
        ActionHelper.unitCommand(cc.unit(), Abilities.LIFT, false);
    }

    public static void addFlyingCC(Unit cc, Point2d destination, boolean makeMacroOC) {
        addFlyingCC(Bot.OBS.getUnit(cc.getTag()), destination, makeMacroOC);
    }

    public static void onStep() {
        for (int i=0; i<flyingCCs.size(); i++) {
            FlyingCC flyingCC = flyingCCs.get(i);
            //if cc died and was to become a macro OC
            if (flyingCC.hasDied() && flyingCC.makeMacroOC) {
                PosConstants.addMacroOcPos(flyingCC.destination);
            }
            if (flyingCC.hasDied() || flyingCC.hasLanded()) {
                flyingCCs.remove(i--);
                break;
            }
            flyingCC.keepCCMoving();
        }
    }
}
