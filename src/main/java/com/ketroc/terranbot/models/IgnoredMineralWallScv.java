package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.ActionHelper;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.Comparator;

public class IgnoredMineralWallScv extends Ignored {
    UnitInPool scv;
    public IgnoredMineralWallScv(Tag unitTag) {
        super(unitTag);
        scv = Bot.OBS.getUnit(unitTag);
        Base.releaseMineralScv(scv.unit());
    }

    @Override
    public boolean doReleaseUnit() {
        if (!scv.isAlive()) {
            addScv();
            return true;
        }
        else if (!scv.unit().getActive().orElse(true)) {
            return true;
        }
        return false;
    }

    public static void addScv() {
        if (numScvsActive() > 0) {
            return;
        }
        Unit mineral = UnitUtils.getClosestUnitOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_WALL_TYPE, LocationConstants.baseLocations.get(0));
        if (mineral != null && UnitUtils.getDistance(mineral, LocationConstants.baseLocations.get(0)) < 50) {
            UnitInPool scv = WorkerManager.getClosestAvailableScv(mineral.getPosition().toPoint2d());
            if (scv != null) {
                Ignored.add(new IgnoredMineralWallScv(scv.getTag()));
                ActionHelper.unitCommand(scv.unit(), Abilities.SMART, mineral, false);
            }
        }
    }

    private static int numScvsActive() {
        return (int)ignoredUnits.stream().filter(ignored -> ignored instanceof IgnoredMineralWallScv).count();
    }

}
