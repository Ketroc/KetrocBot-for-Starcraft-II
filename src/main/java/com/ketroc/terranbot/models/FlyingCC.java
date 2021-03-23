package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.ActionHelper;
import com.ketroc.terranbot.utils.ActionIssued;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.PlacementMap;

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
        return unit.unit().getType() == Units.TERRAN_COMMAND_CENTER_FLYING && ActionIssued.getCurOrder(unit.unit()).isPresent();
    }
    public boolean hasLanded() {
        return unit.unit().getType() == Units.TERRAN_COMMAND_CENTER;
    }
    public boolean hasDied() {
        return !unit.isAlive();
    }

    public void keepCCMoving() {
        Unit cc = unit.unit();
        if (cc.getType() == Units.TERRAN_COMMAND_CENTER_FLYING && ActionIssued.getCurOrder(cc).isEmpty()) {
            ActionHelper.unitCommand(cc, Abilities.LAND, destination, false);
        }
    }

    // **********************
    // *** STATIC METHODS ***
    // **********************

    public static void addFlyingCC(UnitInPool cc, Point2d destination, boolean makeMacroOC) {
        flyingCCs.add(new FlyingCC(cc, destination, makeMacroOC));
        ActionHelper.unitCommand(cc.unit(), Abilities.LIFT_COMMAND_CENTER, false);
    }

    public static void addFlyingCC(Unit cc, Point2d destination, boolean makeMacroOC) {
        addFlyingCC(Bot.OBS.getUnit(cc.getTag()), destination, makeMacroOC);
    }

    public static void onStep() {
        for (int i=0; i<flyingCCs.size(); i++) {
            FlyingCC flyingCC = flyingCCs.get(i);
            //if cc died and was to become a macro OC
            if (flyingCC.hasDied() && flyingCC.makeMacroOC) {
                LocationConstants.MACRO_OCS.add(flyingCC.destination);
            }
            if (flyingCC.hasDied() || flyingCC.hasLanded()) {
                flyingCCs.remove(i--);
                break;
            }
            flyingCC.keepCCMoving();
        }
    }
}
