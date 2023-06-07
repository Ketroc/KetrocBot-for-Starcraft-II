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
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
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
    public static final List<Units> repairTargets = List.of(
            Units.TERRAN_BUNKER, Units.TERRAN_SIEGE_TANK_SIEGED, Units.TERRAN_SIEGE_TANK, Units.TERRAN_MISSILE_TURRET, Units.TERRAN_SCV);
    private static boolean isBarracksSentHome;
    private static Point2d enemyPos;
    private static boolean didFirstScoutScvIdle; //first scv to arrive behindBunkerPos

    public static void onGameStart() {
        //exit
        if (proxyBunkerLevel == 0) {
            return;
        }
        barracksPos = PosConstants.proxyBarracksPos;
        bunkerPos = PosConstants.proxyBunkerPos;
        enemyPos = getEnemyPos();
        defaultSpotterPos = Position.towards(bunkerPos, enemyPos, 9);
        behindBunkerPos = Position.towards(bunkerPos, enemyPos,-2);
        tank1Pos = Position.rotate(
                Position.towards(behindBunkerPos, bunkerPos, -0.6f),
                bunkerPos, PosConstants.opponentRace == Race.TERRAN? 85 : 35);
        tank2Pos = Position.rotate(
                Position.towards(behindBunkerPos, bunkerPos, -0.6f),
                bunkerPos, PosConstants.opponentRace == Race.TERRAN? -85 : -35);
        marinesNeeded = PosConstants.opponentRace == Race.TERRAN ? 3 : 4;
        scoutProxy = (PosConstants.opponentRace == Race.TERRAN);
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

        scoutScvs.forEach(scv -> DebugHelper.boxUnit(scv.unit(), Color.RED));
        repairScvList.forEach(scv -> DebugHelper.boxUnit(scv.unit(), Color.BLUE));

        // ========= SCVS ===========
        if (Time.at(100)) { //build 1st depot
            Unit scv = WorkerManager.getScvEmptyHands(PosConstants.extraDepots.get(0)).unit();
            ActionHelper.unitCommand(scv, Abilities.MOVE, PosConstants.extraDepots.get(0), false);
            UnitUtils.patrolInPlace(scv, PosConstants.extraDepots.get(0));
            ((PurchaseStructure) KetrocBot.purchaseQueue.get(0)).setScv(scv);
        }
        sendFirstScv();
        if (scoutProxy) {
            manageScoutScvs();
        } else if (repairScvList.size() < 2 && Time.nowFrames() > Time.toFrames("0:49")) {
            addNewRepairScv();
        }
        replaceLowHpScvs();
        scvRepairMicro();

        // ========= FACTORY SWAP =========
        if (BunkerContain.proxyBunkerLevel == 2) {
            if (factorySwap == null) {
                if (readyToBuildFactory()) {
                    UnitInPool availableRepairScv = repairScvList.stream()
                            .filter(scv -> UnitUtils.getOrder(scv.unit()) != Abilities.BUILD_BUNKER)
                            .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), PosConstants.proxyBarracksPos)))
                            .orElse(repairScvList.get(0));
                    factorySwap = new AddonSwap(barracks, Abilities.BUILD_TECHLAB_BARRACKS, Units.TERRAN_FACTORY, availableRepairScv);
                    KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_MARINE, barracks));
                    KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_MARINE, barracks));
                }
            }
            else if (factorySwap.removeMe) {
                factorySwap = null;
            }
            else {
                factorySwap.onStep();
            }
        }


        // ========= FACTORY ===========
        if (BunkerContain.proxyBunkerLevel == 2) {
            if (factory == null) {
                List<UnitInPool> allFactories = Bot.OBS.getUnits(Alliance.SELF, factory ->
                        factory.unit().getType() == Units.TERRAN_FACTORY &&
                        UnitUtils.getDistance(factory.unit(), PosConstants.proxyBarracksPos) < 10);
                if (!allFactories.isEmpty()) {
                    onFactoryStarted(allFactories.get(0));
                }
            }
            //if not using factory for tanks
            if (factory != null && factorySwap == null) {
                if (UnitUtils.getOrder(factory.unit()) == null) {
                    if (!Strategy.DO_USE_CYCLONES &&
                            !Strategy.DO_OFFENSIVE_TANKS &&
                            !Strategy.DO_DEFENSIVE_TANKS &&
                            tank1 != null &&
                            tank2 != null &&
                            !factory.unit().getFlying().orElse(true)) {
                        BuildManager.liftFactory(factory.unit());
                        factory = null;
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
            if (UnitUtils.getOrder(barracks.unit()) == null && barracks.unit().getBuildProgress() == 1) {
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
            else {
                //get closest tank within 8 - 16 range of bunker
                Unit closestEnemyTank = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, UnitUtils.SIEGE_TANK_TYPE, bunkerPos);
                //set targetPos to tank towards bunkerPos
                if (closestEnemyTank != null && UnitUtils.getDistance(closestEnemyTank, bunkerPos) < 15) {
                    barracksSpotter.targetPos = Position.towards(closestEnemyTank, bunkerPos,
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
                UnitUtils.numMyUnits(UnitUtils.FACTORY_TYPE, true) < 1 &&
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
                (PosConstants.opponentRace == Race.TERRAN && PosConstants.proxyBunkerPos2 != null) &&
                getMarineCount() == 1) {
            UnitMicroList.add(new MarineProxyBunker(marine, PosConstants.proxyBunkerPos2));
            return;
        }
        UnitMicroList.add(new MarineProxyBunker(marine, PosConstants.proxyBunkerPos));
    }

    public static void addRepairScv(UnitInPool scv) {
        repairScvList.add(scv);
        Base.releaseScv(scv.unit());
        Ignored.add(new IgnoredUnit(scv.getTag()));
    }

    private static Point2d getEnemyPos() {
        Point2d enemyNat = PosConstants.baseLocations.get(PosConstants.baseLocations.size()-2);
        Point2d enemyRamp = PosConstants.enemyRampPos;
        float distance = (float) enemyRamp.distance(enemyNat) /
                ((PosConstants.opponentRace == Race.TERRAN) ? 7 : 3);
        return Position.towards(enemyRamp, enemyNat, distance);
    }

    private static void bunkerTargetting() {
        //do nothing if bunker is empty
        if (bunker.unit().getCargoSpaceTaken().orElse(0) == 0) {
            return;
        }

        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                UnitUtils.getDistance(enemy.unit(), bunker.unit()) < BUNKER_RANGE);
        UnitInPool target = selectTarget(enemiesInRange);
        if (target != null) {
            ActionHelper.unitCommand(bunker.unit(), Abilities.ATTACK, target.unit(), false);
        }
    }

    private static void siegeTankTargetting() {
        for (Unit tank : UnitUtils.myUnitsOfType(Units.TERRAN_SIEGE_TANK_SIEGED)) {
            //only find a target if tank is about to fire
            if (!UnitUtils.isWeaponAvailable(tank)) {
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
            if (UnitUtils.getOrder(factory) == null) {
                if (factory.getAddOnTag().isPresent()) {
                    //2 tanks per expansion base
                    if (GameCache.siegeTankList.size() < Math.min(Strategy.MAX_TANKS, Strategy.NUM_TANKS_PER_EXPANSION * (Base.numMyBases() - 1)) &&
                            UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                        ActionHelper.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                    }
                }
                else if (!factory.getFlying().orElse(true) && !Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    ActionHelper.unitCommand(factory, Abilities.RALLY_BUILDING, PosConstants.insideMainWall, false);
                }
            }
        }
    }

    private static void sendBarracksHome() {
        if (!isBarracksSentHome) {
            if (UnitUtils.getOrder(barracks.unit()) == Abilities.TRAIN_MARINE) {
                ActionHelper.unitCommand(barracks.unit(), Abilities.CANCEL_LAST, false);
                DelayedAction.delayedActions.add(new DelayedAction(Time.nowFrames()+2, Abilities.LIFT_BARRACKS, barracks));
            }
            else {
                ActionHelper.unitCommand(barracks.unit(), Abilities.LIFT_BARRACKS, false);
            }
            DelayedAction.delayedActions.add(new DelayedAction(1, Abilities.LAND, barracks, PosConstants._3x3Structures.remove(0)));
            isBarracksSentHome = true;
        }
    }

    private static void sendFirstScv() {
        if (!isFirstScvSent && Time.at(280)) {
            addRepairScv(WorkerManager.getScvEmptyHands(PosConstants.extraDepots.get(0)));
            Unit firstScv = repairScvList.get(0).unit();
            if (UnitUtils.isCarryingResources(firstScv)) {
                ActionHelper.unitCommand(firstScv, Abilities.HARVEST_RETURN, false);
                ActionHelper.unitCommand(firstScv, Abilities.MOVE, barracksPos, true);
            }
            else {
                ActionHelper.unitCommand(firstScv, Abilities.MOVE, barracksPos, false);
            }
            UnitUtils.patrolInPlace(firstScv, barracksPos);
            isFirstScvSent = true;
        }
    }

    private static void manageScoutScvs() {
        if (!isScoutScvsSent && Time.nowFrames() >= Time.toFrames(34)) {
            while (scoutScvs.size() < 2) {
                UnitInPool newScv = WorkerManager.getScvEmptyHands(PosConstants.BUNKER_NATURAL);
                if (newScv == null) {
                    return;
                }
                scoutScvs.add(newScv);
                Base.releaseScv(newScv.unit());
            }
            if (!PosConstants.MAP.contains("Golden Wall") && !PosConstants.MAP.contains("Blackburn")) {
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.clockBasePositions.get(1)), false);
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.clockBasePositions.get(2)), true);
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.clockBasePositions.get(3)), true);
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.counterClockBasePositions.get(1)), false);
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.counterClockBasePositions.get(2)), true);
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.counterClockBasePositions.get(3)), true);
            }
            else { //for goldenwall and blackburn only
                ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.baseLocations.get(3)), false);
                ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, getResourceMidPoint(PosConstants.baseLocations.get(4)), false);
            }
            ActionHelper.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, behindBunkerPos, true);
            ActionHelper.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, behindBunkerPos, true);
            isScoutScvsSent = true;
        }
        else if (scoutScvs.size() > 0) {
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
                        if (PosConstants.proxyBunkerPos2 != null) {
                            ActionHelper.unitCommand(scoutScv.unit(), Abilities.MOVE, PosConstants.proxyBunkerPos2, false);
                            UnitUtils.patrolInPlace(scoutScv.unit(), PosConstants.proxyBunkerPos2);
                            KetrocBot.purchaseQueue.stream()
                                    .filter(purchase -> purchase instanceof PurchaseStructure &&
                                            ((PurchaseStructure) purchase).getStructureType() == Units.TERRAN_BUNKER)
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
        marineCount += UnitUtils.myUnitsOfType(Units.TERRAN_BUNKER).stream()
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
                        (repairTarget.unit().getType() != Units.TERRAN_SCV || repairScvList.stream().anyMatch(scv -> scv.getTag().equals(repairTarget.getTag()))) &&
                        UnitUtils.getDistance(repairTarget.unit(), bunkerPos) < 8)
                .stream()
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
            return;
        }

        //repair logic
        for (Units repairType : repairTargets) {
            Unit target = injuredUnits.stream()
                    .filter(u -> u.unit().getType() == repairType)
                    .findFirst()
                    .map(UnitInPool::unit)
                    .orElse(null);
            if (target != null && !availableScvs.isEmpty()) {
                ActionHelper.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, target, false);
                return;
            }
        } //TODO: have injured scv target repair something else

//        UnitInPool injuredScv = null;
//        for (UnitInPool injuredUnit : injuredUnits) {
//            //2nd target solely for the injured scv
//            if (injuredScv != null) {
//                ActionHelper.unitCommand(injuredScv.unit(), Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
//                break;
//            }
//            //if target is an scv
//            else if (availableScvs.remove(injuredUnit)) {
//                injuredScv = injuredUnit;
//                if (!availableScvs.isEmpty()) {
//                    ActionHelper.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
//                }
//            }
//            //if target is not an scv
//            else {
//                ActionHelper.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
//                break;
//            }
//        }
    }

    public static List<Unit> getAvailableRepairScvs() {
        return repairScvList.stream()
                .map(UnitInPool::unit)
                .filter(scv -> !StructureScv.contains(scv) && !isRepairingValidTarget(scv))
                .collect(Collectors.toList());
    }

    public static boolean isRepairingValidTarget(Unit scv) {
        if (ActionIssued.getCurOrder(scv).isPresent()) {
            ActionIssued scvOrder = ActionIssued.getCurOrder(scv).get();
            if (scvOrder.ability == Abilities.EFFECT_REPAIR) {
                if (scvOrder.targetTag != null) {
                    UnitInPool repairTarget = Bot.OBS.getUnit(scvOrder.targetTag);
                    //valid repair targets: any non-scv or repairScvs
                    if (repairTarget != null && (
                            repairTarget.unit().getType() != Units.TERRAN_SCV ||
                            repairScvList.stream().anyMatch(u -> u.getTag().equals(repairTarget.getTag())))) {
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
                .filter(scv -> !StructureScv.contains(scv))
                .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv, targetPos)))
                .orElse(null);
    }

    private static void replaceLowHpScvs() {
        for (int i = 0; i< repairScvList.size(); i++) {
            UnitInPool oldScv = repairScvList.get(i);
            if (!oldScv.isAlive() || oldScv.unit().getHealth().orElse(45f) < 10) {
                if (oldScv.isAlive()) {
                    UnitUtils.returnAndStopScv(oldScv);
                }
                UnitInPool newScv = WorkerManager.getScv(PosConstants.proxyBunkerPos, scv -> scv.unit().getHealth().orElse(0f) > 40);
                if (newScv == null) {
                    return;
                }
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
        UnitInPool newScv = WorkerManager.getScvEmptyHands(bunkerPos, scv -> scv.unit().getHealth().orElse(0f) > 40);
        if (newScv == null) {
            return;
        }
        addRepairScv(newScv);
        ActionHelper.unitCommand(newScv.unit(), Abilities.MOVE, bunkerPos, false);
        UnitUtils.patrolInPlace(newScv.unit(), bunkerPos);
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
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos));
                UnitUtils.myUnitsOfType(Units.TERRAN_MARINE).stream()
                        .forEach(marine -> UnitMicroList.add(new MarineProxyBunker(marine, PosConstants.proxyBunkerPos)));
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
            if (structureScv.structurePos.distance(PosConstants.REPAIR_BAY) > 50) {
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
                if (structure.getPosition() != null && structure.getPosition().distance(PosConstants.REPAIR_BAY) > 50) {
                    if (requeueStructure(structure.getStructureType())) {
                        i++;
                    }
                    KetrocBot.purchaseQueue.remove(i--);
                }
            }
        }

        //cancel all siege tanks queued
        Purchase.removeAll(Units.TERRAN_SIEGE_TANK);

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
            repairScvList.forEach(scv -> {
                UnitUtils.returnAndStopScv(scv);
                Ignored.remove(scv.getTag());
            });
        }

        //end proxy rush
        proxyBunkerLevel = 0;
        ArmyManager.doOffense = false;
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
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_FACTORY));
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
        //DebugHelper.draw3dBox(turretPos, Color.GREEN, 1f);
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


    public static void onBarracksComplete() {
        //set barracks rally
        Point2d rallyPos = (PosConstants.proxyBunkerPos2 != null && PosConstants.opponentRace == Race.TERRAN)
                ? Position.towards(PosConstants.proxyBunkerPos2, PosConstants.myRampPos, 2)
                : behindBunkerPos;
        ActionHelper.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, rallyPos, false);
    }

    private static void onBunkerStarted(UnitInPool bunk) {
        bunker = bunk;
        ActionHelper.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, PosConstants.insideMainWall, false);

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
//        if (proxyBunkerLevel != 2) {
//            repairScvList.stream()
//                    .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), PosConstants.myMineralPos)))
//                    .ifPresent(u -> removeRepairScv(u));
//        }
    }

    private static void onFactoryStarted(UnitInPool fact) {
        factory = fact;

        //set siege tank purchases to this factory
        KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseUnit)
                .map(p -> (PurchaseUnit) p)
                .filter(p -> p.getUnitType() == Units.TERRAN_SIEGE_TANK)
                .forEach(p -> p.setProductionStructure(factory));

        Point2d turretPos = calcTurretPosition(true);
        if (!Strategy.NO_TURRETS && PosConstants.opponentRace == Race.TERRAN) {
            if (turretPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
            }
            turretPos = calcTurretPosition(false);
            if (turretPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
            }
        }
    }

    public static void onFactoryLift() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING));
        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR));
    }

    public static boolean requiresTanks() {
        return proxyBunkerLevel == 2 && (tank1 == null || tank2 == null);
    }
}