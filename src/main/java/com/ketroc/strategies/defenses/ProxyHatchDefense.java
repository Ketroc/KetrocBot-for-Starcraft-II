package com.ketroc.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.ScvAttackTarget;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.*;

public class ProxyHatchDefense {
    public static boolean isProxyHatch;
    public static UnitInPool hatchery;

    public static void onStep() {
        setIsProxyHatch();
        if (isProxyHatch) {
            if (!ScvAttackTarget.contains(hatchery.getTag())) {
                Point2d hatcheryPos = hatchery.unit().getPosition().toPoint2d();
                int numScvs = 6 + (int)(hatchery.unit().getBuildProgress() * 10);
                for (int i=0; i<numScvs; i++) {
                    UnitInPool scv = WorkerManager.getScvEmptyHands(hatcheryPos);
                    if (scv == null) {
                        break;
                    }
                    UnitMicroList.add(new ScvAttackTarget(scv, hatchery));
                }
            }
        }
    }

    private static void setIsProxyHatch() {
        if (!isProxyHatch) {
            if (Time.nowFrames() < Time.toFrames("3:00")) {
                UnitInPool closestHatchery = UnitUtils.getClosestEnemyUnitOfType(Units.ZERG_HATCHERY, PosConstants.baseLocations.get(1));
                if (closestHatchery != null &&
                        closestHatchery.unit().getBuildProgress() < 0.8 &&
                        (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, closestHatchery.unit().getPosition().toPoint2d()) ||
                                InfluenceMaps.getValue(InfluenceMaps.pointInNat, closestHatchery.unit().getPosition().toPoint2d()))) {
                    hatchery = closestHatchery;
                    isProxyHatch = true;
                    Chat.tag("VS_PROXY_HATCH");
                    Chat.tag("VS_CHEESE");
                }
            }
        }
        else {
            isProxyHatch = hatchery.isAlive();
        }
    }
}
