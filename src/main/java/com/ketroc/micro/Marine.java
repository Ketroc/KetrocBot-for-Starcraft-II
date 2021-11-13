package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

import java.util.List;

public class Marine extends BasicUnitMicro {
    public Marine(Unit unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    public Marine(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    @Override
    public void onArrival() {
        Unit bunker = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, targetPos, 3f)
                .stream()
                .findFirst()
                .map(UnitInPool::unit)
                .orElse(null);
        if (bunker != null && bunker.getBuildProgress() == 1f) {
            ActionHelper.unitCommand(unit.unit(), Abilities.SMART, bunker, false);
            removeMe = true;
        }
        else if (UnitUtils.getDistance(unit.unit(), targetPos) > 0.5f && !isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }
//        if (!unit.unit().getActive().orElse(true)) {
//            ActionHelper.unitCommand(unit.unit(), Abilities.STOP_DANCE, false);
//        }
    }

    @Override
    protected boolean isSafe() {
        Unit bunker = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, targetPos, 3f)
                .stream()
                .findFirst()
                .map(UnitInPool::unit)
                .orElse(null);
        return bunker != null || super.isSafe();
    }

    public static void setTargetPos(Point2d newTargetPos) {
        List<Marine> marineList = UnitMicroList.getUnitSubList(Marine.class);
        //for null position do nothing, or seek for hidden structures if finishHim == true
        if (newTargetPos == null) {
            if (LocationConstants.nextEnemyBase == null) {
                marineList.forEach(marine -> {
                    if (UnitUtils.getDistance(marine.unit.unit(), marine.targetPos) < 2) {
                        marine.targetPos = UnitUtils.getRandomPathablePos();
                    }
                });
            }
            return;
        }
        marineList.forEach(marine -> marine.targetPos = newTargetPos);
    }

    public static void assignRandomTargets() {
        List<Marine> marineList = UnitMicroList.getUnitSubList(Marine.class);
        for (Marine marine : marineList) {
            if (UnitUtils.getDistance(marine.unit.unit(), marine.targetPos) < 2.5) {
                Point2d randomPathablePosition = null;
                while (randomPathablePosition == null || !Bot.OBS.isPathable(randomPathablePosition)) {
                    randomPathablePosition = Bot.OBS.getGameInfo().findRandomLocation();
                }
                marine.targetPos = randomPathablePosition;
            }
        }
    }

}
