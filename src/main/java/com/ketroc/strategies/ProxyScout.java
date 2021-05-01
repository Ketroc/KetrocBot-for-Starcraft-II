package com.ketroc.strategies;

public class ProxyScout {
//
//    private static void sendScoutScvs() {
//        if (!isScoutScvsSent && Time.nowFrames() >= Time.toFrames("0:25")) {
//            List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(GameCache.baseList.get(0).getResourceMidPoint(), 10);
//            scoutScvs = availableScvs.subList(0, 2);
//            ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(1)), false)
//            ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(2)), true)
//            ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(3)), true);
//            UnitUtils.patrolInPlace(scoutScvs.get(0).unit(), getResourceMidPoint(LocationConstants.clockBasePositions.get(3)));
//            ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(1)), false)
//            ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(2)), true)
//            ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(3)), true);
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
//                    ActionHelper.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, scvAttackTarget, false);
//                    UnitUtils.patrolInPlace(UnitUtils.toUnitList(scoutScvs), scvAttackTarget.getPosition().toPoint2d());
//                }
//                //if no enemy proxy or after enemy proxy cancelled, then go to behindBunkerPos
//                else if (scoutScvs.stream().anyMatch(
//                        scv -> UnitUtils.getOrder(scv.unit()) == Abilities.ATTACK)) {
//                    ActionHelper.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.MOVE, behindBunkerPos, false);
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
//                            ActionHelper.unitCommand(scoutScv.unit(), Abilities.MOVE, LocationConstants.proxyBunkerPos2, false);
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
//                    .forEach(scv -> ActionHelper.unitCommand(scv, Abilities.MOVE, behindBunkerPos, false));
//        }
//
    }
