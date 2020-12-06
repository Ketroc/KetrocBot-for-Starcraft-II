package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyHatchDefense {
    public static boolean isProxyHatch;
    public static UnitInPool hatchery;
    public static List<Unit> allScvList;

    public static void onStep() {
        setIsProxyHatch();
        if (isProxyHatch) {
            if (allScvList == null) {
                List<UnitInPool> allScvs = WorkerManager.getAllScvs(LocationConstants.baseLocations.get(0), 30);
                allScvList = allScvs.stream()
                        .sorted(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(1))))
                        .limit(allScvs.size()-3)
                        .map(UnitInPool::unit)
                        .collect(Collectors.toList());
            }
            Bot.ACTION.unitCommand(allScvList, Abilities.ATTACK, hatchery.unit(), false);
        }
    }

    private static void setIsProxyHatch() {
        if (!isProxyHatch) {
            if (Time.nowFrames() < Time.toFrames("3:00")) {
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
