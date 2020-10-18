package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.BuildManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.*;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

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
    public static UnitInPool barracks;
    public static UnitInPool factory;
    public static UnitInPool bunker;
    public static Point2d behindBunkerPos;
    public static Point2d siegeTankPos;
    public static final Set<Units> defenders = Set.of(Units.PROTOSS_PROBE, Units.PROTOSS_ZEALOT);
    public static final Set<Units> repairTargets = Set.of(
            Units.TERRAN_SCV, Units.TERRAN_BUNKER, Units.TERRAN_SIEGE_TANK_SIEGED, Units.TERRAN_SIEGE_TANK, Units.TERRAN_MISSILE_TURRET);
    private static boolean isBarracksSentHome;

    public static void onGameStart() {
        //exit
        if (proxyBunkerLevel == 0) {
            return;
        }
        barracksPos = LocationConstants.proxyBarracksPos;
        bunkerPos = LocationConstants.proxyBunkerPos;
        behindBunkerPos = getBehindBunkerPos();
        siegeTankPos = Position.towards(behindBunkerPos, bunkerPos, -1.4f);
        marinesNeeded = (LocationConstants.opponentRace == Race.TERRAN) ? 5 : 4;
        scoutProxy = (LocationConstants.opponentRace == Race.TERRAN);
    }

    public static void addRepairScv(UnitInPool scv) {
        repairScvList.add(scv);
        Ignored.add(new IgnoredUnit(scv.getTag()));
    }

    public static void removeRepairScv(UnitInPool scv) {
        repairScvList.remove(scv);
        Ignored.remove(scv.getTag());
    }

    private static Point2d getBehindBunkerPos() {
        Point2d enemyMain = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1);
        Point2d enemyNat = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-2);
        Point2d betweenMainAndNat = Position.towards(enemyNat, enemyMain, (float)enemyMain.distance(enemyNat)/4);
        return Position.towards(bunkerPos, betweenMainAndNat,-2);
    }

    public static void onStep() {
        //exit
        if (proxyBunkerLevel == 0) {
            return;
        }

        // ========= SCVS ===========
        sendFirstScv();
        if (scoutProxy) {
            sendScoutScvs();
        }
        updateScvs();

        // ========= BARRACKS ===========
        if (barracks == null) {
            List<UnitInPool> allBarracks = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BARRACKS, barracksPos, 1);
            if (!allBarracks.isEmpty()) {
                onBarracksStarted(allBarracks.get(0));
            }
        }
        else if (!barracks.isAlive()) {
            if (bunker == null || bunker.unit().getCargoSpaceTaken().orElse(0) < 1) {
                abandonProxy();
            }
        }
        else if (!barracks.unit().getActive().orElse(true)) {
            if (getMarineCount() < marinesNeeded) {
                buildMarines();
                return;
            }
            else {
                sendBarracksHome();
            }
        }

        // ========= BUNKER ===========
        if (bunker == null) {
            List<UnitInPool> allBunkers = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, bunkerPos, 1);
            if (!allBunkers.isEmpty()) {
                onBunkerStarted(allBunkers.get(0));
            }
        }
        else if (!bunker.isAlive()) {
            abandonProxy();
            return;
        }
        else {
            bunkerTargetting();
        }

        // ========= MARINES ===========
        marineMicro();

        if (BunkerContain.proxyBunkerLevel == 2) {
            // ========= FACTORY ===========
            if (factory == null) {
                List<UnitInPool> allFactories = Bot.OBS.getUnits(Alliance.SELF, factory -> factory.unit().getType() == Units.TERRAN_FACTORY);
                if (!allFactories.isEmpty()) {
                    onFactoryStarted(allFactories.get(0));
                }
            }
            else if (!factory.unit().getActive().orElse(true)) {
                if (UnitUtils.getNumFriendlyUnits(UnitUtils.SIEGE_TANK_TYPE, false) < 2) {
                    buildTanks();
                }
                else if (!factory.unit().getFlying().orElse(true)) {
                    BuildManager.liftFactory();
                }
            }

            // ========= TANKS ===========
            tankMicro();
            siegeTankTargetting();

        }

    }

    private static void bunkerTargetting() {
        //do nothing if bunker is empty
        if (bunker.unit().getCargoSpaceTaken().orElse(0) == 0) {
            return;
        }

        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.getDistance(enemy.unit(), bunker.unit()) < BUNKER_RANGE);
        UnitInPool target = selectTarget(enemiesInRange);
        if (target != null) {
            Bot.ACTION.unitCommand(bunker.unit(), Abilities.ATTACK, target.unit(), false);
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
                Bot.ACTION.unitCommand(tank, Abilities.ATTACK, target.unit(), false);
            }
        }
    }

    private static UnitInPool selectTarget(List<UnitInPool> enemiesInRange) { //TODO: consider damage (eg better for tank to hit 50hp unit than 5hp unit)
        UnitInPool bestTarget = null; //best target will be lowest hp unit without barrier
        float bestTargetHP = Float.MAX_VALUE;
        for (UnitInPool enemy : enemiesInRange) {
            float enemyHP = enemy.unit().getHealth().orElse(0f) + enemy.unit().getShield().orElse(0f);
            if (enemyHP < bestTargetHP && !enemy.unit().getBuffs().contains(Buffs.PROTECTIVE_BARRIER)) {
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
                        Bot.ACTION.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                    }
                }
                else if (!factory.getFlying().orElse(true) && !Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    BansheeBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    Bot.ACTION.unitCommand(factory, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                }
            }
        }
    }

    private static void sendBarracksHome() {
        if (!isBarracksSentHome) {
            if (!barracks.unit().getOrders().isEmpty() && barracks.unit().getOrders().get(0).getAbility() == Abilities.TRAIN_MARINE) {
                Bot.ACTION.unitCommand(barracks.unit(), Abilities.CANCEL_LAST, false);
            }
            DelayedAction.delayedActions.add(new DelayedAction(Bot.OBS.getGameLoop()+2, Abilities.LIFT_BARRACKS, barracks));
            DelayedAction.delayedActions.add(new DelayedAction(1, Abilities.LAND, barracks, LocationConstants._3x3Structures.remove(0)));
            isBarracksSentHome = true;
        }
    }

    private static void sendFirstScv() {
        if (!isFirstScvSent && Bot.OBS.getGameLoop() >= 222) {
            Unit firstScv = repairScvList.get(0).unit();
            if (UnitUtils.isCarryingResources(firstScv)) {
                Bot.ACTION.unitCommand(firstScv, Abilities.HARVEST_RETURN, false)
                        .unitCommand(firstScv, Abilities.MOVE, barracksPos, true)
                        .unitCommand(firstScv, Abilities.HOLD_POSITION, true);
            }
            else {
                Bot.ACTION.unitCommand(firstScv, Abilities.MOVE, barracksPos, false)
                        .unitCommand(firstScv, Abilities.HOLD_POSITION, true);
            }
            isFirstScvSent = true;
        }
    }

    private static void sendScoutScvs() {
        if (!isScoutScvsSent && Bot.OBS.getGameLoop() >= Time.toFrames("0:39")) {
            List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 10);
            scoutScvs = availableScvs.subList(0, 2);
            Bot.ACTION.unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, LocationConstants.baseLocations.get(2), false)
                    .unitCommand(scoutScvs.get(0).unit(), Abilities.MOVE, LocationConstants.baseLocations.get(3), true)
                    .unitCommand(scoutScvs.get(0).unit(), Abilities.HOLD_POSITION, true);
            Bot.ACTION.unitCommand(scoutScvs.get(1).unit(), Abilities.MOVE, LocationConstants.baseLocations.get(4), false)
                    .unitCommand(scoutScvs.get(1).unit(), Abilities.HOLD_POSITION, true);
            isScoutScvsSent = true;
        }
        else if (!scoutScvs.isEmpty()) {
            //if proxy barracks found
            if (Bot.OBS.getGameLoop() < Time.toFrames("3:00")) {
                List<UnitInPool> enemyBarracks = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
                if (!enemyBarracks.isEmpty()) {
                    List<UnitInPool> enemyScv = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_SCV, enemyBarracks.get(0).unit().getPosition().toPoint2d(), 5);
                    if (!enemyScv.isEmpty()) {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, enemyScv.get(0).unit(), false)
                                .unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.HOLD_POSITION, true);
                    }
                    else {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, enemyBarracks.get(0).unit(), false)
                                .unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.HOLD_POSITION, true);
                    }
                }
                //if proxy attack cancelled barracks, then go to behindBunkerPos
                else if (scoutScvs.stream().anyMatch(
                        scv -> !scv.unit().getOrders().isEmpty() && scv.unit().getOrders().get(0).getAbility() == Abilities.ATTACK)) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.MOVE, behindBunkerPos, false);
                }
            }
            //make scoutscv a repairscv when it arrives at bunker
            scoutScvs.stream()
                    .filter(scv -> UnitUtils.getDistance(scv.unit(), behindBunkerPos) < 2)
                    .forEach(scv -> addRepairScv(scv));
            scoutScvs.removeIf(scv -> UnitUtils.getDistance(scv.unit(), behindBunkerPos) < 2);

            //send to bunker if idle
            scoutScvs.stream()
                    .map(UnitInPool::unit)
                    .filter(scv -> !scv.getOrders().isEmpty() && scv.getOrders().get(0).getAbility() == Abilities.HOLD_POSITION)
                    .forEach(scv -> Bot.ACTION.unitCommand(scv, Abilities.MOVE, behindBunkerPos, false));
        }
    }

    private static void marineMicro() {
        //allow first marine to head home
        if (LocationConstants.opponentRace == Race.TERRAN) {
            if (getMarineCount() == 1) {
                Bot.ACTION.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);
                return;
            }
        }
        boolean isBunkerComplete = (bunker != null && bunker.unit().getBuildProgress() == 1);
        List<Unit> proxyMarines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).stream()
                .filter(marine -> UnitUtils.getDistance(marine, bunkerPos) < 60)
                .collect(Collectors.toList());
        for (Unit marine : proxyMarines) {
            List<Unit> allVisibleDefenders = UnitUtils.getVisibleEnemyUnitsOfType(defenders);
            Unit closestEnemy = allVisibleDefenders.stream()
                    .filter(enemy -> UnitUtils.getDistance(enemy, marine) <= 5)
                    .findFirst()
                    .orElse(null);


            //enter bunker if nearby
            if (isBunkerComplete && UnitUtils.getDistance(marine, bunker.unit()) < 6) {
                if (bunker.unit().getPassengers().stream().anyMatch(unitInBunker -> unitInBunker.getType() == Units.TERRAN_SCV)) {
                    Bot.ACTION.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL, false);
                }
                Bot.ACTION.unitCommand(marine, Abilities.SMART, bunker.unit(), false);
            }
            //no enemies
            else if (closestEnemy == null) {
                Bot.ACTION.unitCommand(marine, Abilities.MOVE, behindBunkerPos, false);
            }
            //enemies in range
            else {
                //always shoot when available
                if (marine.getWeaponCooldown().orElse(1f) == 0) {
                    Bot.ACTION.unitCommand(marine, Abilities.ATTACK, behindBunkerPos, false);
                }
                //retreat from enemy when on weapon cooldown and bunker incomplete
                else if (isBunkerComplete) {
                    Point2d retreatPos = Position.towards(marine.getPosition().toPoint2d(), closestEnemy.getPosition().toPoint2d(), -10);
                    Bot.ACTION.unitCommand(marine, Abilities.MOVE, retreatPos, false);
                }
                else {
                    Bot.ACTION.unitCommand(marine, Abilities.MOVE, behindBunkerPos, false);
                }
            }
        }
    }

    private static void tankMicro() {
        for (Unit tank : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK)) {
            List<Unit> allVisibleDefenders = UnitUtils.getVisibleEnemyUnitsOfType(defenders);
            Unit closestEnemy = allVisibleDefenders.stream()
                    .filter(enemy -> UnitUtils.getDistance(enemy, tank) <= 7)
                    .findFirst()
                    .orElse(null);


            //siege if in position
            if (UnitUtils.getDistance(tank, siegeTankPos) < 1.5) {
                Bot.ACTION.unitCommand(tank, Abilities.MORPH_SIEGE_MODE, false);
            }
            //no enemies
            else if (closestEnemy == null) {
                Bot.ACTION.unitCommand(tank, Abilities.ATTACK, siegeTankPos, false);
            }
            //enemies in range
            else {
                //always shoot when available
                if (tank.getWeaponCooldown().orElse(1f) == 0) {
                    Bot.ACTION.unitCommand(tank, Abilities.ATTACK, siegeTankPos, false);
                }
                //always retreat from enemy when on weapon cooldown
                else {
                    Point2d retreatPos = Position.towards(tank.getPosition().toPoint2d(), closestEnemy.getPosition().toPoint2d(), -10);
                    Bot.ACTION.unitCommand(tank, Abilities.MOVE, retreatPos, false);
                }
            }
        }
    }

    private static void buildMarines() {
        if (UnitUtils.canAfford(Units.TERRAN_MARINE)) {
            Bot.ACTION.unitCommand(barracks.unit(), Abilities.TRAIN_MARINE, false);
            Cost.updateBank(Units.TERRAN_MARINE);
        }
    }
    private static int getMarineCount() {
        int marineCount = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).size();
        marineCount += UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER).stream()
                .mapToInt(bunker -> bunker.getCargoSpaceTaken().orElse(0))
                .sum();
        return marineCount;
    }

    private static void updateScvs() {
        replaceDeadScvs();
        giveScvsCommands();
    }

    private static void giveScvsCommands() {
        scvRepairMicro();
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
                .sorted(Comparator.comparing(unit -> UnitUtils.getHealthPercentage(unit.unit())))
                .collect(Collectors.toList());

        //move back to position if no repair targets
        if (injuredUnits.isEmpty()) {
            List<Unit> scvsToMove = availableScvs.stream()
                    .filter(scv -> scv.getOrders().isEmpty())
                    .filter(scv -> UnitUtils.getDistance(scv, behindBunkerPos) > 2)
                    .collect(Collectors.toList());
            if (!scvsToMove.isEmpty()) {
                Bot.ACTION.unitCommand(scvsToMove, Abilities.MOVE, behindBunkerPos, false);
            }
        }
        //repair logic
        else {
            UnitInPool injuredScv = null;
            for (UnitInPool injuredUnit : injuredUnits) {
                //2nd target solely for the injured scv
                if (injuredScv != null) {
                    Bot.ACTION.unitCommand(injuredScv.unit(), Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    break;
                }
                //if target is an scv
                else if (availableScvs.remove(injuredUnit)) {
                    injuredScv = injuredUnit;
                    if (!availableScvs.isEmpty()) {
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    }
                }
                //if target is not an scv
                else {
                    Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    break;
                }
            }
        }
    }

    private static List<Unit> getAvailableRepairScvs() {
        return repairScvList.stream()
                .map(UnitInPool::unit)
                .filter(scv -> scv.getOrders().isEmpty() || !scv.getOrders().get(0).getAbility().toString().contains("BUILD"))
                .collect(Collectors.toList());
    }

    private static void replaceDeadScvs() {
        for (int i = 0; i< repairScvList.size(); i++) {
            UnitInPool oldScv = repairScvList.get(i);
            if (!oldScv.isAlive() || oldScv.unit().getHealth().orElse(45f) < 10) {
                if (oldScv.isAlive()) {
                    Bot.ACTION.unitCommand(oldScv.unit(), Abilities.SMART, GameCache.baseList.get(0).getRallyNode(), false);
                }
                UnitInPool newScv = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 10).get(0);
                Ignored.remove(oldScv.getTag());
                Ignored.add(new IgnoredUnit(newScv.getTag()));
                repairScvList.set(i, newScv);
                if (isFactoryScv(oldScv.getTag())) {
                    setFactoryScv();
                }
                Bot.ACTION.unitCommand(newScv.unit(), Abilities.MOVE, behindBunkerPos, false);
            }
        }
    }

    public static void addAnotherRepairScv() {
        UnitInPool newScv = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 10).get(0);
        addRepairScv(newScv);
        Bot.ACTION.unitCommand(newScv.unit(), Abilities.MOVE, behindBunkerPos, false);
    }

    private static void abandonProxy() {
        //cancel all proxy construction in progress
        for (int i=0; i<StructureScv.scvBuildingList.size(); i++) {
            StructureScv structureScv = StructureScv.scvBuildingList.get(i);
            //if proxy structure
            if (structureScv.structurePos.distance(LocationConstants.REPAIR_BAY) > 50) {
                requeueStructure(structureScv.structureType);
                StructureScv.remove(structureScv);
                i--;
            }
        }
        //cancel all proxy construction in queue
        for (int i=0; i<BansheeBot.purchaseQueue.size(); i++) {
            if (BansheeBot.purchaseQueue.get(i) instanceof PurchaseStructure) {
                PurchaseStructure structure = (PurchaseStructure)BansheeBot.purchaseQueue.get(i);
                if (structure.getPosition().distance(LocationConstants.REPAIR_BAY) > 50) {
                    requeueStructure(structure.getStructureType());
                    BansheeBot.purchaseQueue.remove(i--);
                }
            }
        }

        if (barracks.isAlive()) {
            //cancel barracks in production
            if (barracks.unit().getBuildProgress() < 1) {
                Bot.ACTION.unitCommand(barracks.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS));
            }
            //send completed barracks home
            else {
                sendBarracksHome();
            }
        }

        if (bunker.isAlive()) {
            //cancel bunker in production
            if (bunker.unit().getBuildProgress() < 1) {
                Bot.ACTION.unitCommand(bunker.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
            }
            //empty bunker and salvage
            else {
                Bot.ACTION.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL_BUNKER, false);
                DelayedAction.delayedActions.add(
                        new DelayedAction(Bot.OBS.getGameLoop()+2, Abilities.EFFECT_SALVAGE, bunker));
            }
        }
        else {
            //send marines home on a-move
            List<Unit> marines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
            Bot.ACTION.unitCommand(marines, Abilities.ATTACK, LocationConstants.insideMainWall, false);
        }

        //set up scvs to be sent to mine
        if (!repairScvList.isEmpty()) { //TODO: does this work?  Is this required
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(repairScvList), Abilities.STOP, false);
            repairScvList.forEach(scv -> Ignored.remove(scv.getTag()));
        }

        //end proxy rush
        proxyBunkerLevel = 0;
    }

    private static void requeueStructure(Units structureType) {
        switch (structureType) {
            case TERRAN_FACTORY:
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                break;
            case TERRAN_BARRACKS:
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS));
                break;
            case TERRAN_BUNKER: case TERRAN_MISSILE_TURRET:
                //don't requeue at home
                break;
        }
    }

    private static Point2d calcTurretPosition() {
        boolean xBigger = Math.abs(bunkerPos.getX() - behindBunkerPos.getX()) > Math.abs(bunkerPos.getY() - behindBunkerPos.getY());
        if (xBigger) {
            Point2d testPoint = Position.towards(bunkerPos, behindBunkerPos, 1.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 0.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -0.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, -1.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 1.5f, -2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, -2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 0.5f, -2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -0.5f, -2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
        }
        else {
            Point2d testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, 0.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, 1.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, -0.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -1.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5f, 1.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5f, 0.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5f, -0.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5f, 2.5f);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
        }
        System.out.println("--- ERROR ---- NO PROXY TURRET POSITION FOUND -----");
        return null;
    }

    private static void onBarracksStarted(UnitInPool bar) {
        barracks = bar;
        Bot.ACTION.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, LocationConstants.BUNKER_NATURAL, false);
    }

    public static void onBarracksComplete() {
        //change proxyBunker scv to nearest scv
        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(UnitUtils.getClosestUnitOfType(Alliance.SELF, Units.TERRAN_SCV, bunkerPos), Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
    }

    private static void onBunkerStarted(UnitInPool bunk) {
        bunker = bunk;
        Bot.ACTION.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);

        //send out 2nd scv
        if (!scoutProxy) {
            addAnotherRepairScv();
        }

        //use a repairSCV to build proxy factory
        setFactoryScv();
    }


    private static boolean isFactoryScv(Tag scv) {
        if (proxyBunkerLevel == 2 &&
                //(LocationConstants.MAP.equals(MapNames.ZEN) || LocationConstants.MAP.equals(MapNames.THUNDERBIRD)) &&
                Purchase.isStructureQueued(Units.TERRAN_FACTORY)) {
            for (Purchase purchase : BansheeBot.purchaseQueue) {
                if (purchase instanceof PurchaseStructure) {
                    PurchaseStructure p = (PurchaseStructure) purchase;
                    if (p.getStructureType() == Units.TERRAN_FACTORY) {
                        return p.getScv().getTag().equals(scv);
                    }
                }
            }
        }
        return false;
    }

    private static void setFactoryScv() {
        if (proxyBunkerLevel == 2 &&
                //(LocationConstants.MAP.equals(MapNames.ZEN) || LocationConstants.MAP.equals(MapNames.THUNDERBIRD)) &&
                Purchase.isStructureQueued(Units.TERRAN_FACTORY)) {
            UnitInPool factoryScv = repairScvList.get(0);
            for (Purchase purchase : BansheeBot.purchaseQueue) {
                if (purchase instanceof PurchaseStructure) {
                    PurchaseStructure p = (PurchaseStructure) purchase;
                    if (p.getStructureType() == Units.TERRAN_FACTORY) {
                        p.setScv(factoryScv.unit());
                        Bot.ACTION.unitCommand(factoryScv.unit(), Abilities.MOVE, p.getPosition(), false)
                                .unitCommand(factoryScv.unit(), Abilities.HOLD_POSITION, true);
                        break;
                    }
                }
            }
        }
    }

    public static void onBunkerComplete() {
        Bot.ACTION.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);
//        //hide scv in bunker if enemy army unit nearby and marine hasn't arrived yet
//        if (GameCache.pointRaiseDepots[Position.getMapCoord(bunkerPos.getX())][Position.getMapCoord(bunkerPos.getY())] &&
//                !UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_MARINE, bunkerPos, 10)) {
//            repairScvs.stream()
//                    .map(UnitInPool::unit)
//                    .filter(scv -> UnitUtils.getDistance(scv, bunkerPos) < 5)
//                    .forEach(scv -> Bot.ACTION.unitCommand(scv, Abilities.MOVE, bunker.unit(), false));
//        }
    }

    private static void onFactoryStarted(UnitInPool fact) {
        factory = fact;
        Bot.ACTION.unitCommand(factory.unit(), Abilities.RALLY_BUILDING, siegeTankPos, false);
        WorkerManager.scvsPerGas = 2;
    }

    public static void onFactoryComplete() {
        BansheeBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
    }

    public static void onTechLabComplete() {
        BansheeBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, factory.unit()));
    }

    public static void onFactoryLift() {
        BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        BansheeBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ENGINEERING_BAY).get(0).getTag())));
    }

    public static void onEngineeringBayComplete(UnitInPool engBay) {
        BansheeBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, engBay));
        List<Unit> availableScvs = getAvailableRepairScvs();
        Point2d turretPos = calcTurretPosition();
        if (turretPos != null) {
            if (!availableScvs.isEmpty()) {
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(availableScvs.get(0), Units.TERRAN_MISSILE_TURRET, turretPos));
            } else {
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
            }
        }
    }
}