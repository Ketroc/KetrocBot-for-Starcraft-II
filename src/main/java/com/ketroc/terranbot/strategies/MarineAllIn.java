package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.micro.Marine;
import com.ketroc.terranbot.micro.MarineBasic;
import com.ketroc.terranbot.micro.UnitMicroList;
import com.ketroc.terranbot.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MarineAllIn {
    public static boolean doAttack;
    public static List<Point2d> attackPoints;
    public static boolean isInitialBuildUp = false;

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
        setInitialBuildUp(marineList);
        if (isInitialBuildUp) {
            //patrolForOverlords(marineList);
            Marine.setTargetPos(GameCache.baseList.get(0).getResourceMidPoint());
        }
        toggleAttackMode(marineUnitList);
        if (doAttack) {
            assignTargetPos(marineUnitList);
        }
    }

    private static void setInitialBuildUp(List<MarineBasic> marineList) {
        if (isInitialBuildUp && marineList.size() >= 20) {
//                (LocationConstants.opponentRace != Race.ZERG ||
//                    marineList.size() >= 20 ||
//                    GameCache.allEnemiesList.stream().anyMatch(enemy -> UnitUtils.canAttackGround(enemy.unit()))
//                )) {
            isInitialBuildUp = false;
            Marine.setTargetPos(LocationConstants.insideMainWall);
        }
    }

    private static void patrolForOverlords(List<MarineBasic> marineList) {
        marineList.stream().forEach(marine -> {
            Chat.chatOnceOnly("Keep those ugly balloons from seeing our barracks count.");
            Point2d patrolPoint1 = LocationConstants.baseLocations.get(3);
            Point2d patrolPoint2 = LocationConstants.baseLocations.get(4);
            if (marine.targetPos.distance(patrolPoint1) < 1 && UnitUtils.getDistance(marine.unit.unit(), patrolPoint1) < 2.5) {
                marine.targetPos = patrolPoint2;
            }
            else if (marine.targetPos.distance(patrolPoint2) < 1 && UnitUtils.getDistance(marine.unit.unit(), patrolPoint2) < 2.5) {
                marine.targetPos = patrolPoint1;
            }
            else if (UnitUtils.getDistance(marine.unit.unit(), LocationConstants.insideMainWall) < 3) {
                marine.targetPos = patrolPoint1;
            }
        });
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

    private static void toggleAttackMode(List<Unit> marineList) {
        if (doAttack && marineList.size() < 20) {
            Chat.chatWithoutSpam("Run away! Run away!", 60);
            doAttack = false;
        }
        else if (!doAttack &&
                marineList.size() >= 20 &&
                marineList.stream()
                .allMatch(marine -> InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, marine.getPosition().toPoint2d()))) {
            doAttack = true;
            Chat.chatWithoutSpam("Hell. It's about time.", 60);
        }
    }
}
