package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyBunkerDefense {
    public static boolean isProxyBunker;
    public static UnitInPool bunker;
    public static List<Unit> allScvList;

    public static void onStep() {
        setIsProxyBunker();
        if (isProxyBunker) {
            if (allScvList == null) {
                List<UnitInPool> allScvs = WorkerManager.getAllScvs(LocationConstants.baseLocations.get(0), 30);
                allScvList = allScvs.stream()
                        .sorted(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(1))))
                        .limit(5)
                        .map(UnitInPool::unit)
                        .collect(Collectors.toList());
            }
            Bot.ACTION.unitCommand(allScvList, Abilities.ATTACK, bunker.unit(), false);
        }
    }

    private static void setIsProxyBunker() {
        if (!isProxyBunker) {
            if (Time.nowFrames() < Time.toFrames("3:00")) {
                List<UnitInPool> bunkerList =
                        UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_BUNKER, LocationConstants.baseLocations.get(1), 10);
                if (!bunkerList.isEmpty()) {
                    bunker = bunkerList.get(0);
                    isProxyBunker = true;
                }
            }
        }
        else {
            isProxyBunker = bunker.isAlive();
        }
    }
}
