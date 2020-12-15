package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.List;

public class ProxyScout {
//
//    private static void sendScoutScvs() {
//        if (!isScoutScvsSent && Time.nowFrames() >= Time.toFrames("0:25")) {
//            List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(GameCache.baseList.get(0).getResourceMidPoint(), 10);
//            scoutScvs = availableScvs.subList(0, 2);
//            Bot.ACTION.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(1)), false)
//                    .unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(2)), true)
//                    .unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(3)), true);
//            UnitUtils.patrolInPlace(scoutScvs.get(0).unit(), getResourceMidPoint(LocationConstants.clockBasePositions.get(3)));
//            Bot.ACTION.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(1)), false)
//                    .unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(2)), true)
//                    .unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(3)), true);
//            UnitUtils.patrolInPlace(scoutScvs.get(1).unit(), getResourceMidPoint(LocationConstants.counterClockBasePositions.get(3)));
//            isScoutScvsSent = true;
//        }
//        else if (!scoutScvs.isEmpty()) {
//            //if proxy barracks found
//            if (Time.nowFrames() < Time.toFrames("3:00")) {
//                List<UnitInPool> enemyBarracks = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
//                if (!enemyBarracks.isEmpty()) {
//                    List<UnitInPool> enemyScv = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_SCV, enemyBarracks.get(0).unit().getPosition().toPoint2d(), 5);
//                    Unit scvAttackTarget = (!enemyScv.isEmpty()) ? enemyScv.get(0).unit() : enemyBarracks.get(0).unit();
//                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, scvAttackTarget, false);
//                    UnitUtils.patrolInPlace(UnitUtils.toUnitList(scoutScvs), scvAttackTarget.getPosition().toPoint2d());
//                }
//                //if no enemy proxy or after enemy proxy cancelled, then go to behindBunkerPos
//                else if (scoutScvs.stream().anyMatch(
//                        scv -> UnitUtils.getOrder(scv.unit()) == Abilities.ATTACK)) {
//                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.MOVE, behindBunkerPos, false);
//                }
//            }
//            //make scoutscv a repairscv when it arrives at bunker
//            for (UnitInPool scoutScv : scoutScvs) {
//                if (UnitUtils.getDistance(scoutScv.unit(), behindBunkerPos) < 2) {
//                    addRepairScv(scoutScv);
//                    //send scv to build 2nd proxy bunker and factory
//                    if (!didFirstScoutScvIdle) {
//                        didFirstScoutScvIdle = true;
//                        if (LocationConstants.proxyBunkerPos2 != null) {
//                            Bot.ACTION.unitCommand(scoutScv.unit(), Abilities.MOVE, LocationConstants.proxyBunkerPos2, false);
//                            UnitUtils.patrolInPlace(scoutScv.unit(), LocationConstants.proxyBunkerPos2);
//                            KetrocBot.purchaseQueue.stream()
//                                    .filter(purchase -> purchase instanceof PurchaseStructure &&
//                                            ((PurchaseStructure) purchase).getStructureType() == Units.TERRAN_FACTORY)
//                                    .findFirst()
//                                    .ifPresent(purchase -> ((PurchaseStructure) purchase).setScv(scoutScv.unit()));
//                        }
//                    }
//                }
//            }
//            scoutScvs.removeIf(scv -> UnitUtils.getDistance(scv.unit(), behindBunkerPos) < 2);
//
//            //send to bunker if idle
//            scoutScvs.stream()
//                    .map(UnitInPool::unit)
//                    .filter(scv -> UnitUtils.getOrder(scv) == Abilities.PATROL)
//                    .forEach(scv -> Bot.ACTION.unitCommand(scv, Abilities.MOVE, behindBunkerPos, false));
//        }
//
    }
