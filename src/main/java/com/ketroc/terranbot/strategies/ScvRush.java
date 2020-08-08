package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.Collections;
import java.util.List;

public class ScvRush {
    public static int clusterMyNodeStep;
    public static int clusterEnemyNodeStep;

    public static int scvRushStep;
    public static List<UnitInPool> scvList;
    public static boolean isAttackingCommand;

    public static boolean onStep() {
        if (Bot.isDebugOn) {
            int lines = 0;
            Bot.DEBUG.debugTextOut("clusterEnemyNodeStep: " + ScvRush.clusterEnemyNodeStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("scvRushStep: " + ScvRush.scvRushStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.inner.unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.outer1.unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.outer2.unit().getPosition(), Color.WHITE, 10);
        }
        try {
            //update snapshots
            LocationConstants.enemyMineralTriangle.updateNodes(LocationConstants.enemyMineralPos);

            switch (scvRushStep) {
                case 0: //send scvs across the map to cluster on enemy node
                    if (scvList == null) {
                        scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, GameCache.ccList.get(0).getPosition().toPoint2d(), 20);
                    }
                    if (clusterEnemyNode()) {
                        scvRushStep++;
                    }
                    break;

                case 1: //attack probes or attack nexus or cluster
                    UnitUtils.removeDeadUnits(scvList);
                    if (scvList.isEmpty()) {
                        scvRushStep++;
                    }
                    else {
                        attackingBase();
                    }
                    break;
                case 2: //all scvs dead or enemy command structure dead
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            return scvRushStep == 2;
        }

    }

    private static void attackingBase() {
        UnitType commandStructureType = UnitUtils.enemyCommandStructures.stream().filter(units -> !GameCache.allEnemiesMap.getOrDefault(units, Collections.emptyList()).isEmpty()).findFirst().orElse(Units.INVALID);

        //if command structure is dead, head home and advance to scvRushStep 2 to end
        if (GameCache.allEnemiesMap.getOrDefault(commandStructureType, Collections.emptyList()).isEmpty()) {
            //send scvs home on attack-move
            Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, LocationConstants.baseLocations.get(0), 2), false)
                    .unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, false);
            scvRushStep++;
            return;
        }

        Unit enemyCommand = GameCache.allEnemiesMap.get(commandStructureType).get(0).unit();

        int numEnemyWorkers = Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                worker.unit().getPosition().toPoint2d().distance(enemyCommand.getPosition().toPoint2d()) < 10).size();

        //if scv count doubles probe count then kill his command structure
        if (scvList.size() >= numEnemyWorkers * 2) {
            for (UnitInPool scv : scvList) {
                //if worker < 1 distance from scv, attack worker
                if (!Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                        worker.unit().getPosition().toPoint2d().distance(scv.unit().getPosition().toPoint2d()) < 1).isEmpty()) {
                    Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                }
                else {
                    List<UnitInPool> nearbyWeakScvs = Bot.OBS.getUnits(Alliance.SELF, weakScv -> weakScv.unit().getType() == Units.TERRAN_SCV &&
                            weakScv.unit().getPosition().toPoint2d().distance(scv.unit().getPosition().toPoint2d()) < 1 &&
                            weakScv.unit().getHealth().get() < weakScv.unit().getHealthMax().get());

                    //if not broke and near weak scv, then repair it
                    if (GameCache.mineralBank > 0 && !nearbyWeakScvs.isEmpty()) {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, nearbyWeakScvs.get(0).unit(), false);
                    }

                    //otherwise attack nexus
                    else {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, enemyCommand, false);
                    }
                }
            }
            isAttackingCommand = true;
        }
        else {
            //triangle-cluster after switching from attacking his command structure
            if (isAttackingCommand) {
                if (clusterEnemyNode()) {
                    isAttackingCommand = false;
                }
            }
            //cluster and attack micro
            else {
                //cluster if not tight together
                if (scvList.stream().allMatch(u -> UnitUtils.getDistance(u.unit(), LocationConstants.enemyMineralTriangle.inner.unit()) > 1.5)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
                }
                else {
                    //cluster if no enemy worker in range
                    Unit closestWorker = UnitUtils.getClosestEnemyOfType(UnitUtils.enemyWorkerType, scvList.get(0).unit().getPosition().toPoint2d());
                    if (closestWorker.getPosition().toPoint2d().distance(scvList.get(0).unit().getPosition().toPoint2d()) > 1) { // &&
//                            scvList.stream().anyMatch(u -> u.unit().getWeaponCooldown().get() > 0f)) {
                        Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
                    }
                    //otherwise attack
                    else {
                        for (UnitInPool scv : scvList) {
                            if (scv.unit().getWeaponCooldown().get() == 0f) {
                                Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.myMineralPos, false);
                            }
                            else {
                                Bot.ACTION.unitCommand(scv.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
                            }
                        }
//                        Bot.ACTION.unitCommand(scvList.stream().map(UnitInPool::unit).filter(u -> u.getWeaponCooldown().get() == 0f).collect(Collectors.toList()), Abilities.ATTACK, closestWorker, false);
//                        Bot.ACTION.unitCommand(scvList.stream().map(UnitInPool::unit).filter(u -> u.getWeaponCooldown().get() != 0f).collect(Collectors.toList()), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);

//                        Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.ATTACK, closestWorker, false);
                    }
                }
            }
        }
//        //if all scvs off cooldown and all clustered, attack
//        else if (scvList.stream().allMatch(scv -> scv.unit().getPosition().toPoint2d().distance(enemyMineralPos) < 1.5 &&
//                (scv.unit().getWeaponCooldown().get() == 0f || scv.unit().getWeaponCooldown().get() > 0.8f))) {
//            Unit closestProbe = UnitUtils.getClosestEnemyOfType(Units.PROTOSS_PROBE, scvList.get(0).unit().getPosition().toPoint2d());
//            //if probe in range to attack
//            if (closestProbe.getPosition().toPoint2d().distance(scvList.get(0).unit().getPosition().toPoint2d()) < 1) {
//                Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.ATTACK, closestProbe, false);
//                return;
//            }
//            //keep clustering
//            else {
//                Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, enemyMineralUnit, false);
//            }
//        }
//        //else cluster
//        else {
//            Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, enemyMineralUnit, false);
//        }
    }

    public static boolean clusterEnemyNode() {
        switch (clusterEnemyNodeStep) {
            case 0:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), LocationConstants.enemyMineralTriangle.outer1.unit()) > 2.5).count() > 2) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.outer1.unit(), false);
                }
                else {
                    clusterEnemyNodeStep++;
                }
                break;
            case 1:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), LocationConstants.enemyMineralTriangle.outer2.unit()) > 2.5).count() > 2) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.outer2.unit(), false);
                }
                else {
                    clusterEnemyNodeStep++;
                }
                break;
            case 2:
                if (scvList.stream().allMatch(u -> UnitUtils.getDistance(u.unit(), LocationConstants.enemyMineralTriangle.inner.unit()) > 1.5)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
                }
                else {
                    clusterEnemyNodeStep = 0;
                    return true;
                }
                break;

        }
        return false;
    }

}
