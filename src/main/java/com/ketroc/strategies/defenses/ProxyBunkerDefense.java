package com.ketroc.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.micro.ScvAttackTarget;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.Chat;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.List;

public class ProxyBunkerDefense {
    public static boolean isProxyBunker;
    public static boolean isScvsSent;
    public static UnitInPool bunker;

    public static void onStep() {
        setIsProxyBunker();
        if (isProxyBunker) {
            if (!isScvsSent) {
                GameCache.baseList.get(0).getAndReleaseAvailableScvs(6)
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
                    Unit enemyBunker = enemyBunkerList.get(0).unit();
                    Chat.tag("VS_PROXY_BUNKER");
                    Chat.tag("VS_CHEESE");
                    if (enemyBunker.getBuildProgress() < 1 && enemyBunker.getDisplayType() != DisplayType.SNAPSHOT) {
                        bunker = enemyBunkerList.get(0);
                        isProxyBunker = true;
                    }
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
