package com.ketroc.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.micro.ScvAttacker;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.Chat;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

public class ProxyBunkerDefense {
    public static boolean isProxyBunker;
    public static boolean isScvsSent;
    public static UnitInPool bunker;

    public static void onStep() {
        setIsProxyBunker();
        if (isProxyBunker) {
            //send initial scvs
            if (!isScvsSent) {
                GameCache.baseList.get(0).getAndReleaseAvailableScvs(6)
                        .forEach(scv -> UnitMicroList.add(new ScvAttacker(scv)));
                isScvsSent = true;
                return;
            }
        }
    }

    private static void setIsProxyBunker() {
        if (!isProxyBunker) {
            if (Time.nowFrames() < Time.toFrames("4:00")) {
                Bot.OBS.getUnits(Alliance.ENEMY, u -> u.unit().getType() == Units.TERRAN_BUNKER).stream()
                        .filter(u -> UnitUtils.isInMyMainOrNat(u.unit()) &&
                                u.unit().getBuildProgress() < 1 &&
                                u.unit().getDisplayType() != DisplayType.SNAPSHOT)
                        .findAny()
                        .ifPresent(u -> {
                            Chat.tag("VS_PROXY_BUNKER");
                            Chat.tag("VS_CHEESE");
                            bunker = u;
                            isProxyBunker = true;
                        });
            }
        }
        else {
            //cancel if bunker dies or completes without being near dead
            if (!bunker.isAlive() ||
                    (bunker.unit().getBuildProgress() == 1 &&
                            bunker.unit().getDisplayType() == DisplayType.VISIBLE &&
                            UnitUtils.getHealthPercentage(bunker.unit()) > 8)) {
                isProxyBunker = false;
                isScvsSent = false;
                bunker = null;
                UnitMicroList.removeAll(ScvAttacker.class);
            }
        }
    }
}
