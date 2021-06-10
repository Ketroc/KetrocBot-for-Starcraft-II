package com.ketroc.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.BuildManager;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.purchases.*;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BunkerContain {
    public static final float BUNKER_RANGE = 7.5f; //range marines can fire in bunker = 5range, +1 for bunker, +1.5 for bunker radius
    private static boolean isFirstScvSent;
    private static boolean isScoutScvsSent;
    private static int marinesNeeded;
    public static int proxyBunkerLevel; //0 - no contain, 1 - bunker + 2scvs, 2 - bunker, 3scv, 2tanks, turret TODO: currently only works as 2 vs terran or 1 vs protoss
    public static boolean scoutProxy;
    public static Point2d barracksPos;
    public static Point2d bunkerPos;
    public static List<UnitInPool> repairScvList = new ArrayList<>();
    public static List<UnitInPool> scoutScvs = new ArrayList<>();
    public static UnitInPool tank1;
    public static UnitInPool tank2;
    public static Point2d tank1Pos;
    public static Point2d tank2Pos;
    public static UnitInPool barracks;
    public static StructureFloater barracksSpotter;
    public static Point2d defaultSpotterPos;
    public static UnitInPool factory;
    public static UnitInPool bunker;
    public static AddonSwap factorySwap;
    public static Point2d behindBunkerPos;
    public static final Set<Units> defenders = Set.of(Units.PROTOSS_PROBE, Units.PROTOSS_ZEALOT);
    public static final Set<Units> repairTargets = Set.of(
            Units.TERRAN_SCV, Units.TERRAN_BUNKER, Units.TERRAN_SIEGE_TANK_SIEGED, Units.TERRAN_SIEGE_TANK, Units.TERRAN_MISSILE_TURRET);
    private static boolean isBarracksSentHome;
    private static Point2d enemyPos;
    private static boolean didFirstScoutScvIdle; //first scv to arrive behindBunkerPos

    public static void onGameStart() {
        //exit
        if (proxyBunkerLevel == 0) {
            return;
        }
        barracksPos = LocationConstants.proxyBarracksPos;
        bunkerPos = LocationConstants.proxyBunkerPos;
        enemyPos = getEnemyPos();
        defaultSpotterPos = Position.towards(bunkerPos, enemyPos, 6);
        behindBunkerPos = Position.towards(bunkerPos, enemyPos,-2);
        tank1Pos = Position.rotate(
                Position.towards(behindBunkerPos, bunkerPos, -0.5f),
                bunkerPos, 85);
        tank2Pos = Position.rotate(
                Position.towards(behindBunkerPos, bunkerPos, -0.5f),
                bunkerPos, -85);
        marinesNeeded = 4;
        if (LocationConstants.opponentRace == Race.TERRAN) {
            marinesNeeded = 3;
        }
        scoutProxy = (LocationConstants.opponentRace == Race.TERRAN);
    }

    public static void onStep() {
        //exit
        if (proxyBunkerLevel == 0) {
            return;
        }

        //check if contain is broken
        abandonProxy();
        if (proxyBunkerLevel == 0) {
            return;
        }

        // ========= SCVS ===========
        if (Time.nowFrames() == Time.toFrames(6)) {
            Unit scv = WorkerManager.getClosestAvailableScv(LocationConstants.extraDepots.get(0)).unit();
            ActionHelper.giveScvCommand(scv, Abilities.MOVE, LocationConstants.extraDepots.get(0), false);
            UnitUtils.patrolInPlace(scv, LocationConstants.extraDepots.get(0));
            ((PurchaseStructure)KetrocBot.purchaseQueue.get(0)).setScv(scv);
        }
        if (Time.nowFrames() == Time.toFrames("1:06")) {
            addNewRepairScv();
        }
        sendFirstScv();
        if (scoutProxy) {
            sendScoutScvs();
        }
        replaceLowHpScvs();
        scvRepairMicro();

        // ========= FACTORY SWAP =========
        if (BunkerContain.proxyBunkerLevel == 2) {
            if (factorySwap == null) {
                if (readyToBuildFactory()) {
                    UnitInPool availableRepairScv = repairScvList.stream()
                            .filter(scv -> UnitUtils.getOrder(scv.unit()) == null ||
                                    UnitUtils.getOrder(scv.unit()) != Abilities.BUILD_BUNKER)
                            .findFirst()
                            .orElse(repairScvList.get(0));
                    factorySwap = new AddonSwap(barracks, Abilities.BUILD_TECHLAB_BARRACKS, Units.TERRAN_FACTORY, availableRepairScv);
                    KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_MARINE, barracks));
                }
            }
            else if (factorySwap.removeMe) {
                factorySwap = null;
                KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, factory));
                KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, factory));
            }
            else {
                factorySwap.onStep();
            }
        }


        // ========= FACTORY ===========
        if (BunkerContain.proxyBunkerLevel == 2) {
            if (factory == null) {
                List<UnitInPool> allFactories = Bot.OBS.getUnits(Alliance.SELF, factory -> factory.unit().getType() == Units.TERRAN_FACTORY);
                if (!allFactories.isEmpty()) {
                    onFactoryStarted(allFactories.get(0));
                }
            }
            if (factory != null && factorySwap == null) {
                if (!factory.unit().getActive().orElse(true)) {
                    if ((tank1 == null || tank2 == null)) {
                        //buildTanks();
                    } else if (!factory.unit().getFlying().orElse(true)) {
                        BuildManager.liftFactory();
                    }
                }
            }

            // ========= TANKS ===========
            //siegeTankTargetting();
        }


        // ========= BARRACKS ===========
        if (barracks == null) {
            UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BARRACKS, barracksPos, 1).stream()
                    .findFirst()
            .ifPresent(unitInPool -> barracks = unitInPool);

        }
        if (barracks != null && factorySwap == null) {
            if (!barracks.unit().getActive().orElse(true)) {
                if (getMarineCount() < marinesNeeded) {
                    buildMarines();
                    return;
                }
                else if (proxyBunkerLevel == 2 && barracksSpotter == null) {
                    barracksSpotter = new StructureFloater(barracks, behindBunkerPos, false);
                }
                else if (!isBarracksSentHome) {
                    sendBarracksHome();
                }
            }
        }

        // =========== BARRACKS SPOTTER ===========
        if (barracksSpotter != null) {
            //move forward for spotting once barracks arrives
            if (barracksSpotter.targetPos.distance(behindBunkerPos) < 1 &&
                    UnitUtils.getDistance(barracksSpotter.unit.unit(), behindBunkerPos) < 3) {
                barracksSpotter.targetPos = defaultSpotterPos;
            }
            //spot enemy siege tanks
            else {  String a = "";
                //get closest tank within 8 - 16 range of bunker
                Unit closestEnemyTank = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, UnitUtils.SIEGE_TANK_TYPE, bunkerPos);
                //set targetPos to tank towards bunkerPos
                if (closestEnemyTank != null && UnitUtils.getDistance(closestEnemyTank, bunkerPos) < 15) {
                    barracksSpotter.targetPos = Position.towards(closestEnemyTank.getPosition().toPoint2d(), bunkerPos,
                            UnitUtils.rangeToSee(barracksSpotter.unit.unit(), closestEnemyTank) - 0.5f);
                }
                else {
                    barracksSpotter.targetPos = defaultSpotterPos;
                }
            }
            barracksSpotter.onStep();
        }

        // ========= BUNKER ===========
        if (bunker == null) {
            List<UnitInPool> allBunkers = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, bunkerPos, 1);
            if (!allBunkers.isEmpty()) {
                onBunkerStarted(allBunkers.get(0));
            }
        }
        else if (bunker.unit().getCargoSpaceTaken().orElse(0) > 0) {
            bunkerTargetting();
        }
    }

    private static boolean readyToBuildFactory() {
        return Bot.OBS.getVespene() > 55 &&
                UnitUtils.getNumFriendlyUnits(UnitUtils.FACTORY_TYPE, true) < 1 &&
                barracks != null;
    }

    public static void onTankCreated(UnitInPool tank) {
        if (tank1 == null || !tank1.isAlive()) {
            tank1 = tank;
            UnitMicroList.add(new TankToPosition(tank1, tank1Pos, MicroPriority.GET_TO_DESTINATION));
        }
        else if (tank2 == null || !tank2.isAlive()) {
            tank2 = tank;
            UnitMicroList.add(new TankToPosition(tank2, tank2Pos, MicroPriority.GET_TO_DESTINATION));
        }
    }

    public static void onMarineCreated(UnitInPool marine) {
        if (proxyBunkerLevel > 0 &&
                (LocationConstants.opponentRace == Race.TERRAN && LocationConstants.proxyBunkerPos2 != null) &&
                getMarineCount() == 1) {
            UnitMicroList.add(new MarineProxyBunker(marine, LocationConstants.proxyBunkerPos2));
            return;
        }
        UnitMicroList.add(new MarineProxyBunker(marine, LocationConstants.proxyBunkerPos));
    }

    public static void addRepairScv(UnitInPool scv) {
        repairScvList.add(scv);
        Base.releaseScv(scv.unit());
        Ignored.add(new IgnoredUnit(scv.getTag()));
    }

    public static void removeRepairScv(UnitInPool scv) {
        repairScvList.remove(scv);
        if (UnitUtils.getOrder(scv.unit()) != null) {
            ActionHelper.unitCommand(scv.unit(), Abilities.STOP, false);
        }
        Ignored.remove(scv.getTag());
    }

    private static Point2d getEnemyPos() {
        Point2d enemyNat = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-2);
        Point2d enemyRamp = LocationConstants.pointOnEnemyRamp;
        float distance = (float) enemyRamp.distance(enemyNat) /
                ((LocationConstants.opponentRace == Race.TERRAN) ? 7 : 3);
        return Position.towards(enemyRamp, enemyNat, distance);
    }

    private static void bunkerTargetting() {
        //do nothing if bunker is empty
        if (bunker.unit().getCargoSpaceTaken().orElse(0) == 0) {
            return;
        }

        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.getDistance(enemy.unit(), bunker.unit()) < BUNKER_RANGE);
        UnitInPool target = selectTarget(enemiesInRange);
        if (target != null) {
            ActionHelper.unitCommand(bunker.unit(), Abilities.ATTACK, target.unit(), false);
        }
    }

    private static void siegeTankTargetting() {
        for (Unit tank : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK_SIEGED)) {
            //only find a target if tank is about to fire
            if (tank.getWeaponCooldown().orElse(1f) > 0.05f) {
                continue;
            }

            //get all enemy targets in range
            List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> {
                float distance = UnitUtils.getDistance(enemy.unit(), tank);
                return (distance > 2 && distance < 13 && enemy.unit().getDisplayType() == DisplayType.VISIBLE);
            });
            UnitInPool target = selectTarget(enemiesInRange);
            if (target != null) {
                ActionHelper.unitCommand(tank, Abilities.ATTACK, target.unit(), false);
            }
        }
    }

    private static UnitInPool selectTarget(List<UnitInPool> enemiesInRange) { //TODO: consider damage (eg better for tank to hit 50hp unit than 5hp unit)
        UnitInPool bestTarget = null; //best target will be lowest hp unit without barrier
        float bestTargetHP = Float.MAX_VALUE;
        for (UnitInPool enemy : enemiesInRange) {
            float enemyHP = enemy.unit().getHealth().orElse(0f) + enemy.unit().getShield().orElse(0f);
            if (enemyHP < bestTargetHP && !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                bestTargetHP = enemyHP;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }

    private static void buildTanks() {
        if (!GameCache.factoryList.isEmpty()) {
            Unit factory = GameCache.factoryList.get(0).unit();
            if (!factory.getActive().get()) {
                if (factory.getAddOnTag().isPresent()) {
                    //2 tanks per expansion base
                    if (GameCache.siegeTankList.size() < Math.min(Strategy.MAX_TANKS, Strategy.NUM_TANKS_PER_EXPANSION * (Base.numMyBases() - 1)) &&
                            UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                        ActionHelper.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                    }
                }
                else if (!factory.getFlying().orElse(true) && !Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    ActionHelper.unitCommand(factory, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                }
            }
        }
    }

    private static void sendBarracksHome() {
        if (!isBarracksSentHome) {
            if (UnitUtils.getOrder(barracks.unit()) == Abilities.TRAIN_MARINE) {
                ActionHelper.unitCommand(barracks.unit(), Abilities.CANCEL_LAST, false);
            }
            DelayedAction.delayedActions.add(new DelayedAction(Time.nowFrames()+2, Abilities.LIFT_BARRACKS, barracks));
            DelayedAction.delayedActions.add(new DelayedAction(1, Abilities.LAND, barracks, LocationConstants._3x3Structures.remove(0)));
            isBarracksSentHome = true;
        }
    }

    private static void sendFirstScv() {
        if (!isFirstScvSent && Time.nowFrames() >= Time.toFrames(8)) {
            Unit firstScv = repairScvList.get(0).unit();
            Base.releaseScv(firstScv);
            if (UnitUtils.isCarryingResources(firstScv)) {
                ActionHelper.unitCommand(firstScv, Abilities.HARVEST_RETURN, false);
                ActionHelper.unitCommand(firstScv, Abilities.MOVE, barracksPos, true);
                UnitUtils.patrolInPlace(firstScv, barracksPos);
            }
            else {
                ActionHelper.unitCommand(firstScv, Abilities.MOVE, barracksPos, false);
                UnitUtils.patrolInPlace(firstScv, barracksPos);
            }
            isFirstScvSent = true;
        }
    }

    private static void sendScoutScvs() {
        if (!isScoutScvsSent && Time.nowFrames() >= Time.toFrames(23)) {
            List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(GameCache.baseList.get(0).getResourceMidPoint(), 10);
            scoutScvs = availableScvs.subList(0, 2);
            scoutScvs.forEach(scv -> Base.releaseScv(scv.unit()));
            if (!LocationConstants.MAP.contains("Golden Wall") && !LocationConstants.MAP.contains("Blackburn")) {
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(1)), false);
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(2)), true);
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.clockBasePositions.get(3)), true);
                UnitUtils.patrolInPlace(scoutScvs.get(0).unit(), getResourceMidPoint(LocationConstants.clockBasePositions.get(3)));
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(1)), false);
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(2)), true);
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.counterClockBasePositions.get(3)), true);
                UnitUtils.patrolInPlace(scoutScvs.get(1).unit(), getResourceMidPoint(LocationConstants.counterClockBasePositions.get(3)));
            }
            else {
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.baseLocations.get(3)), false);
                UnitUtils.patrolInPlace(scoutScvs.get(0).unit(), getResourceMidPoint(LocationConstants.baseLocations.get(3)));
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(LocationConstants.baseLocations.get(4)), false);
                UnitUtils.patrolInPlace(scoutScvs.get(1).unit(), getResourceMidPoint(LocationConstants.baseLocations.get(4)));
            }
            isScoutScvsSent = true;
        }
        else if (!scoutScvs.isEmpty()) {
            //if proxy barracks found
            if (Time.nowFrames() < Time.toFrames("3:00")) {
                List<UnitInPool> enemyBarracks = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
                if (!enemyBarracks.isEmpty()) {
                    List<UnitInPool> enemyScv = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_SCV, enemyBarracks.get(0).unit().getPosition().toPoint2d(), 5);
                    Unit scvAttackTarget = (!enemyScv.isEmpty()) ? enemyScv.get(0).unit() : enemyBarracks.get(0).unit();
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, scvAttackTarget, false);
                    UnitUtils.patrolInPlace(UnitUtils.toUnitList(scoutScvs), scvAttackTarget.getPosition().toPoint2d());
                }
                //if no enemy proxy or after enemy proxy cancelled, then go to behindBunkerPos
                else if (scoutScvs.stream().anyMatch(
                        scv -> UnitUtils.getOrder(scv.unit()) == Abilities.ATTACK)) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.MOVE, behindBunkerPos, false);
                }
            }
            //make scoutscv a repairscv when it arrives at bunker
            for (UnitInPool scoutScv : scoutScvs) {
                if (UnitUtils.getDistance(scoutScv.unit(), behindBunkerPos) < 2) {
                    addRepairScv(scoutScv);
                    //send scv to build 2nd proxy bunker and factory
                    if (!didFirstScoutScvIdle) {
                        didFirstScoutScvIdle = true;
                        if (LocationConstants.proxyBunkerPos2 != null) {
                            ActionHelper.unitCommand(scoutScv.unit(), Abilities.MOVE, LocationConstants.proxyBunkerPos2, false);
                            UnitUtils.patrolInPlace(scoutScv.unit(), LocationConstants.proxyBunkerPos2);
                            KetrocBot.purchaseQueue.stream()
                                    .filter(purchase -> purchase instanceof PurchaseStructure &&
                                            ((PurchaseStructure) purchase).getStructureType() == Units.TERRAN_FACTORY)
                                    .findFirst()
                                    .ifPresent(purchase -> ((PurchaseStructure) purchase).setScv(scoutScv.unit()));
                        }
                    }
                }
            }
            scoutScvs.removeIf(scv -> UnitUtils.getDistance(scv.unit(), behindBunkerPos) < 2);

            //send to bunker if idle
            scoutScvs.stream()
                    .map(UnitInPool::unit)
                    .filter(scv -> UnitUtils.getOrder(scv) == Abilities.PATROL)
                    .forEach(scv -> ActionHelper.unitCommand(scv, Abilities.MOVE, behindBunkerPos, false));
        }
    }

    private static Point2d getResourceMidPoint(Point2d basePos) {
        return GameCache.baseList.stream()
                .filter(base -> base.getCcPos().distance(basePos) < 1)
                .findFirst()
                .get()
                .getResourceMidPoint();
    }

//    private static void marineMicro() {
//        //allow first marine to head home
//        if (LocationConstants.opponentRace == Race.TERRAN) {
//            if (getMarineCount() == 1) {
//                ActionHelper.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);
//                return;
//            }
//        }
//        boolean isBunkerComplete = (bunker != null && bunker.unit().getBuildProgress() == 1);
//        List<Unit> proxyMarines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).stream()
//                .filter(marine -> UnitUtils.getDistance(marine, bunkerPos) < 60)
//                .collect(Collectors.toList());
//        for (Unit marine : proxyMarines) {
//            List<Unit> allVisibleDefenders = UnitUtils.getVisibleEnemyUnitsOfType(defenders);
//            Unit closestEnemy = allVisibleDefenders.stream()
//                    .filter(enemy -> UnitUtils.getDistance(enemy, marine) <= 5)
//                    .findFirst()
//                    .orElse(null);
//
//
//            //enter bunker if nearby
//            if (isBunkerComplete && UnitUtils.getDistance(marine, bunker.unit()) < 6) {
//                if (bunker.unit().getPassengers().stream().anyMatch(unitInBunker -> unitInBunker.getType() == Units.TERRAN_SCV)) {
//                    ActionHelper.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL, false);
//                }
//                ActionHelper.unitCommand(marine, Abilities.SMART, bunker.unit(), false);
//            }
//            //no enemies
//            else if (closestEnemy == null) {
//                ActionHelper.unitCommand(marine, Abilities.MOVE, behindBunkerPos, false);
//            }
//            //enemies in range
//            else {
//                //always shoot when available
//                if (UnitUtils.isWeaponAvailable(marine)) {
//                    ActionHelper.unitCommand(marine, Abilities.ATTACK, behindBunkerPos, false);
//                }
//                //retreat from enemy when on weapon cooldown and bunker incomplete
//                else if (isBunkerComplete) {
//                    Point2d retreatPos = Position.towards(marine.getPosition().toPoint2d(), closestEnemy.getPosition().toPoint2d(), -10);
//                    ActionHelper.unitCommand(marine, Abilities.MOVE, retreatPos, false);
//                }
//                else {
//                    ActionHelper.unitCommand(marine, Abilities.MOVE, behindBunkerPos, false);
//                }
//            }
//        }
//    }

    private static void buildMarines() {
        if (UnitUtils.canAfford(Units.TERRAN_MARINE)) {
            ActionHelper.unitCommand(barracks.unit(), Abilities.TRAIN_MARINE, false);
            Cost.updateBank(Units.TERRAN_MARINE);
        }
    }
    private static int getMarineCount() {
        int marineCount = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MARINE).size();
        marineCount += UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER).stream()
                .mapToInt(bunker -> bunker.getCargoSpaceTaken().orElse(0))
                .sum();
        return marineCount;
    }

    private static void scvRepairMicro() {
        List<Unit> availableScvs = getAvailableRepairScvs();
        if (availableScvs.isEmpty()) {
            return;
        }

        List<UnitInPool> injuredUnits = Bot.OBS.getUnits(Alliance.SELF, repairTarget ->
                        repairTargets.contains(repairTarget.unit().getType()) &&
                        repairTarget.unit().getBuildProgress() == 1 &&
                        repairTarget.unit().getHealth().orElse(0f) < repairTarget.unit().getHealthMax().orElse(0f) &&
                        (repairTarget.unit().getType() != Units.TERRAN_SCV || repairScvList.contains(repairTarget)) &&
                        UnitUtils.getDistance(repairTarget.unit(), bunkerPos) < 8)
                .stream()
                .filter(u -> u.unit().getType() != Units.TERRAN_SCV || u.unit().getHealth().orElse(50f) >= 10)
                .sorted(Comparator.comparing(unit -> UnitUtils.getHealthPercentage(unit.unit())))
                .collect(Collectors.toList());

        //move back to position if no repair targets
        if (injuredUnits.isEmpty()) {
            List<Unit> scvsToMove = availableScvs.stream()
                    .filter(scv -> ActionIssued.getCurOrder(scv).isEmpty())
                    .filter(scv -> UnitUtils.getDistance(scv, behindBunkerPos) > 2)
                    .collect(Collectors.toList());
            if (!scvsToMove.isEmpty()) {
                ActionHelper.unitCommand(scvsToMove, Abilities.MOVE, behindBunkerPos, false);
            }
        }
        //repair logic
        else {
            UnitInPool injuredScv = null;
            for (UnitInPool injuredUnit : injuredUnits) {
                //2nd target solely for the injured scv
                if (injuredScv != null) {
                    ActionHelper.unitCommand(injuredScv.unit(), Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    break;
                }
                //if target is an scv
                else if (availableScvs.remove(injuredUnit)) {
                    injuredScv = injuredUnit;
                    if (!availableScvs.isEmpty()) {
                        ActionHelper.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    }
                }
                //if target is not an scv
                else {
                    ActionHelper.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    break;
                }
            }
        }
    }

    public static List<Unit> getAvailableRepairScvs() {
        return repairScvList.stream()
                .map(UnitInPool::unit)
                .filter(scv -> !StructureScv.isScvProducing(scv) && !isRepairingValidTarget(scv))
                .collect(Collectors.toList());
    }

    public static boolean isRepairingValidTarget(Unit scv) {
        if (ActionIssued.getCurOrder(scv).isPresent()) {
            ActionIssued order = ActionIssued.getCurOrder(scv).get();
            if (order.ability == Abilities.EFFECT_REPAIR) {
                if (order.targetTag != null) {
                    UnitInPool target = Bot.OBS.getUnit(order.targetTag);
                    if (target != null && target.unit().getType() != Units.TERRAN_SCV ||
                            repairScvList.stream().anyMatch(u -> u.getTag().equals(target.getTag()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static Unit getClosestAvailableRepairScvs(Point2d targetPos) {
        return repairScvList.stream()
                .map(UnitInPool::unit)
                .filter(scv -> !StructureScv.isScvProducing(scv))
                .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv, targetPos)))
                .orElse(null);
    }

    private static void replaceLowHpScvs() {
        for (int i = 0; i< repairScvList.size(); i++) {
            UnitInPool oldScv = repairScvList.get(i);
            if (!oldScv.isAlive() || oldScv.unit().getHealth().orElse(45f) < 10) {
                if (oldScv.isAlive()) {
                    ActionHelper.unitCommand(oldScv.unit(), Abilities.STOP, false);
                }
                UnitInPool newScv = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 10).get(0);
                Base.releaseScv(newScv.unit());
                Ignored.remove(oldScv.getTag());
                Ignored.add(new IgnoredUnit(newScv.getTag()));
                repairScvList.set(i, newScv);
                if (isFactoryScv(oldScv.getTag())) {
                    setFactoryScv();
                }
                ActionHelper.unitCommand(newScv.unit(), Abilities.MOVE, behindBunkerPos, false);
            }
        }
    }

    public static void addNewRepairScv() {
        UnitInPool newScv = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 10).get(0);
        addRepairScv(newScv);
        ActionHelper.unitCommand(newScv.unit(), Abilities.MOVE, behindBunkerPos, false);
    }

    private static boolean abandonProxy() {
        boolean bunkerDied = updateBunker();
        boolean tank1Died = updateTank1();
        boolean tank2Died = updateTank2();

        //only check if a core defense unit died
        if (!bunkerDied && !tank1Died && !tank2Died) {
            return false;
        }

        //check if contain is broken
        if (!isContainBroken()) {
            if (bunkerDied) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).stream()
                        .forEach(marine -> UnitMicroList.add(new MarineProxyBunker(marine, LocationConstants.proxyBunkerPos)));
            }
            return false;
        }

        //******************************************************
        // CONTAIN BROKEN: cancel everything and send units home
        //******************************************************

        //cancel all proxy construction in progress
        for (int i=0; i<StructureScv.scvBuildingList.size(); i++) {
            StructureScv structureScv = StructureScv.scvBuildingList.get(i);
            //if proxy structure
            if (structureScv.structurePos.distance(LocationConstants.REPAIR_BAY) > 50) {
                structureScv.cancelProduction();
                requeueStructure(structureScv.structureType);
                StructureScv.remove(structureScv);
                i--;
            }
        }

        //cancel all proxy construction in purchase queue
        for (int i = 0; i<KetrocBot.purchaseQueue.size(); i++) {
            if (KetrocBot.purchaseQueue.get(i) instanceof PurchaseStructure) {
                PurchaseStructure structure = (PurchaseStructure) KetrocBot.purchaseQueue.get(i);
                if (structure.getPosition() != null && structure.getPosition().distance(LocationConstants.REPAIR_BAY) > 50) {
                    if (requeueStructure(structure.getStructureType())) {
                        i++;
                    }
                    KetrocBot.purchaseQueue.remove(i--);
                }
            }
        }

        //float barracks home
        if (barracks != null && barracks.isAlive() && barracks.unit().getBuildProgress() == 1) {
            sendBarracksHome();
        }

        //salvage bunker and send marines home
        if (bunker != null && bunker.isAlive()) {
            //empty bunker and salvage
            if (bunker.unit().getBuildProgress() == 1) {
                ActionHelper.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL_BUNKER, false);
                DelayedAction.delayedActions.add(
                        new DelayedAction(Time.nowFrames()+2, Abilities.EFFECT_SALVAGE, bunker));
            }
        }
        else {
            //send marines home
            UnitMicroList.unitMicroList.removeIf(basicUnitMicro -> basicUnitMicro instanceof MarineProxyBunker);

//            UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).stream()
//                    .forEach(marine -> UnitMicroList.add(new BasicUnitMicro(marine, LocationConstants.insideMainWall, MicroPriority.SURVIVAL)));
        }

        //send tanks home
        if (tank1 != null) {
            UnitMicroList.remove(tank1.getTag());
        }
        if (tank2 != null) {
            UnitMicroList.remove(tank2.getTag());
        }

        //send scvs home
        if (!repairScvList.isEmpty()) { //TODO: does this work?  Is this required
            ActionHelper.unitCommand(UnitUtils.toUnitList(repairScvList), Abilities.STOP, false);
            repairScvList.forEach(scv -> Ignored.remove(scv.getTag()));
        }

        //end proxy rush
        proxyBunkerLevel = 0;
        return true;
    }

    private static boolean isContainBroken() {
        return bunker == null &&
                (tank1 == null || tank1.unit().getType() != Units.TERRAN_SIEGE_TANK_SIEGED) &&
                (tank2 == null || tank2.unit().getType() != Units.TERRAN_SIEGE_TANK_SIEGED);
    }

    private static boolean updateBunker() {
        if (bunker != null && !bunker.isAlive()) {
            bunker = null;
            return true;
        }
        return false;
    }

    private static boolean updateTank1() {
        if (tank1 != null && !tank1.isAlive()) {
            tank1 = null;
            return true;
        }
        return false;
    }

    private static boolean updateTank2() {
        if (tank2 != null && !tank2.isAlive()) {
            tank2 = null;
            return true;
        }
        return false;
    }

    private static boolean requeueStructure(Units structureType) {
        switch (structureType) {
            case TERRAN_FACTORY:
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                return true;
            case TERRAN_BARRACKS:
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS));
                return true;
            case TERRAN_BUNKER: case TERRAN_MISSILE_TURRET:
                //don't requeue at home
                return false;
        }
        return false;
    }

    private static Point2d calcTurretPosition(boolean isClockwise) {
        float angle = (isClockwise) ? 65 : -65;
        Point2d turretPos = Position.toWholePoint(
                Position.towards(
                        Position.rotate(behindBunkerPos, bunkerPos, angle),
                        bunkerPos,
                        -3.5f
                )
        );
        DebugHelper.draw3dBox(turretPos, Color.GREEN, 1f);
        Point2d bestPlacementPos = Position.findNearestPlacement(Units.TERRAN_MISSILE_TURRET, turretPos, 2);
        if (bestPlacementPos == null) {
            turretPos = Position.toWholePoint(
                    Position.towards(
                            Position.rotate(behindBunkerPos,
                                    Position.towards(behindBunkerPos, enemyPos, 10),
                                    angle
                            ),
                            enemyPos,
                            -5f
                    )
            );
            bestPlacementPos = Position.findNearestPlacement(Units.TERRAN_MISSILE_TURRET, turretPos, 4);
        }
        return bestPlacementPos;
    }

    private static void onBarracksStarted(UnitInPool bar) {
        barracks = bar;

        //queue marine
        KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_MARINE, barracks));
    }

    public static void onBarracksComplete() {
        //add remainder of build order
        if (proxyBunkerLevel == 2) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
//            Point2d factoryPos = Position.towards(
//                    LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 3),
//                    LocationConstants.pointOnMyRamp,
//                    3);
//            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, factoryPos));
        }

        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));

        if (proxyBunkerLevel == 2) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        }

        //set barracks rally
        Point2d rallyPos = (LocationConstants.proxyBunkerPos2 != null)
                ? Position.towards(LocationConstants.proxyBunkerPos2, LocationConstants.pointOnMyRamp, 2)
                : behindBunkerPos;
        ActionHelper.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, rallyPos, false);
    }

    private static void onBunkerStarted(UnitInPool bunk) {
        bunker = bunk;
        ActionHelper.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);

        //send out another scv
        if (!scoutProxy) {
            addNewRepairScv();
        }

        //use a repairSCV to build proxy factory
        //setFactoryScv();
    }


    private static boolean isFactoryScv(Tag scv) {
        if (proxyBunkerLevel == 2 &&
                //(LocationConstants.MAP.equals(MapNames.ZEN) || LocationConstants.MAP.equals(MapNames.THUNDERBIRD)) &&
                Purchase.isStructureQueued(Units.TERRAN_FACTORY)) {
            for (Purchase purchase : KetrocBot.purchaseQueue) {
                if (purchase instanceof PurchaseStructure) {
                    PurchaseStructure p = (PurchaseStructure) purchase;
                    if (p.getStructureType() == Units.TERRAN_FACTORY) {
                        return p.getScv() != null && p.getScv().getTag().equals(scv);
                    }
                }
            }
        }
        return false;
    }

    private static void setFactoryScv() {
        if (proxyBunkerLevel == 2 && Purchase.isStructureQueued(Units.TERRAN_FACTORY)) {
            UnitInPool factoryScv = repairScvList.get(0);
            for (Purchase purchase : KetrocBot.purchaseQueue) {
                if (purchase instanceof PurchaseStructure) {
                    PurchaseStructure p = (PurchaseStructure) purchase;
                    if (p.getStructureType() == Units.TERRAN_FACTORY) {
                        p.setScv(factoryScv.unit());
                        ActionHelper.unitCommand(factoryScv.unit(), Abilities.MOVE, p.getPosition(), false);
                        UnitUtils.patrolInPlace(factoryScv.unit(), p.getPosition());
                        break;
                    }
                }
            }
        }
    }

    public static void onBunkerComplete() {
        ActionHelper.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);
        if (proxyBunkerLevel != 2) {
            repairScvList.stream()
                    .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), LocationConstants.myMineralPos)))
                    .ifPresent(u -> removeRepairScv(u));
        }
    }

    private static void onFactoryStarted(UnitInPool fact) {
        factory = fact;
        ActionHelper.unitCommand(factory.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);

        Point2d turretPos = calcTurretPosition(true);
        if (!Strategy.NO_TURRETS) {
            if (turretPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
            }
            turretPos = calcTurretPosition(false);
            if (turretPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
            }
        }
    }

    public static void onFactoryComplete() {
        KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
    }

    public static void onFactoryTechLabComplete() {
        KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, factory));
        KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, factory));
    }

    public static void onFactoryLift() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ENGINEERING_BAY).get(0).getTag())));
    }

    public static void onEngineeringBayComplete(UnitInPool engBay) {
//        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, engBay));
//        int depotPurchaseIndex = getDepotPurchaseIndex();
//        Point2d turretPos = calcTurretPosition(true);
//        if (!Strategy.NO_TURRETS) {
//            if (turretPos != null) {
//                KetrocBot.purchaseQueue.add(++depotPurchaseIndex, new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
//            }
//            turretPos = calcTurretPosition(false);
//            if (turretPos != null) {
//                KetrocBot.purchaseQueue.add(++depotPurchaseIndex, new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
//            }
//        }
    }

    private static int getDepotPurchaseIndex() {
        for (int i = 0; i< KetrocBot.purchaseQueue.size(); i++) {
            Purchase p = KetrocBot.purchaseQueue.get(i);
            if (p instanceof PurchaseStructure &&
                    ((PurchaseStructure) p).getStructureType() == Units.TERRAN_SUPPLY_DEPOT) {
                return i;
            }
        }
        return -1;
    }
}
