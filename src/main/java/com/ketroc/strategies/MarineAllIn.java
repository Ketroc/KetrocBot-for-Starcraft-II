package com.ketroc.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.micro.MarineBasic;
import com.ketroc.micro.MarineOffense;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MarineAllIn {
    public static final int MIN_MARINES_TO_ATTACK = 18;

    public static boolean doAttack;
    public static List<Point2d> attackPoints;
    public static boolean isInitialBuildUp = true;

    public static void onGameStart() {
        //calculate typical zerg 3rd base (base nearest enemy natural but far from my natural)
//        List<Point2d> basesClosestToEnemyNatural = GameCache.baseList.subList(GameCache.baseList.size()/2, GameCache.baseList.size() - 2).stream()
//                .map(Base::getResourceMidPoint)
//                .sorted(Comparator.comparing(basePos -> basePos.distance(GameCache.baseList.get(GameCache.baseList.size() - 2).getResourceMidPoint())))
//                .collect(Collectors.toList());
//        Point2d thirdBase1 = basesClosestToEnemyNatural.get(0);
//        Point2d thirdBase2 = basesClosestToEnemyNatural.get(1);
//        Point2d thirdBaseFinal = thirdBase1;
//        if (thirdBase1.distance(LocationConstants.BUNKER_NATURAL) < thirdBase2.distance(LocationConstants.BUNKER_NATURAL)) {
//            thirdBaseFinal = thirdBase2;
//        }

        //attack enemy natural then enemy main
        attackPoints = new ArrayList<>(List.of(
                GameCache.baseList.get(GameCache.baseList.size()-2).getResourceMidPoint(),
                LocationConstants.pointOnEnemyRamp,
                GameCache.baseList.get(GameCache.baseList.size()-1).getResourceMidPoint()
        ));
    }

    public static void onStep() {
        if (!Strategy.MARINE_ALLIN) {
            return;
        }

//        List<MarineOffense> marineList = UnitMicroList.getUnitSubList(MarineOffense.class);
//        List<Unit> marineUnitList = marineList.stream().map(marineOffense -> marineOffense.unit.unit()).collect(Collectors.toList());
        setInitialBuildUp();
//        if (isInitialBuildUp) {
//            //patrolForOverlords(marineList);
//            Marine.setTargetPos(GameCache.baseList.get(0).getResourceMidPoint());
//        }
//        setDoOffense(marineUnitList);
//        if (doAttack) {
//            assignTargetPos(marineUnitList);
//        }
    }

//    public static Point2d getRallyPoint() {
//        if (isInitialBuildUp) {
//            return GameCache.baseList.get(0).getResourceMidPoint();
//        }
//    }

    private static void setInitialBuildUp() {
        if (isInitialBuildUp && UnitMicroList.getUnitSubList(MarineOffense.class).size() >= MIN_MARINES_TO_ATTACK) {
            isInitialBuildUp = false;
            //Marine.setTargetPos(LocationConstants.insideMainWall);
        }
    }

//    private static void patrolForOverlords(List<MarineBasic> marineList) {
//        marineList.stream().forEach(marine -> {
//            Chat.chatNeverRepeat("Keep those ugly balloons from seeing our barracks count.");
//            Point2d patrolPoint1 = LocationConstants.baseLocations.get(3);
//            Point2d patrolPoint2 = LocationConstants.baseLocations.get(4);
//            if (marine.targetPos.distance(patrolPoint1) < 1 && UnitUtils.getDistance(marine.unit.unit(), patrolPoint1) < 2.5) {
//                marine.targetPos = patrolPoint2;
//            }
//            else if (marine.targetPos.distance(patrolPoint2) < 1 && UnitUtils.getDistance(marine.unit.unit(), patrolPoint2) < 2.5) {
//                marine.targetPos = patrolPoint1;
//            }
//            else if (UnitUtils.getDistance(marine.unit.unit(), LocationConstants.insideMainWall) < 3) {
//                marine.targetPos = patrolPoint1;
//            }
//        });
//    }

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
            Point2d attackPos = attackPoints.get(0);
            int distance = Bot.OBS.isPathable(attackPos, false) ? 4 : 8;
            if (avgMarinePos.distance(attackPos) < 3) {
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

    public static boolean getDoOffense() {
        List<MarineOffense> marineList = UnitMicroList.getUnitSubList(MarineOffense.class);
        if (ArmyManager.doOffense && marineList.size() < MIN_MARINES_TO_ATTACK) {
            Chat.chatWithoutSpam("Run away! Run away!", 30);
            return false;
        }
        else if (!ArmyManager.doOffense &&
                marineList.size() >= 20 &&
                marineList.stream().allMatch(marine -> InfluenceMaps.getValue(InfluenceMaps.pointInMainBase,
                        marine.unit.unit().getPosition().toPoint2d()))) {
            Chat.chatWithoutSpamInvisToHuman("Hell. It's about time.", 30);
            return true;
        }
        else {
            return ArmyManager.doOffense;
        }
    }
}
