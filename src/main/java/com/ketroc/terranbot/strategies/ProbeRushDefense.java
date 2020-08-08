package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.StructureScv;

import java.util.Collections;
import java.util.List;

public class ProbeRushDefense {
    public static int clusterMyNodeStep;
    public static int clusterEnemyNodeStep;

    public static int defenseStep;
    public static List<UnitInPool> scvList;
    public static boolean isAttackingTownHall;
    public static Units townHallType;

    public static boolean onStep() {
        try {
//            if (Bot.isDebugOn) {
//                int lines = 0;
//                Bot.DEBUG.debugTextOut("clusterMyNodeStep: " + ProbeRushDefense.clusterMyNodeStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                Bot.DEBUG.debugTextOut("clusterEnemyNodeStep: " + ProbeRushDefense.clusterEnemyNodeStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                Bot.DEBUG.debugTextOut("defenseStep: " + ProbeRushDefense.defenseStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.inner.unit().getPosition(), Color.WHITE, 10);
//                Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.outer1.unit().getPosition(), Color.WHITE, 10);
//                Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.outer2.unit().getPosition(), Color.WHITE, 10);
//            }

            //initialize townHall type
            if (townHallType == null && UnitUtils.enemyCommandStructures != null) {
                townHallType = UnitUtils.enemyCommandStructures.get(0);
            }

            //things to be done at any point during probe rush
            if (defenseStep >= 2) {

                //update snapshots
                LocationConstants.enemyMineralTriangle.updateNodes(LocationConstants.enemyMineralPos);

                //remove dead scvs from scvList
                UnitUtils.removeDeadUnits(scvList);

                //lift/land cc logic
                Unit cc = GameCache.ccList.get(0);
                int numWorkersNearCC = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, cc.getPosition().toPoint2d(), 12).size();
                if (numWorkersNearCC > 0 && cc.getType() == Units.TERRAN_COMMAND_CENTER) {
                    if (!cc.getOrders().isEmpty() && cc.getOrders().get(0).getAbility() == Abilities.TRAIN_SCV) {
                        Bot.ACTION.unitCommand(cc, Abilities.CANCEL_LAST, false);
                    } else {
                        Bot.ACTION.unitCommand(GameCache.ccList.get(0), Abilities.LIFT_COMMAND_CENTER, false);
                    }
                } else if (numWorkersNearCC == 0 && GameCache.ccList.get(0).getType() == Units.TERRAN_COMMAND_CENTER_FLYING) {
                    Bot.ACTION.unitCommand(GameCache.ccList.get(0), Abilities.LAND_COMMAND_CENTER, LocationConstants.baseLocations.get(0), false);
                }

            }
            switch (defenseStep) {
                case 0: //probe rush check
                    if (Bot.OBS.getGameLoop() < 3200 && GameCache.allEnemiesMap.getOrDefault(UnitUtils.enemyWorkerType, Collections.emptyList()).size() > 5 &&
                            GameCache.allEnemiesMap.getOrDefault(townHallType, Collections.emptyList()).isEmpty()) {
                        defenseStep++;
                        Bot.ACTION.sendChat("Okay!  I can do that too.", ActionChat.Channel.BROADCAST);
                    }
                    break;

                case 1: //cancel depot & refinery and add depot back to top of extraDepots list
                    //cancel structures
                    List<UnitInPool> producingStructures = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getBuildProgress() != 1.0f);
                    for (int i = 0; i< StructureScv.scvBuildingList.size(); i++) {
                        StructureScv scv = StructureScv.scvBuildingList.get(i);

                        //cancel structure if started
                        for (UnitInPool structure : producingStructures) {
                            if (UnitUtils.getDistance(structure.unit(), scv.structurePos) < 1) {
                                Bot.ACTION.unitCommand(structure.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                                break;
                            }
                        }

                        //put scv back to mineral line
                        Bot.ACTION.unitCommand(scv.scv.unit(), Abilities.SMART, GameCache.baseList.get(0).getRallyNode(), false);

                        //put depot back in depot list
                        if (scv.structureType == Units.TERRAN_SUPPLY_DEPOT) {
                            LocationConstants.extraDepots.add(0, scv.structurePos);
                        }
                        StructureScv.scvBuildingList.remove(i--);
                    }
                    defenseStep++;
                    break;

                case 2: //mine until the workers get close, then cluster up
                    if (!UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, GameCache.ccList.get(0).getPosition().toPoint2d(), 12).isEmpty()) {
                        //cluster on my mineral patch
                        if (scvList == null) {
                            scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, GameCache.ccList.get(0).getPosition().toPoint2d(), 20);
                        }
                        if (clusterMyNode()) {
                            defenseStep++;
                        }
                    }
                    break;

                case 3: //send scvs across the map to cluster on enemy node
                    if (clusterEnemyNode()) {
                        defenseStep++;
                    }
                    break;

                case 4: //attack probes or attack nexus or cluster
                    attackingTownHall();
                    break;

                case 5: //land cc
                    if (GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_COMMAND_CENTER_FLYING, Collections.emptyList()).isEmpty()) {
                        defenseStep = 0;
                    }
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            return defenseStep != 0;
        }
    }

    private static void attackingTownHall() {
        //if townhall dead, head home and advance to defenseStep 5
        if (GameCache.allEnemiesMap.get(townHallType).isEmpty()) {
            //send scvs home on attack-move
            Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, LocationConstants.baseLocations.get(0), 2), false)
                    .unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, false);
            defenseStep++;
            return;
        }

        Unit townHall = GameCache.allEnemiesMap.get(townHallType).get(0).unit();
        int numWorkers = Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                UnitUtils.getDistance(worker.unit(), townHall) < 10).size();

        //if scv count doubles enemy worker count then kill townhall
        if (scvList.size() > numWorkers * 2) {
            for (UnitInPool scv : scvList) {
                //if enemy worker < 1 distance from scv, attack worker
                if (!Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                        UnitUtils.getDistance(worker.unit(), scv.unit()) < 1).isEmpty()) {
                    Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                }
                else {
                    List<UnitInPool> nearbyWeakScvs = Bot.OBS.getUnits(Alliance.SELF, weakScv -> weakScv.unit().getType() == Units.TERRAN_SCV &&
                            UnitUtils.getDistance(weakScv.unit(), scv.unit()) < 1 &&
                            weakScv.unit().getHealth().get() < weakScv.unit().getHealthMax().get());

                    //if not broke and near weak scv, then repair it
                    if (GameCache.mineralBank > 0 && !nearbyWeakScvs.isEmpty()) {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, nearbyWeakScvs.get(0).unit(), false);
                    }

                    //otherwise attack townhall
                    else {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, townHall, false);
                    }
                }
            }
            isAttackingTownHall = true;
        }
        else {
            //triangle-cluster after switching from attacking townhall
            if (isAttackingTownHall) {
                if (clusterEnemyNode()) {
                    isAttackingTownHall = false;
                }
            }
            //cluster and attack micro
            else {
                Unit closestWorker = UnitUtils.getClosestEnemyOfType(UnitUtils.enemyWorkerType, scvList.get(0).unit().getPosition().toPoint2d());
                if (UnitUtils.getDistance(closestWorker, scvList.get(0).unit()) > 1) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
                } else {
                    for (UnitInPool scv : scvList) {
                        if (scv.unit().getWeaponCooldown().get() == 0f) {
                            Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.myMineralPos, false);
                        } else {
                            Bot.ACTION.unitCommand(scv.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
                        }
                    }
                }
            }
        }
    }

    public static boolean clusterMyNode() {
        switch (clusterMyNodeStep) {
            case 0:
                if (scvList.stream().anyMatch(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.myMineralTriangle.outer1.unit()) > 2)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.outer1.unit(), false);
                }
                else {
                    clusterMyNodeStep++;
                }
                break;
            case 1:
                if (scvList.stream().anyMatch(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.myMineralTriangle.outer2.unit()) > 2)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.outer2.unit(), false);
                }
                else {
                    clusterMyNodeStep++;
                }
                break;
            case 2:
                if (scvList.stream().anyMatch(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.myMineralTriangle.inner.unit()) > 2)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.inner.unit(), false);
                }
                else {
                    clusterMyNodeStep = 0;
                    return true;
                }
                break;

        }
        return false;
    }

    public static boolean clusterEnemyNode() {
        switch (clusterEnemyNodeStep) {
            case 0:
                if (scvList.stream().anyMatch(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.enemyMineralTriangle.outer1.unit()) > 2)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.outer1.unit(), false);
                }
                else {
                    clusterEnemyNodeStep++;
                }
                break;
            case 1:
                if (scvList.stream().anyMatch(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.enemyMineralTriangle.outer2.unit()) > 2)) {
                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.outer2.unit(), false);
                }
                else {
                    clusterEnemyNodeStep++;
                }
                break;
            case 2:
                if (scvList.stream().anyMatch(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.enemyMineralTriangle.inner.unit()) > 2)) {
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
