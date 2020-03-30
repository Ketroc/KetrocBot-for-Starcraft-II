package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.EnemyUnit;
import com.ketroc.terranbot.models.Gas;

import java.util.*;

public class GameState {

    public static int mineralBank;
    public static int gasBank;

    public static final Point2d SCREEN_BOTTOM_LEFT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP0().toPoint2d();
    public static final Point2d SCREEN_TOP_RIGHT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP1().toPoint2d();
    public static boolean[][] pointDetected = null;
//    public static boolean[][] pointUnsafeFromGround = null;
//    public static boolean[][] pointUnsafeFromAir = null;
    public static boolean[][] pointInBansheeRange = null;
    public static boolean[][] pointInVikingRange = null;
    public static byte[][] threatToAir = null;

    public static float z;

    public static final List<Unit> ccList = new ArrayList<>();
    public static final List<UnitInPool> scvMineralList = new ArrayList<>();
    public static final List<UnitInPool> scvIdleList = new ArrayList<>();
    public static final List<UnitInPool> barracksList = new ArrayList<>();
    public static final List<UnitInPool> factoryList = new ArrayList<>();
    public static final List<UnitInPool> starportList = new ArrayList<>();
    public static final List<UnitInPool> refineryList = new ArrayList<>();
    public static final List<UnitInPool> siegeTankList = new ArrayList<>();
    public static final List<Unit> bansheeList = new ArrayList<>();
    public static final List<Unit> vikingList = new ArrayList<>();
    public static final List<Unit> bansheeDivers = new ArrayList<>();
    public static final List<Unit> vikingDivers = new ArrayList<>();
    public static final List<UnitInPool> mineralNodeList = new ArrayList<>();
    public static final List<UnitInPool> geyserList = new ArrayList<>();
    public static final List<Base> baseList = new ArrayList<>();
    public static final List<Unit> inProductionList = new ArrayList<>();

    public static final List<Unit> enemyAllUnits = new ArrayList<>();
    public static final List<Unit> enemyAttacksAir = new ArrayList<>();
    public static final List<Unit> enemyIsGround = new ArrayList<>();
    public static final List<Unit> enemyIsAir = new ArrayList<>();
    public static final List<Unit> enemyDetector = new ArrayList<>();
    public static final List<EnemyUnit> enemyMappingList = new ArrayList<>();

    public static final Map<Ability, Integer> productionMap = new HashMap<>(); //only counts structures scvs are on the way to build but haven't started yet
    public static final Set<Tag> claimedGases = new HashSet<>();
    public static final List<UnitInPool> otherFriendliesList = new ArrayList<>();
    public static final Map<Units, List<UnitInPool>> allFriendliesMap = new HashMap<>();
    public static Unit mineralNodeRally;

    public static void onStep() {
        mineralBank = Bot.OBS.getMinerals();
        gasBank = Bot.OBS.getVespene();
        productionMap.clear();
        refineryList.clear();
        geyserList.clear();
        baseList.clear();
        mineralNodeList.clear();
        ccList.clear();
        vikingList.clear();
        siegeTankList.clear();
        bansheeList.clear();
        bansheeDivers.clear();
        vikingDivers.clear();
        starportList.clear();
        factoryList.clear();
        barracksList.clear();
        scvIdleList.clear();
        scvMineralList.clear();
        claimedGases.clear();
        otherFriendliesList.clear();
        allFriendliesMap.clear();
        inProductionList.clear();
        enemyAttacksAir.clear();
        enemyAllUnits.clear();
        enemyDetector.clear();
        enemyIsGround.clear();
        enemyIsAir.clear();
        enemyMappingList.clear();

        long start1 = System.currentTimeMillis();

        for (UnitInPool unitInPool: Bot.OBS.getUnits()) {
            Unit unit = unitInPool.unit();
            Alliance alliance = unit.getAlliance();
            if (unit.getType() instanceof Units.Other) {
                continue;
            }
            if (unit.getBuildProgress() < 1.0f && unit.getType() != Units.TERRAN_REFINERY) { //ignore structures in production except refineries
                if (alliance == Alliance.SELF) {
                    inProductionList.add(unit);
                }
                continue;
            }
            Units unitType = (Units)unit.getType();

            switch (alliance) {
                case SELF:
                    if (!allFriendliesMap.containsKey(unitType)) {
                        allFriendliesMap.put(unitType, new ArrayList<>());
                    }
                    allFriendliesMap.get(unitType).add(unitInPool);
                    for (UnitOrder order: unit.getOrders()) {
                        if (order.getTargetedWorldSpacePosition().isPresent()) {
                            Units structureType = Bot.abilityToUnitType.getOrDefault(order.getAbility(), Units.INVALID);
                            if (structureType != Units.INVALID && !UnitUtils.isUnitTypesNearby(structureType, order.getTargetedWorldSpacePosition().get().toPoint2d(), 1)) {
                                productionMap.put(order.getAbility(), productionMap.getOrDefault(order.getAbility(), 0) + 1);
                            }
                        }
                        if (order.getAbility() == Abilities.BUILD_REFINERY) {
                            claimedGases.add(order.getTargetedUnitTag().get());
                        }
                    }

                    switch (unitType) {
                        case TERRAN_COMMAND_CENTER: case TERRAN_PLANETARY_FORTRESS: case TERRAN_ORBITAL_COMMAND:
                        case TERRAN_COMMAND_CENTER_FLYING: case TERRAN_ORBITAL_COMMAND_FLYING:
                            ccList.add(unit);
                            break;
                        case TERRAN_REFINERY:
                            refineryList.add(unitInPool);
                            break;
                        case TERRAN_BARRACKS: case TERRAN_BARRACKS_FLYING:
                            barracksList.add(unitInPool);
                            break;
                        case TERRAN_FACTORY: case TERRAN_FACTORY_FLYING:
                            factoryList.add(unitInPool);
                            break;
                        case TERRAN_STARPORT: case TERRAN_STARPORT_FLYING:
                            starportList.add(unitInPool);
                            break;

                        case TERRAN_SCV:
                            if (WorkerManager.isMiningMinerals(unitInPool)) {
                                scvMineralList.add(unitInPool);
                            }
                            else if (unit.getOrders().isEmpty()) {
                                scvIdleList.add(unitInPool);
                            }
                            break;
                        case TERRAN_SIEGE_TANK: case TERRAN_SIEGE_TANK_SIEGED:
                            siegeTankList.add(unitInPool);
                            break;
                        case TERRAN_BANSHEE:
                            bansheeList.add(unit);
                            break;
                        case TERRAN_VIKING_FIGHTER: case TERRAN_VIKING_ASSAULT:
                            vikingList.add(unit);
                            break;
                        default:
                            otherFriendliesList.add(unitInPool);
                    }

                    break;

                case NEUTRAL:
                    switch (unitType) {
                        case NEUTRAL_MINERAL_FIELD: case NEUTRAL_MINERAL_FIELD750: case NEUTRAL_RICH_MINERAL_FIELD: case NEUTRAL_RICH_MINERAL_FIELD750: case NEUTRAL_LAB_MINERAL_FIELD:case NEUTRAL_LAB_MINERAL_FIELD750: case NEUTRAL_PURIFIER_MINERAL_FIELD: case NEUTRAL_PURIFIER_MINERAL_FIELD750: case NEUTRAL_BATTLE_STATION_MINERAL_FIELD: case NEUTRAL_BATTLE_STATION_MINERAL_FIELD750: case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD: case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750:
                            mineralNodeList.add(unitInPool);
                            break;
                        case NEUTRAL_VESPENE_GEYSER: case NEUTRAL_RICH_VESPENE_GEYSER: case NEUTRAL_PROTOSS_VESPENE_GEYSER:  case NEUTRAL_PURIFIER_VESPENE_GEYSER:  case NEUTRAL_SHAKURAS_VESPENE_GEYSER: case NEUTRAL_SPACE_PLATFORM_GEYSER:
                            geyserList.add(unitInPool);
                            break;
                    }
                    break;
                case ENEMY:
                    enemyAllUnits.add(unit);
                    enemyMappingList.add(new EnemyUnit(unit));
                    if (!unit.getFlying().orElse(false)) {
                        enemyIsGround.add(unit);
                    }
                    else {
                        //air units
                        enemyIsAir.add(unit);
                    }
                    if (unit.getDetectRange().orElse(0f) > 0f) {
                        enemyDetector.add(unit);
                    }
                    Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.getType()).getWeapons();
                    for (Weapon weapon : weapons) {
                        if (weapon.getTargetType() == Weapon.TargetType.AIR || weapon.getTargetType() == Weapon.TargetType.ANY) {
                            enemyAttacksAir.add(unit);
                            break;
                        }
                    }

            } //end alliance switch statement
        } //end unit loop
        long resultTime = System.currentTimeMillis() - start1;
        if (resultTime > 12)
            System.out.println("GameState.getUnits loop time: " + resultTime);

        //build base list
        start1 = System.currentTimeMillis();
        for (Point p : LocationConstants.myExpansionLocations) {
            //set cc
            for (Unit cc : ccList) {
                if (p.toPoint2d().distance(cc.getPosition().toPoint2d()) < 1) { //if cc in this base location
                    Base base = new Base(p.toPoint2d());
                    //set cc
                    base.setCc(cc);

                    //set mineral nodes
                    for (UnitInPool mineralNode : mineralNodeList) {
                        if (cc.getPosition().toPoint2d().distance(mineralNode.unit().getPosition().toPoint2d()) < 10) {
                            base.getMineralPatches().add(mineralNode);
                        }
                    }
                    if ((cc.getAssignedHarvesters().get() < cc.getIdealHarvesters().get()) && !base.getMineralPatches().isEmpty()) {
                        base.setRallyNode(base.getMineralPatches().get(0));
                    }

                    //set geyser nodes
                    for (UnitInPool geyser : geyserList) {
                        if (cc.getPosition().toPoint2d().distance(geyser.unit().getPosition().toPoint2d()) < 10) {
                            Gas gas = new Gas(geyser);
                            for (UnitInPool refinery : refineryList) {
                                if (geyser.unit().getPosition().toPoint2d().distance(refinery.unit().getPosition().toPoint2d()) < 1) {
                                    gas.setRefinery(refinery);
                                }
                            }
                            base.getGases().add(gas);
                        }
                    }

//                    //set missile turrets  UNNEEDED IF I ONLY BUILD THE TURRETS ONCE
//                    for (UnitInPool turret : allFriendliesMap.getOrDefault(Units.TERRAN_MISSILE_TURRET, Collections.emptyList())) {
//                        if (cc.unit().getPosition().toPoint2d().distance(turret.unit().getPosition().toPoint2d()) < 5) {
//                            base.getTurrets().add(turret);
//                        }
//                    }
                    baseList.add(base);
                    break; //next expansion location
                }
            } //end loop through cc list
        } //end loop through expansionLocations
        resultTime = System.currentTimeMillis() - start1;
        if (resultTime > 12)
            System.out.println("GameState baseList loop: " + resultTime);

        //loop through effects
        start1 = System.currentTimeMillis();
        for (EffectLocations effect : Bot.OBS.getEffects()) {
            switch ((Effects) effect.getEffect()) {
                case SCANNER_SWEEP:
                case RAVAGER_CORROSIVE_BILE_CP:
                    enemyMappingList.add(new EnemyUnit(effect));
                    break;
            }
        }
        resultTime = System.currentTimeMillis() - start1;
        if (resultTime > 12)
            System.out.println("GameState effects loop time: " + resultTime);

        //set detected and air attack cells on the map
        start1 = System.currentTimeMillis();
        mapTheMap();

        //update lists of vikings and banshees that are to dive on detectors
        updateDiverStatus();

        resultTime = System.currentTimeMillis() - start1;
        if (resultTime > 12)
            System.out.println("mapTheMap() time: " + resultTime);
    } //end onStep()

    private static void updateDiverStatus() { //TODO: make method callable with unit type rather than hardcoded viking and banshee
        //banshees
        //find dive target
        if (Switches.bansheeDiveTarget == null) {
            for (Unit detector : GameState.enemyDetector) {
                if (!detector.getFlying().orElse(true)) {
                    if (ArmyManager.shouldDive(Units.TERRAN_BANSHEE, detector)) {
                        Switches.bansheeDiveTarget = Bot.OBS.getUnit(detector.getTag());
                        break;
                    }
                }
            }
        }
        if (Switches.bansheeDiveTarget != null) {
            //cancel if target is gone
            if (!Switches.bansheeDiveTarget.isAlive() || Switches.bansheeDiveTarget.getLastSeenGameLoop() != Bot.OBS.getGameLoop()) {
                Switches.bansheeDiveTarget = null;
            }
            else {
                //build diverList
                for (int i = 0; i < bansheeList.size(); i++) {
                    if (UnitUtils.getDistance(bansheeList.get(i), Switches.bansheeDiveTarget.unit()) < 15) {
                        bansheeDivers.add(bansheeList.remove(i--));
                    }
                }
                //cancel if no banshees left nearby
                if (bansheeDivers.isEmpty()) {
                    Switches.bansheeDiveTarget = null;
                }
            }
        }

        //vikings
        if (Switches.vikingDiveTarget == null) {
            for (Unit detector : GameState.enemyDetector) {
                if (detector.getFlying().orElse(false)) {
                    if (ArmyManager.shouldDive(Units.TERRAN_VIKING_FIGHTER, detector)) {
                        Switches.vikingDiveTarget = Bot.OBS.getUnit(detector.getTag());
                        break;
                    }
                }
            }
        }
        if (Switches.vikingDiveTarget != null) {
            //cancel if target is gone
            if (!Switches.vikingDiveTarget.isAlive() || Switches.vikingDiveTarget.getLastSeenGameLoop() != Bot.OBS.getGameLoop()) {
                Switches.vikingDiveTarget = null;
            }
            else {
                //build diverList
                for (int i = 0; i < vikingList.size(); i++) {
                    if (UnitUtils.getDistance(vikingList.get(i), Switches.vikingDiveTarget.unit()) < 1) {
                        vikingDivers.add(vikingList.remove(i--));
                    }
                }
                //cancel if no banshees left nearby
                if (vikingDivers.isEmpty()) {
                    Switches.vikingDiveTarget = null;
                }
            }
        }
    }

    public static void mapTheMap() {
        long start = System.currentTimeMillis();
        int xMin = (int)SCREEN_BOTTOM_LEFT.getX();
        int xMax = (int)SCREEN_TOP_RIGHT.getX();
        int yMin = (int)SCREEN_BOTTOM_LEFT.getY();
        int yMax = (int)SCREEN_TOP_RIGHT.getY();

        pointDetected = new boolean[400][400];
//        pointUnsafeFromGround = new boolean[400][400];
//        pointUnsafeFromAir = new boolean[400][400];
        pointInBansheeRange = new boolean[400][400];
        pointInVikingRange = new boolean[400][400];
        threatToAir = new byte[400][400];

        for (EnemyUnit enemy : enemyMappingList) {
            //only look at box of max range around the enemy
            int xStart = Math.max((int)(enemy.x - enemy.maxRange), xMin);
            int yStart = Math.max((int)(enemy.y - enemy.maxRange), yMin);
            int xEnd = Math.min((int)(enemy.x + enemy.maxRange), xMax);
            int yEnd = Math.min((int)(enemy.y + enemy.maxRange), yMax);

            //loop through box
            for (int x = xStart; x <= xEnd; x++) {
                for (int y = yStart; y <= yEnd; y++) {
                    float distance = distance(x, y, enemy.x, enemy.y);
                    if (enemy.isDetector && distance < enemy.detectRange) {
                        pointDetected[x][y] = true;
                        if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.3f,y-0.3f, z), Point.of(x+0.3f,y+0.3f, z), Color.BLUE);
                    }
                    if (enemy.isAir) {
                        if (distance < Strategy.VIKING_RANGE) {
                            pointInVikingRange[x][y] = true;
                        }
                        if (distance < enemy.airAttackRange) {
//                            pointUnsafeFromAir[x][y] = true;
                            threatToAir[x][y] += enemy.threatLevel;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.2f,y-0.2f, z), Point.of(x+0.2f,y+0.2f, z), Color.PURPLE);
                        }
                    }
                    else { //ground unit or effect
                        if (distance < Strategy.BANSHEE_RANGE && !enemy.isEffect) {
                            pointInBansheeRange[x][y] = true;
                        }
                        if (distance < enemy.airAttackRange) {
//                            pointUnsafeFromGround[x][y] = true;
                            threatToAir[x][y] += enemy.threatLevel;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.1f,y-0.1f, z), Point.of(x+0.2f,y+0.2f, z), Color.RED);
                        }
                    }
                }
            }
        }

        //debug threat text
        if (Bot.isDebugOn) {
            for (int x = xMin; x <= xMax; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    if (threatToAir[x][y] != 0) {
                        Bot.DEBUG.debugTextOut(String.valueOf(threatToAir[x][y]), Point.of(x, y, z), Color.RED, 12);
                    }
                }
            }
        }


        if (System.currentTimeMillis() - start > 20) {
            System.out.println("mapTheMap() took: " + (System.currentTimeMillis() - start) + "ms");
        }
    } //end mapTheMap()

    public static boolean inDetectionRange(int x1, int y1, Unit enemy) {
        return inRange(x1, y1, enemy.getPosition().getX(), enemy.getPosition().getY(), enemy.getDetectRange().orElse(0f));
    }

    public static boolean inAirAttackRange(int x1, int y1, Unit enemy) { //TODO: figure out logic for if enemy has a range upgrade (I believe info is not provided by api)
        return inRange(x1, y1, enemy.getPosition().getX(), enemy.getPosition().getY(), UnitUtils.getAirAttackRange(enemy));
    }

    public static boolean inRange(int x1, int y1, float x2, float y2, float range) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        return Math.sqrt(width*width + height*height) < range;
    }

    public static float distance(int x1, int y1, float x2, float y2) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        return (float)Math.sqrt(width*width + height*height);
    }
}
