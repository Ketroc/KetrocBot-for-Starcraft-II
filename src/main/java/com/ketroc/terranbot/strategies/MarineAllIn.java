package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.micro.MarineBasic;
import com.ketroc.terranbot.micro.UnitMicroList;
import com.ketroc.terranbot.utils.InfluenceMaps;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MarineAllIn {
    public static boolean attackMode;
    public static List<Point2d> attackPoints;

    public static void onGameStart() {
        //attack enemy natural then enemy main
        attackPoints = new ArrayList<>(List.of(
                GameCache.baseList.get(GameCache.baseList.size()-2).getResourceMidPoint(),
                GameCache.baseList.get(GameCache.baseList.size()-1).getResourceMidPoint()
        ));
    }

    public static void onStep() {
        if (!Strategy.MARINE_ALLIN) {
            return;
        }

        List<MarineBasic> marineList = UnitMicroList.getUnitSubList(MarineBasic.class);
        List<Unit> marineUnitList = marineList.stream().map(marineBasic -> marineBasic.unit.unit()).collect(Collectors.toList());
        toggleAttackMode(marineUnitList);
        if (attackMode) {
            assignTargetPos(marineUnitList);
        }
    }

    private static void assignTargetPos(List<Unit> marineUnitList) {
        Point2d avgMarinePos = Position.midPointUnitsMedian(marineUnitList);
        Point2d targetPos = getTargetPos(avgMarinePos);
        if (targetPos == null) {
            MarineBasic.assignRandomTargets();
        }
        else {
            MarineBasic.setTargetPos(targetPos);
        }
    }

    private static Point2d getTargetPos(Point2d avgMarinePos) {
        if (!attackPoints.isEmpty()) {
            if (avgMarinePos.distance(attackPoints.get(0)) < 3) {
                attackPoints.remove(0);
            }
        }
        if (attackPoints.isEmpty()) {
            List<UnitInPool> enemyStructures = Bot.OBS.getUnits(Alliance.ENEMY, u -> UnitUtils.isStructure((Units) u.unit().getType()));
            if (!enemyStructures.isEmpty()) {
                return enemyStructures.stream()
                        .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), avgMarinePos)))
                        .get().unit().getPosition().toPoint2d();
            }
            return null;
        }
        else {
            return attackPoints.get(0);
        }
    }

    private static void toggleAttackMode(List<Unit> marineList) {
        if (marineList.size() < 20) {
            attackMode = false;
        }
        else if (marineList.stream()
                .allMatch(marine -> InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, marine.getPosition().toPoint2d()))) {
            attackMode = true;
        }
    }
}
