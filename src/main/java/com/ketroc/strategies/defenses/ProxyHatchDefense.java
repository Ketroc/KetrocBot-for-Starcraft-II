package com.ketroc.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.ScvAttackTarget;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;

public class ProxyHatchDefense {
    public static boolean isProxyHatch;
    public static UnitInPool hatchery;

    public static void onStep() {
        setIsProxyHatch();
        if (isProxyHatch) {
            if (!ScvAttackTarget.contains(hatchery.getTag())) {
                List<UnitInPool> allScvs = WorkerManager.getAllScvs(LocationConstants.baseLocations.get(0), 30);
                int numScvs = hatchery.unit().getBuildProgress() > 0.45f ? 12 : 7; //TODO: math this to more accuracy?
                numScvs = Math.min(allScvs.size(), numScvs);
                allScvs.stream()
                        .sorted(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), hatchery.unit().getPosition().toPoint2d())))
                        .limit(numScvs)
                        .forEach(scv -> UnitMicroList.add(new ScvAttackTarget(scv, hatchery)));
            }
        }
    }

    private static void setIsProxyHatch() {
        if (!isProxyHatch) {
            if (Time.nowFrames() < Time.toFrames("3:00")) {
                UnitInPool closestHatchery = UnitUtils.getClosestEnemyUnitOfType(Units.ZERG_HATCHERY, LocationConstants.baseLocations.get(1));
                if (closestHatchery != null &&
//                        closestHatchery.unit().getBuildProgress() < 0.8 &&
                        (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, closestHatchery.unit().getPosition().toPoint2d()) ||
                                InfluenceMaps.getValue(InfluenceMaps.pointInNat, closestHatchery.unit().getPosition().toPoint2d()))) {
                    hatchery = closestHatchery;
                    isProxyHatch = true;
                }
            }
        }
        else {
            isProxyHatch = hatchery.isAlive();
        }
    }
}
