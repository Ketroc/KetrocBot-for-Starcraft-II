package com.ketroc;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.*;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.*;

public class GameCache {

    public static int mineralBank;
    public static int gasBank;
    public static int freeSupply;

    public static final List<Upgrades> upgradesCompleted = new ArrayList<>(); //completed upgrades

    public static final List<Unit> ccList = new ArrayList<>();
    public static final List<UnitInPool> barracksList = new ArrayList<>();
    public static final List<UnitInPool> factoryList = new ArrayList<>();
    public static final List<UnitInPool> starportList = new ArrayList<>();
    public static final List<Unit> refineryList = new ArrayList<>();
    public static final List<Unit> siegeTankList = new ArrayList<>();
    public static final List<Unit> liberatorList = new ArrayList<>();
    public static final List<Unit> bansheeList = new ArrayList<>();
    public static final List<Unit> ravenList = new ArrayList<>();
    public static final List<Unit> vikingList = new ArrayList<>();
    public static final List<Unit> mineralNodeList = new ArrayList<>();
    public static final List<Unit> geyserList = new ArrayList<>();
    public static final List<Base> baseList = new ArrayList<>();
    public static final List<Unit> inProductionList = new ArrayList<>();
    public static final Map<Units, Integer> inProductionMap = new HashMap<>();

    public static final List<Unit> bansheeDivers = new ArrayList<>();
    public static final List<Unit> vikingDivers = new ArrayList<>();

    public static final List<UnitInPool> allVisibleEnemiesList = new ArrayList<>();
    public static final Map<Units, List<Unit>> allVisibleEnemiesMap = new HashMap<>();
    public static final List<UnitInPool> allEnemiesList = new ArrayList<>(); //tracks units in fog of war too
    public static final Map<Units, List<UnitInPool>> allEnemiesMap = new HashMap<>();
    public static final List<Unit> enemyAttacksAir = new ArrayList<>();
    public static final List<Unit> enemyIsGround = new ArrayList<>();
    public static final List<Unit> enemyIsAir = new ArrayList<>();
    public static final List<Unit> enemyDetector = new ArrayList<>();
    public static final List<EnemyUnit> enemyMappingList = new ArrayList<>();

    public static final Map<Units, List<Unit>> allFriendliesMap = new HashMap<>();
    public static Unit defaultRallyNode;
    public static List<Unit> wallStructures = new ArrayList<>();
    public static List<Unit> burningStructures = new ArrayList<>();


    public static void onStep() throws Exception {
        mineralBank = Bot.OBS.getMinerals();
        gasBank = Bot.OBS.getVespene();
        freeSupply = Bot.OBS.getFoodCap() - Bot.OBS.getFoodUsed();

        defaultRallyNode = null;
        refineryList.clear();
        geyserList.clear();
        mineralNodeList.clear();
        ccList.clear();
        vikingList.clear();
        siegeTankList.clear();
        liberatorList.clear();
        bansheeList.clear();
        ravenList.clear();
        bansheeDivers.clear();
        vikingDivers.clear();
        starportList.clear();
        factoryList.clear();
        barracksList.clear();
        allFriendliesMap.clear();
        allVisibleEnemiesList.clear();
        allVisibleEnemiesMap.clear();
        inProductionList.clear();
        inProductionMap.clear();
        allEnemiesMap.clear();
        enemyAttacksAir.clear();
        enemyDetector.clear();
        enemyIsGround.clear();
        enemyIsAir.clear();
        enemyMappingList.clear();
        wallStructures.clear();
        burningStructures.clear();

        allEnemiesList.removeIf(enemy -> !UnitUtils.isInFogOfWar(enemy) ||
                enemy.unit().getDisplayType() == DisplayType.SNAPSHOT ||
                !enemy.isAlive() ||
                enemy.getLastSeenGameLoop() + Time.toFrames(90) < Time.nowFrames()); //90s memory to clean up uncleared units

        for (UnitInPool unitInPool: Bot.OBS.getUnits()) {
            Unit unit = unitInPool.unit();

            //ignore specified units
            if (Ignored.contains((unit.getTag()))) {
                continue;
            }

            if (unit.getType() instanceof Units.Other) {
                float x = unit.getPosition().getX();
                float y = unit.getPosition().getY();
                if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.22f,y-0.22f, Position.getZ(x, y)), Point.of(x+0.22f,y+0.22f, Position.getZ(x, y)), Color.GREEN);
                continue;
            }
            Units unitType = (Units)unit.getType();
            Alliance alliance = unit.getAlliance();

            //treat parasitic'ed unit as an enemy to dodge
            if (unit.getBuffs().contains(Buffs.PARASITIC_BOMB)) {
                enemyMappingList.add(new EnemyUnit(unit, true));
            }
            switch (alliance) {
                case SELF:
                    //skip most structures in production
                    if (unit.getBuildProgress() < 1.0f) {
                        inProductionList.add(unit);
                        inProductionMap.put((Units)unit.getType(), inProductionMap.getOrDefault((Units)unit.getType(), 0) + 1);
                        switch (unitType) {
                            case TERRAN_REFINERY: case TERRAN_REFINERY_RICH: case TERRAN_COMMAND_CENTER:
                                break;
                            default: //ignore in-production units except the ones above
                                continue;
                        }
                    }

                    //see what repair is required for wall or burning structures
                    if (UnitUtils.isStructure(unitType)) {
                        if (UnitUtils.isWallStructure(unit)) {
                            wallStructures.add(unit);
                        }
                        else if (unit.getBuildProgress() == 1.0f && UnitUtils.getHealthPercentage(unit) <= 35) {
                            burningStructures.add(unit);
                        }
                    }

                    //map of every friendly unit
                    if (!allFriendliesMap.containsKey(unitType)) {
                        allFriendliesMap.put(unitType, new ArrayList<>());
                    }
                    allFriendliesMap.get(unitType).add(unit);

                    //build unitType specific lists
                    Abilities curOrder = UnitUtils.getOrder(unit);
                    switch (unitType) {
                        case TERRAN_COMMAND_CENTER: case TERRAN_PLANETARY_FORTRESS: case TERRAN_ORBITAL_COMMAND:
                            if (curOrder != null) {
                                Units unitProducing = Bot.abilityToUnitType.get(curOrder);
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_COMMAND_CENTER_FLYING: case TERRAN_ORBITAL_COMMAND_FLYING:
                            ccList.add(unit);
                            break;
                        case TERRAN_REFINERY: case TERRAN_REFINERY_RICH:
                            refineryList.add(unit);
                            break;
                        case TERRAN_SUPPLY_DEPOT:
                            if (UnitUtils.isWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            break;
                        case TERRAN_ENGINEERING_BAY:
                            if (UnitUtils.isWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            break;
                        case TERRAN_BARRACKS:
                            if (UnitUtils.isWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            if (curOrder != null) {
                                Units unitProducing = Bot.abilityToUnitType.get(curOrder);
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_BARRACKS_FLYING:
                            barracksList.add(unitInPool);
                            break;
                        case TERRAN_FACTORY:
                            if (curOrder != null) {
                                Units unitProducing = Bot.abilityToUnitType.get(curOrder);
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_FACTORY_FLYING:
                            factoryList.add(unitInPool);
                            break;
                        case TERRAN_STARPORT:
                            if (curOrder != null) {
                                Units unitProducing = Bot.abilityToUnitType.get(curOrder);
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_STARPORT_FLYING:
                            starportList.add(unitInPool);
                            break;
                        case TERRAN_SIEGE_TANK: case TERRAN_SIEGE_TANK_SIEGED:
                            siegeTankList.add(unit);
                            break;
                        case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG:
                            liberatorList.add(unit);
                            break;
                        case TERRAN_BANSHEE:
                            bansheeList.add(unit);
                            break;
                        case TERRAN_RAVEN:
                            if (unit.getEnergy().orElse(0f) > 175) {
                                ravenList.add(0, unit);
                            }
                            else {
                                ravenList.add(unit);
                            }
                            break;
                        case TERRAN_VIKING_FIGHTER: case TERRAN_VIKING_ASSAULT:
                            vikingList.add(unit);
                            break;

                    }

                    break;

                case NEUTRAL:
                    switch (unitType) {
                        case NEUTRAL_MINERAL_FIELD: case NEUTRAL_MINERAL_FIELD750: case NEUTRAL_RICH_MINERAL_FIELD: case NEUTRAL_RICH_MINERAL_FIELD750: case NEUTRAL_LAB_MINERAL_FIELD:case NEUTRAL_LAB_MINERAL_FIELD750: case NEUTRAL_PURIFIER_MINERAL_FIELD: case NEUTRAL_PURIFIER_MINERAL_FIELD750: case NEUTRAL_BATTLE_STATION_MINERAL_FIELD: case NEUTRAL_BATTLE_STATION_MINERAL_FIELD750: case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD: case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750:
                            mineralNodeList.add(unit);
                            break;
                        case NEUTRAL_VESPENE_GEYSER: case NEUTRAL_RICH_VESPENE_GEYSER: case NEUTRAL_PROTOSS_VESPENE_GEYSER:  case NEUTRAL_PURIFIER_VESPENE_GEYSER:  case NEUTRAL_SHAKURAS_VESPENE_GEYSER: case NEUTRAL_SPACE_PLATFORM_GEYSER:
                            geyserList.add(unit);
                            break;
                    }
                    break;

                case ENEMY:
                    //update enemy race vs random player
                    if (LocationConstants.opponentRace == Race.RANDOM) {
                        LocationConstants.opponentRace = Bot.OBS.getUnitTypeData(false).get(unitType).getRace().get();
                        LocationConstants.setEnemyTypes();
                        Strategy.setRaceStrategies();
                    }

                    //ignore hallucinations
                    if (unit.getHallucination().orElse(false)) {
                        continue;
                    }

                    //ignore autoturret snapshots
                    if (unit.getDisplayType() == DisplayType.SNAPSHOT && unit.getType() == Units.TERRAN_AUTO_TURRET) {
                        continue;
                    }

                    //check if enemy can create air units
                    if (!Switches.enemyCanProduceAir &&
                            UnitUtils.EVIDENCE_OF_AIR.contains(unitType)) {
                        Bot.ACTION.sendChat("Wake up our viking pilots. Enemy is getting flyers.", ActionChat.Channel.BROADCAST);
                        Switches.enemyCanProduceAir = true;
                        Strategy.DO_INCLUDE_LIBS = false;
                        if (LocationConstants.opponentRace != Race.TERRAN) {
                            Strategy.DO_DEFENSIVE_TANKS = false;
                            Strategy.DO_OFFENSIVE_TANKS = false;
                        }
                    }

                    //set enemy cloaked variable
                    switch (unitType) {
                        case PROTOSS_DARK_TEMPLAR: case PROTOSS_DARK_SHRINE: case PROTOSS_MOTHERSHIP:
                        case ZERG_LURKER_DEN_MP: case ZERG_LURKER_MP: case ZERG_LURKER_MP_EGG: case ZERG_LURKER_MP_BURROWED:
                        case TERRAN_BANSHEE: case TERRAN_WIDOWMINE: case TERRAN_WIDOWMINE_BURROWED: case TERRAN_GHOST: case TERRAN_GHOST_ACADEMY:
                        case ZERG_INFESTOR_BURROWED: case ZERG_BANELING_BURROWED: case ZERG_DRONE_BURROWED: case ZERG_HYDRALISK_BURROWED:
                        case ZERG_QUEEN_BURROWED: case ZERG_SWARM_HOST_BURROWED_MP: case ZERG_ZERGLING_BURROWED: case ZERG_INFESTOR_TERRAN_BURROWED:
                        case ZERG_RAVAGER_BURROWED: case ZERG_ROACH_BURROWED: case ZERG_ULTRALISK_BURROWED:
                            if (!Switches.enemyHasCloakThreat) {
                                Bot.ACTION.sendChat("Sneaky boy. Looks like detection is needed.", ActionChat.Channel.BROADCAST);
                            }
                            Switches.enemyHasCloakThreat = true;
                            break;
                    }

                    switch (unitType) {
                        //change base viking:banshee ratio once tempests hit the field
                        case PROTOSS_TEMPEST:
                            Strategy.VIKING_BANSHEE_RATIO = 1f;
                            Chat.tag("VS_TEMPESTS");
                            break;
                        //ignore phoenixes until one is verify as real
                        case PROTOSS_PHOENIX:
                            if (!Switches.phoenixAreReal) {
                                continue;
                            }
                            break;
                    }

                    //map of every enemy unit
                    allVisibleEnemiesList.add(unitInPool);
                    allEnemiesList.add(unitInPool);
                    if (!allVisibleEnemiesMap.containsKey(unitType)) {
                        allVisibleEnemiesMap.put(unitType, new ArrayList<>());
                    }
                    allVisibleEnemiesMap.get(unitType).add(unit);

                    if (!unit.getFlying().orElse(false)) {
                        enemyIsGround.add(unit);
                    }
                    else {
                        //air units
                        enemyIsAir.add(unit);
                    }
                    if (unit.getDetectRange().orElse(0f) > 0f || unit.getType() == Units.PROTOSS_OBSERVER) {
                        enemyDetector.add(unit);
                    }
                    Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.getType()).getWeapons();
                    for (Weapon weapon : weapons) {
                        if (weapon.getTargetType() == Weapon.TargetType.AIR || weapon.getTargetType() == Weapon.TargetType.ANY) {
                            enemyAttacksAir.add(unit);
                            break;
                        }
                    }

            }
        }

        //build enemies Units map
        for (UnitInPool unitInPool : allEnemiesList) {
            Units unitType = (Units) unitInPool.unit().getType();
            if (!allEnemiesMap.containsKey(unitType)) {
                allEnemiesMap.put(unitType, new ArrayList<>());
            }
            allEnemiesMap.get(unitType).add(unitInPool);
        }

        //************************
        // *** BUILD BASE LIST ***
        //************************
        List<UnitInPool> enemyCCs = Bot.OBS.getUnits(Alliance.ENEMY, enemyCC -> UnitUtils.enemyCommandStructures.contains(enemyCC.unit().getType())); //TODO: refactor when allEnemiesList doesn't duplicate snapshots
        for (Base base : baseList) { //TODO: handle FlyingCCs
            //ignore bases that aren't mine AND aren't visible
            if (!base.isMyBase() &&
                    Bot.OBS.getVisibility(base.getCcPos()) != Visibility.VISIBLE &&
                    base.getMineralPatchUnits().stream().noneMatch(patch -> patch.getDisplayType() == DisplayType.VISIBLE) &&
                    (base.lastScoutedFrame != 0 || !base.getMineralPatchUnits().isEmpty())) {
                continue;
            }
            base.lastScoutedFrame = Time.nowFrames();
            base.scvsAddedThisFrame = 0;

            //update cc
            base.setCc(base.getUpdatedUnit(Units.TERRAN_PLANETARY_FORTRESS, base.getCc(), base.getCcPos()));

            //set who owns the base
            base.isEnemyBase = enemyCCs.stream().anyMatch(enemyCC -> UnitUtils.getDistance(enemyCC.unit(), base.getCcPos()) < 2);

            //update mineral nodes
//            base.getMineralPatchUnits().clear();
//            base.getMineralPatchUnits().addAll(mineralNodeList.stream()
//                    .filter(node -> UnitUtils.getDistance(node, base.getCcPos()) < 10)
//                    .collect(Collectors.toList()));
            base.getMineralPatches().forEach(mineralPatch -> mineralPatch.updateUnit());
            base.getMineralPatches().removeIf(mineralPatch -> mineralPatch.getNode() == null);


//            base.getMineralPatches().addAll(mineralNodeList.stream()
//                    .filter(node -> UnitUtils.getDistance(node, base.getCcPos()) < 10)
//                    .map(node -> new MineralPatch(node, base.getCcPos()))
//                    .collect(Collectors.toList()));

            //check if base is dry
            if (!base.isDryedUp() && base.getMineralPatchUnits().isEmpty()) { //TODO: check gas (need workaround for geyser snapshot always having zero gas)
                base.setDryedUp(true);
            }

            //nothing else required if enemy base or untaken base
            if (base.isEnemyBase || base.getCc() == null) {
                continue;
            }

            //set this base's rally node
            if (!base.getMineralPatchUnits().isEmpty()) {
                base.setRallyNode(base.getMineralPatchUnits().stream()
                        .max(Comparator.comparing(node -> node.getMineralContents().orElse(0)))
                        .orElse(null));
            }

            //set default rally node for any base
            //TODO: remove - for debugging
            if (base.getCc() == null) {
                Print.print("error on GameCache::387");
            }
            Unit cc = base.getCc().unit();
            if (cc.getAssignedHarvesters().isEmpty()) {
                Print.print("error on GameCache::391");
                Print.print("base index: " + baseList.indexOf(base));
                Print.print("base.getCcPos() = " + base.getCcPos());
                Print.print("cc.getType() = " + cc.getType());
                Print.print("cc.getBuildProgress() = " + cc.getBuildProgress());
                Print.print("FlyingCC.flyingCCs.size() = " + FlyingCC.flyingCCs.size());
                Print.print("base.isEnemyBase = " + base.isEnemyBase);
                Print.print("base.getCc() == null?: " + (base.getCc() == null));

            }
            if (cc.getIdealHarvesters().isEmpty()) {
                Print.print("error on GameCache::414");
            }
            if (!base.getMineralPatchUnits().isEmpty()) {
                if (defaultRallyNode == null) {
                    defaultRallyNode = base.getRallyNode();
                }
            }

            //set geyser nodes and refineries
//            base.getGases().clear();
//            geyserList.stream()
//                    .filter(geyser -> UnitUtils.getDistance(geyser, base.getCcPos()) < 10)
//                    .forEach(geyser -> {
//                        Gas gas = new Gas(geyser, base.getCcPos());
//                        refineryList.stream()
//                                .filter(refinery -> UnitUtils.getDistance(geyser, refinery) < 1)
//                                .findFirst()
//                                .ifPresent(refinery -> gas.setRefinery(refinery));
//                        base.getGases().add(gas);
//                    });
            base.getGases().forEach(gas -> gas.updateUnit());


            //update turret
            for (DefenseUnitPositions turret : base.getTurrets()) {
                turret.setUnit(base.getUpdatedUnit(Units.TERRAN_MISSILE_TURRET, turret.getUnit(), turret.getPos()));
            }

        }

        //if all my bases have no minerals left, distance mine to an untaken base
        if (defaultRallyNode == null) {
            baseList.stream()
                    .filter(base -> base.isUntakenBase() && !base.getMineralPatchUnits().isEmpty())
                    .findFirst()
                    .ifPresent(base -> defaultRallyNode = base.getMineralPatchUnits().get(0));
        }

        //if no minerals to distance mine either, then mine nearest mineral wall
        if (defaultRallyNode == null) {
            defaultRallyNode = UnitUtils.getClosestUnitOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_WALL_TYPE, LocationConstants.baseLocations.get(0));
        }


        //loop through effects
        for (EffectLocations effect : Bot.OBS.getEffects()) {
            if (effect.getAlliance().orElse(Alliance.SELF) == Alliance.ENEMY) {
                switch ((Effects) effect.getEffect()) {
                    case SCANNER_SWEEP:
                        if (!EnemyScan.contains(effect)) {
                            EnemyScan.add(effect);
                        }
                        break;
                    case RAVAGER_CORROSIVE_BILE_CP:
                    case PSI_STORM_PERSISTENT:
                    case LIBERATOR_TARGET_MORPH_DELAY_PERSISTENT:
                    case LIBERATOR_TARGET_MORPH_PERSISTENT:
//                    case NUKE_PERSISTENT:
                        enemyMappingList.add(new EnemyUnit(effect));
                        break;
                }
            }
        }

        //add scans to enemyMappingList
        EnemyScan.enemyScanSet.stream().forEach(enemyScan -> enemyMappingList.add(new EnemyUnit(enemyScan.scanEffect)));

        //add all enemies to the enemyMappingList (include enemies that entered fog within last 5sec)
        //TODO: include snapshot units like cannons and high ground marines
        //TODO: remember unit positions for units that can't move like siege tanks / burrowed_lurkers
        //      but forget the unit when its position becomes visible, or the unit is seen again elsewhere (this may be auto-done)
        allEnemiesList.stream()
                //filter to all visible enemies and non-visible tempests that have entered the fog within the last 5sec
                .filter(enemy -> enemy.getLastSeenGameLoop() +
                        ((UnitUtils.LONG_RANGE_ENEMIES.contains(enemy.unit().getType())) ? Strategy.MAP_ENEMIES_IN_FOG_DURATION : 24) >= Time.nowFrames())
                .forEach(enemy -> enemyMappingList.add(new EnemyUnit(enemy.unit())));

        //add siege tanks and lurkers that are no longer visible
        EnemyUnitMemory.onStep();

        //check for fungal mapping
        Infestor.onStep();

        //set detected and air attack cells on the map
        buildInfluenceMap();

        //update lists of vikings and banshees that are to dive on detectors
        updateDiverStatus();

    } //end onStep()

    private static void updateDiverStatus() throws Exception { //TODO: make method callable with unit type rather than hardcoded viking and banshee
        // === banshees ===
        //find dive target
        if (Switches.bansheeDiveTarget == null) {
            for (Unit detector : GameCache.enemyDetector) {
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
            if (!Switches.bansheeDiveTarget.isAlive() || UnitUtils.isInFogOfWar(Switches.bansheeDiveTarget)) {
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

        // === vikings ===
        //start viking dive vs tempests
        if (Switches.vikingDiveTarget == null && LocationConstants.opponentRace == Race.PROTOSS && !GameCache.vikingList.isEmpty()) {
            List<UnitInPool> tempests = getProtossCapitalShips();
            if (!tempests.isEmpty()) {
                UnitInPool closestTempest = UnitUtils.getClosestUnitFromUnitList(tempests, Position.midPointUnitsMedian(GameCache.vikingList));
                if (closestTempest != null) {
                    Point2d closestTempestPos = closestTempest.unit().getPosition().toPoint2d();
                    List<UnitInPool> nearbyAntiTempestUnits = UnitUtils.getUnitsNearbyOfType(
                            Alliance.SELF,
                            Set.of(Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_CYCLONE),
                            closestTempestPos,
                            Strategy.TEMPEST_DIVE_RANGE
                    );
                    if (ArmyManager.shouldDiveTempests(closestTempestPos, nearbyAntiTempestUnits.size())) {
                        Switches.vikingDiveTarget = closestTempest;
                        if (Switches.vikingDiveTarget != null) {
                            Switches.isDivingTempests = true;
                            Bot.ACTION.sendChat(Chat.getRandomMessage(Chat.VIKING_DIVE), ActionChat.Channel.BROADCAST);
                        }
                    }
                }
            }
        }
        //start viking dive vs detector
        if (Switches.vikingDiveTarget == null) {
            //don't dive detector if vs terran, no banshees, or if there are still voids or phoenix visible
            if (Strategy.DO_DIVE_RAVENS && //TODO: temp for vs ANIBot
                    (Strategy.gamePlan == GamePlan.BANSHEE || Strategy.gamePlan == GamePlan.BANSHEE_CYCLONE || Strategy.gamePlan == GamePlan.BANSHEE_TANK) &&
                    !GameCache.bansheeList.isEmpty() &&
                    (UnitUtils.getVisibleEnemyUnitsOfType(Units.PROTOSS_PHOENIX).size() + UnitUtils.getVisibleEnemyUnitsOfType(Units.PROTOSS_VOIDRAY).size() == 0)) {
                for (Unit detector : GameCache.enemyDetector) {
                    if (detector.getFlying().orElse(false)) {
                        if (ArmyManager.shouldDive(Units.TERRAN_VIKING_FIGHTER, detector)) {
                            Switches.vikingDiveTarget = Bot.OBS.getUnit(detector.getTag());
                            //scan if needed to target (cloaked observer)
                            if (Switches.vikingDiveTarget.unit().getType() == Units.PROTOSS_OBSERVER &&
                                    Switches.vikingDiveTarget.unit().getCloakState().orElse(CloakState.CLOAKED_DETECTED) == CloakState.CLOAKED) {
                                if (UnitUtils.canScan()) {
                                    List<Unit> orbitals = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
                                    ActionHelper.unitCommand(orbitals, Abilities.EFFECT_SCAN, Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), ArmyManager.retreatPos, -2), false);
                                }
                                //cancel dive if I can't scan
                                else {
                                    Switches.vikingDiveTarget = null;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (Switches.vikingDiveTarget != null) {
            //if target is gone while diving detector
            if (!Switches.isDivingTempests &&
                    (!Switches.vikingDiveTarget.isAlive() || UnitUtils.isInFogOfWar(Switches.vikingDiveTarget))) {
                //end dive logic
                Switches.vikingDiveTarget = null;
            }
            else {
                //if tempest target is dead or if vikings lost the target in the fog, get another
                if (Switches.isDivingTempests) {
                    if (!Switches.vikingDiveTarget.isAlive() ||
                            (UnitUtils.isInFogOfWar(Switches.vikingDiveTarget) &&
                                    vikingList.stream().anyMatch(viking -> UnitUtils.getDistance(viking, Switches.vikingDiveTarget.unit()) < 1))) {
                        Switches.vikingDiveTarget = UnitUtils.getClosestUnitFromUnitList(getProtossCapitalShips(), Position.midPointUnitsMedian(vikingList));
                    }

                }

                //build diverList
                if (Switches.vikingDiveTarget != null) {
                    for (int i = 0; i < vikingList.size(); i++) {
                        if (UnitUtils.getDistance(vikingList.get(i), Switches.vikingDiveTarget.unit()) < ((Switches.isDivingTempests) ? Strategy.TEMPEST_DIVE_RANGE : Strategy.DIVE_RANGE)) {
                            vikingDivers.add(vikingList.remove(i--));
                        }
                    }
                }
                //cancel if no vikings left nearby or no tempest left to target
                if (vikingDivers.isEmpty() || Switches.vikingDiveTarget == null) {
                    Switches.vikingDiveTarget = null;
                    Switches.isDivingTempests = false;
                }
            }
        }
    }

    private static List<UnitInPool> getProtossCapitalShips() {
        List<UnitInPool> protossCapitalShips = new ArrayList<>();
        protossCapitalShips.addAll(UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST));
        protossCapitalShips.addAll(UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_CARRIER));
        protossCapitalShips.addAll(UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_MOTHERSHIP));
        return protossCapitalShips;
    }

    private static boolean tempestFarFromVikings(Unit tempest) {
        return Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_VIKING_FIGHTER && UnitUtils.getDistance(tempest, u.unit()) < 8).isEmpty();
    }

    public static void buildInfluenceMap() throws Exception {
        int xMin = InfluenceMaps.toMapCoord(LocationConstants.MIN_X);
        int xMax = InfluenceMaps.toMapCoord(LocationConstants.MAX_X);
        int yMin = InfluenceMaps.toMapCoord(LocationConstants.MIN_Y);
        int yMax = InfluenceMaps.toMapCoord(LocationConstants.MAX_Y);

        InfluenceMaps.pointDetected = new boolean[800][800];
        InfluenceMaps.pointInBansheeRange = new boolean[800][800];
        InfluenceMaps.pointInRavenCastRange = new boolean[800][800];
        InfluenceMaps.pointInMarineRange = new boolean[800][800];
        InfluenceMaps.pointInEnemyVision = new boolean[800][800];
        InfluenceMaps.enemyInVikingRange = new boolean[800][800];
        InfluenceMaps.enemyInMissileTurretRange = new boolean[800][800];
        InfluenceMaps.pointThreatToAirPlusBufferValue = new int[800][800];
        InfluenceMaps.pointThreatToAirPlusBuffer = new boolean[800][800];
        InfluenceMaps.pointSupplyInSeekerRange = new float[800][800];
        InfluenceMaps.pointThreatToAirValue = new int[800][800];
        InfluenceMaps.pointThreatToAir = new boolean[800][800];
        InfluenceMaps.pointThreatToAirFromGround = new int[800][800];
        InfluenceMaps.pointThreatToGroundValue = new int[800][800];
        InfluenceMaps.pointDamageToGroundValue = new int[800][800];
        InfluenceMaps.pointDamageToAirValue = new int[800][800];
        InfluenceMaps.pointThreatToGround = new boolean[800][800];
        InfluenceMaps.pointPersistentDamageToGround = new boolean[800][800];
        InfluenceMaps.pointPFTargetValue = new int[800][800];
        InfluenceMaps.pointGroundUnitWithin13 = new boolean[800][800];
        InfluenceMaps.pointRaiseDepots = new boolean[800][800];
        InfluenceMaps.pointVikingsStayBack = new boolean[800][800];

        for (EnemyUnit enemy : enemyMappingList) {
            //only look at box of max range around the enemy
            int xStart = Math.max(InfluenceMaps.toMapCoord(enemy.x - enemy.maxRange), xMin);
            int yStart = Math.max(InfluenceMaps.toMapCoord(enemy.y - enemy.maxRange), yMin);
            int xEnd = Math.min(InfluenceMaps.toMapCoord(enemy.x + enemy.maxRange), xMax);
            int yEnd = Math.min(InfluenceMaps.toMapCoord(enemy.y + enemy.maxRange), yMax);

            //loop through box
            for (int x = xStart; x <= xEnd; x++) {
                for (int y = yStart; y <= yEnd; y++) {
                    float distance = Position.distance(x/2f, y/2f, enemy.x, enemy.y);
                    //depot raising
                    if (!enemy.isAir &&
                            enemy.isArmy &&
                            distance < Strategy.DISTANCE_RAISE_DEPOT) {
                        InfluenceMaps.pointRaiseDepots[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x/2-0.32f,y/2-0.32f, z), Point.of(x/2+0.32f,y/2+0.32f, z), Color.WHITE);
                    }
                    //viking keeping distance vs tempests
                    if (distance < 15 + Strategy.KITING_BUFFER) {
                        InfluenceMaps.pointVikingsStayBack[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x/2-0.21f,y/2-0.21f, z), Point.of(x/2+0.21f,y/2+0.21f, z), Color.TEAL);
                    }

                    //threat level to ground
                    if (distance < enemy.groundAttackRange) {
                        InfluenceMaps.pointThreatToGroundValue[x][y] += enemy.threatLevel;
                        InfluenceMaps.pointDamageToGroundValue[x][y] += enemy.groundDamage;
                        InfluenceMaps.pointThreatToGround[x][y] = true;
                        if (enemy.isPersistentDamage) {
                            InfluenceMaps.pointPersistentDamageToGround[x][y] = true;
                        }
                    }

                    //threat to air
                    if (distance < enemy.airAttackRange) {
                        InfluenceMaps.pointThreatToAirValue[x][y] += enemy.threatLevel;
                        InfluenceMaps.pointThreatToAir[x][y] = true;
                        InfluenceMaps.pointDamageToAirValue[x][y] += enemy.airDamage;
                    }


                    //air threat range + extra buffer
                    if (enemy.airAttackRange != 0 && distance < enemy.airAttackRange + Strategy.RAVEN_DISTANCING_BUFFER) {
                        InfluenceMaps.pointThreatToAirPlusBufferValue[x][y] += enemy.threatLevel;
                        InfluenceMaps.pointThreatToAirPlusBuffer[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x/2-0.23f,y/2-0.23f, z), Point.of(x/2+0.23f,y/2+0.23f, z), Color.BLACK);
                    }

                    //seeker missile target
                    if (distance < Strategy.SEEKER_RADIUS && enemy.isArmy && !enemy.isSeekered) {
                        InfluenceMaps.pointSupplyInSeekerRange[x][y] += enemy.supply;
                    }

                    //detection
                    if (enemy.isDetector && distance < enemy.detectRange) {
                        InfluenceMaps.pointDetected[x][y] = true;
                        //DebugHelper.drawBox(x/2f, y/2f, Color.BLUE, 0.25f);
                    }
                    //sight range
                    if (distance <= enemy.visionRange &&
                            (enemy.isAir || Bot.OBS.terrainHeight(Point2d.of(enemy.x, enemy.y)) + 1 > Bot.OBS.terrainHeight(Point2d.of(x/2f, y/2f)))) {
                        InfluenceMaps.pointInEnemyVision[x][y] = true;
                        DebugHelper.drawBox(x/2f, y/2f, Color.GRAY, 0.25f);
                    }
                    //autoturret cast range
                    if (distance < Strategy.RAVEN_CAST_RANGE && !enemy.isEffect && !enemy.isTumor) {
                        InfluenceMaps.pointInRavenCastRange[x][y] = true;
                    }

                    //marine range
                    if (distance < Strategy.MARINE_RANGE && !enemy.isEffect) {
                        InfluenceMaps.pointInMarineRange[x][y] = true;
                    }

                    if (enemy.isAir) {

                        //viking range
                        if (distance < Strategy.VIKING_RANGE) {
                            InfluenceMaps.enemyInVikingRange[x][y] = true;
                        }

                        //missile turret range
                        if (distance < 8 + (Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING) ? 1 : 0)) {
                            InfluenceMaps.enemyInMissileTurretRange[x][y] = true;
                        }
                    }
                    else { //ground unit or effect

                        //banshee range
                        if (distance < Strategy.BANSHEE_RANGE && !enemy.isEffect) {
                            InfluenceMaps.pointInBansheeRange[x][y] = true;
                        }

                        //threat to air from ground
                        float airAttackRange = (enemy.unitType == Units.TERRAN_CYCLONE) ? 10 : enemy.airAttackRange;
                        if (distance < airAttackRange) {
                            InfluenceMaps.pointThreatToAirFromGround[x][y] += enemy.threatLevel;
                        }

                        //PF target value (for PF & tank targetting)
                        if (distance < 1.25) {
                            InfluenceMaps.pointPFTargetValue[x][y] += enemy.pfTargetLevel;
                        }

                        //ground unit within 13 range
                        if (distance < 13) {
                            InfluenceMaps.pointGroundUnitWithin13[x][y] = true;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x/2-0.15f,y/2-0.15f, z), Point.of(x/2+0.15f,y/2+0.15f, z), Color.GREEN);
                        }
                    }
                }
            }
        }

        //debug threat text
        if (Bot.isDebugOn) {
            for (int x = xMin+1; x <= xMax-1; x++) {
                for (int y = yMin+1; y <= yMax-1; y++) {
//                    if (InfluenceMaps.pointPFTargetValue[x][y] > 0) {
//                        DebugHelper.drawText(String.valueOf(InfluenceMaps.pointPFTargetValue[x][y]),x / 2f, y / 2f, Color.RED);
//                    }
//                    if (InfluenceMaps.pointThreatToAir[x][y] && InfluenceMaps.pointDetected[x][y]) {
//                        DebugHelper.drawBox(x / 2f, y / 2f, Color.RED, 0.25f);
//                    }
//                    else if (InfluenceMaps.pointThreatToAir[x][y]) {
//                        DebugHelper.drawBox(x / 2f, y / 2f, Color.GREEN, 0.25f);
//                    }
//                    else if (InfluenceMaps.pointDetected[x][y]) {
//                        DebugHelper.drawBox(x / 2f, y / 2f, Color.BLUE, 0.25f);
//                    }
//                    if (InfluenceMaps.pointInNat[x][y] || InfluenceMaps.pointInEnemyNat[x][y]) {
//                        DebugHelper.drawBox(x/2f, y/2f, Color.GRAY, 0.24f);
//                    }
//                    if (InfluenceMaps.pointInMainBase[x][y] || InfluenceMaps.pointInEnemyMainBase[x][y]) {
//                        DebugHelper.drawBox(x/2f, y/2f, Color.BLUE, 0.24f);
//                    }
                }
            }
//            float x = LocationConstants.mainBaseMidPos.getX();
//            float y = LocationConstants.mainBaseMidPos.getY();
//            float z = Position.getZ(x, y);
//            Bot.DEBUG.debugBoxOut(Point.of(x-0.1f,y-0.1f, z), Point.of(x+0.1f,y+0.1f, z), Color.BLUE);
//            Bot.DEBUG.debugBoxOut(Point.of(x-0.2f,y-0.2f, z), Point.of(x+0.2f,y+0.2f, z), Color.BLUE);
//
//            x = LocationConstants.enemyMainBaseMidPos.getX();
//            y = LocationConstants.enemyMainBaseMidPos.getY();
//            z = Position.getZ(x, y);
//            Bot.DEBUG.debugBoxOut(Point.of(x-0.1f,y-0.1f, z), Point.of(x+0.1f,y+0.1f, z), Color.BLUE);
//            Bot.DEBUG.debugBoxOut(Point.of(x-0.2f,y-0.2f, z), Point.of(x+0.2f,y+0.2f, z), Color.BLUE);
        }


    }

    public static boolean inDetectionRange(int x1, int y1, Unit enemy) throws Exception {
        return inRange(x1, y1, enemy.getPosition().getX(), enemy.getPosition().getY(), enemy.getDetectRange().orElse(0f));
    }

    public static boolean inAirAttackRange(int x1, int y1, Unit enemy) throws Exception { //TODO: figure out logic for if enemy has a range upgrade (I believe info is not provided by api)
        return inRange(x1, y1, enemy.getPosition().getX(), enemy.getPosition().getY(), UnitUtils.getAirAttackRange(enemy));
    }

    public static boolean inRange(int x1, int y1, float x2, float y2, float range) throws Exception {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        return Math.sqrt(width*width + height*height) < range;
    }

    /*
    public static Base createBaseObject(Unit cc, Point2d basePos) {
        Base base = new Base(basePos);
        //set cc
        base.setCc(cc);

        //set mineral nodes
        for (Unit mineralNode : mineralNodeList) {
            if (cc.getPosition().toPoint2d().distance(mineralNode.getPosition().toPoint2d()) < 10) {
                base.getMineralPatches().add(mineralNode);
            }
        }
        if (!base.getMineralPatches().isEmpty()) {
            base.setRallyNode(base.getMineralPatches().get(0));
        }
        if ((base.getNumMineralScvs() < cc.getIdealHarvesters().get()) && !base.getMineralPatches().isEmpty()) {
            if (GameState.defaultRallyNode == null) {
                GameState.defaultRallyNode = base.getRallyNode();
            }
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

//        //set missile turrets  UNNEEDED IF I ONLY BUILD THE TURRETS ONCE
//        for (UnitInPool turret : allFriendliesMap.getOrDefault(Units.TERRAN_MISSILE_TURRET, Collections.emptyList())) {
//            if (cc.unit().getPosition().toPoint2d().distance(turret.unit().getPosition().toPoint2d()) < 5) {
//                base.getTurrets().add(turret);
//            }
//        }

        //set REPAIR_BAY location if not set
        if (LocationConstants.REPAIR_BAY == null && base.getCcPos() == LocationConstants.baseLocations.get(0)) {
            ArmyManager.retreatPos = ArmyManager.attackPos = LocationConstants.REPAIR_BAY = base.getResourceMidPoint();
        }
        return base;
    }
    */
}
