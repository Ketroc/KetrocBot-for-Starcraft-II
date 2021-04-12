package com.ketroc.terranbot.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.Switches;
import com.ketroc.terranbot.models.TriangleOfNodes;
import com.ketroc.terranbot.utils.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.StructureScv;

import java.util.Collections;
import java.util.List;

public class WorkerRushDefense {
    public static int clusterTriangleStep;

    public static int defenseStep;
    public static List<UnitInPool> scvList;
    public static boolean isAttackingTownHall;
    public static Units townHallType;

    public static boolean onStep() {
        try {
//            if (Bot.isDebugOn) {
//                int lines = 0;
//                Bot.DEBUG.debugTextOut("townhall type: " + townHallType, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                Bot.DEBUG.debugTextOut("clusterTriangleStep: " + ProbeRushDefense.clusterTriangleStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                Bot.DEBUG.debugTextOut("defenseStep: " + ProbeRushDefense.defenseStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.getInner().unit().getPosition(), Color.WHITE, 10);
//                Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.getOuter().unit().getPosition(), Color.WHITE, 10);
//                Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.getMiddle().unit().getPosition(), Color.WHITE, 10);
//            }

            //initialize townHall type
            if (townHallType == null && UnitUtils.enemyCommandStructures != null) {
                townHallType = UnitUtils.enemyCommandStructures.iterator().next();
            }

            //things to be done at any point during probe rush
            if (defenseStep >= 2) {

                //update snapshots
                LocationConstants.enemyMineralTriangle.updateNodes();

                //remove dead scvs from scvList
                UnitUtils.removeDeadUnits(scvList);

                //lift/land cc logic
                Unit cc = GameCache.ccList.get(0);
                int numWorkersNearCC = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, cc.getPosition().toPoint2d(), 12).size();
                if (numWorkersNearCC > 0 && cc.getType() == Units.TERRAN_COMMAND_CENTER) {
                    if (UnitUtils.getOrder(cc) == Abilities.TRAIN_SCV) {
                        ActionHelper.unitCommand(cc, Abilities.CANCEL_LAST, false);
                    } else {
                        ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.LIFT_COMMAND_CENTER, false);
                    }
                } else if (numWorkersNearCC == 0 && GameCache.ccList.get(0).getType() == Units.TERRAN_COMMAND_CENTER_FLYING) {
                    ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.LAND_COMMAND_CENTER, LocationConstants.baseLocations.get(0), false);
                }

            }
            switch (defenseStep) {
                case 0: //probe rush check
                    if (UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, LocationConstants.pointOnMyRamp, 7).size() >= 5 &&
                            !UnitUtils.isWallComplete()) {
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
                                ActionHelper.unitCommand(structure.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                                break;
                            }
                        }

                        //put scv back to mineral line
                        ActionHelper.unitCommand(scv.getScv().unit(), Abilities.SMART, GameCache.baseList.get(0).getRallyNode(), false);

                        //put depot back in depot list
                        if (scv.structureType == Units.TERRAN_SUPPLY_DEPOT) {
                            LocationConstants.extraDepots.add(0, scv.structurePos);
                        }
                        StructureScv.remove(scv);
                        i--;
                    }
                    defenseStep++;
                    break;

                case 2: //mine until the workers get close, then cluster up
                    if (!UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, GameCache.ccList.get(0).getPosition().toPoint2d(), 12).isEmpty()) {
                        //cluster on my mineral patch
                        if (scvList == null) {
                            scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, GameCache.ccList.get(0).getPosition().toPoint2d(), 20);
                        }
                        if (clusterTriangleNode(LocationConstants.myMineralTriangle)) {
                            defenseStep++;
                        }
                    }
                    break;

                case 3: //send scvs across the map to cluster on enemy node
                    if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
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
        List<UnitInPool> townHallList = UnitUtils.getEnemyUnitsOfTypes(UnitUtils.enemyCommandStructures);
        if (townHallList.isEmpty()) {
            //send scvs home on attack-move
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, LocationConstants.baseLocations.get(0), 2), false);
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, false);
            defenseStep++;
            return;
        }

        Unit townHall = townHallList.get(0).unit();
        int numEnemyWorkers = Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                UnitUtils.getDistance(worker.unit(), townHall) < 10).size();

        //if scv count doubles enemy worker count then kill townhall
        if (scvList.size() > numEnemyWorkers * 2) {
            for (UnitInPool scv : scvList) {
                //if enemy worker < 1 distance from scv, attack worker
                if (!Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                        UnitUtils.getDistance(worker.unit(), scv.unit()) < 1).isEmpty()) {
                    ActionHelper.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                }
                else {
                    List<UnitInPool> nearbyWeakScvs = Bot.OBS.getUnits(Alliance.SELF, weakScv -> weakScv.unit().getType() == Units.TERRAN_SCV &&
                            UnitUtils.getDistance(weakScv.unit(), scv.unit()) < 1 &&
                            weakScv.unit().getHealth().get() < weakScv.unit().getHealthMax().get());

                    //if not broke and near weak scv, then repair it
                    if (GameCache.mineralBank > 0 && !nearbyWeakScvs.isEmpty()) {
                        ActionHelper.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, nearbyWeakScvs.get(0).unit(), false);
                    }

                    //otherwise attack townhall
                    else {
                        ActionHelper.unitCommand(scv.unit(), Abilities.ATTACK, townHall, false);
                    }
                }
            }
            isAttackingTownHall = true;
        }
        else {
            //triangle-cluster after switching from attacking townhall
            if (isAttackingTownHall) {
                if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                    isAttackingTownHall = false;
                }
            }
            //cluster and attack micro
            else {
                Unit closestEnemyWorker = UnitUtils.getClosestEnemyOfType(UnitUtils.enemyWorkerType, scvList.get(0).unit().getPosition().toPoint2d());
                if (UnitUtils.getDistance(closestEnemyWorker, scvList.get(0).unit()) > 1) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
                } else {
                    for (UnitInPool scv : scvList) {
                        if (UnitUtils.isWeaponAvailable(scv.unit())) {
                            ActionHelper.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.myMineralPos, false);
                        } else {
                            ActionHelper.unitCommand(scv.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
                        }
                    }
                }
            }
        }
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle) {
        switch (clusterTriangleStep) {
            case 0:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), triangle.getInner().unit()) > 2.7).count() > 2) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getInner().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 1:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), triangle.getOuter().unit()) > 2.7).count() > 2) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getOuter().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 2:
                if (scvList.stream().allMatch(u -> UnitUtils.getDistance(u.unit(), triangle.getMiddle().unit()) > 1.4)) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getMiddle().unit(), false);
                }
                else {
                    clusterTriangleStep = 0;
                    return true;
                }
                break;

        }
        return false;
    }
}
