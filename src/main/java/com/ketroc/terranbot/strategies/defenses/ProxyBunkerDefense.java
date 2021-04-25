package com.ketroc.terranbot.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.micro.ScvAttackTarget;
import com.ketroc.terranbot.micro.UnitMicroList;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyBunkerDefense {
    public static boolean isProxyBunker;
    public static boolean isScvsSent;
    public static UnitInPool bunker;

    public static void onStep() {
        setIsProxyBunker();
        if (isProxyBunker) {
            if (!isScvsSent) {
                GameCache.baseList.get(0).getAndReleaseAvailableScvs(4)
                        .forEach(scv -> UnitMicroList.add(new ScvAttackTarget(scv, bunker)));
                isScvsSent = true;
            }
        }
    }

    private static void setIsProxyBunker() {
        if (!isProxyBunker) {
            if (Time.nowFrames() < Time.toFrames("3:00")) {
                List<UnitInPool> enemyBunkerList =
                        UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_BUNKER, LocationConstants.baseLocations.get(1), 10);
                if (!enemyBunkerList.isEmpty()) {
                    bunker = enemyBunkerList.get(0);
                    isProxyBunker = true;
                }
            }
        }
        else {
            if (!bunker.isAlive()) {
                isProxyBunker = false;
                isScvsSent = false;
                bunker = null;
            }
        }
    }
}
