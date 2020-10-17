package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;

import java.util.List;

public class ProxyHatchDefense {
    public static boolean isProxyHatch;
    public static UnitInPool hatchery;
    public static List<Unit> allScvList;

    public static void onStep() {
        setIsProxyHatch();
        if (isProxyHatch) {
            if (allScvList == null) {
                allScvList = WorkerManager.getAllScvUnits(LocationConstants.baseLocations.get(0));
            }
            Bot.ACTION.unitCommand(allScvList, Abilities.ATTACK, hatchery.unit(), false);
        }
    }

    private static void setIsProxyHatch() {
        if (!isProxyHatch) {
            if (Bot.OBS.getGameLoop() < 4000) {
                List<UnitInPool> hatcheryList =
                        UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.ZERG_HATCHERY, LocationConstants.baseLocations.get(1), 4);
                if (!hatcheryList.isEmpty()) {
                    hatchery = hatcheryList.get(0);
                    isProxyHatch = true;
                }
            }
        }
        else {
            isProxyHatch = hatchery.isAlive();
        }
    }
}
