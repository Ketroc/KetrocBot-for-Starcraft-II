package com.ketroc.gamestate;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.micro.MarineBasic;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.models.*;
import com.ketroc.purchases.PurchaseUnit;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.MarineAllIn;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.*;
import java.util.function.Predicate;

public class GameCache {
    public static int mineralBank;
    public static int gasBank;
    public static int freeSupply;

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
    public static final Map<Units, List<UnitInPool>> allVisibleEnemiesMap = new HashMap<>();
    public static final List<UnitInPool> allEnemiesList = new ArrayList<>(); //tracks units in fog of war too
    public static final Set<UnitInPool> allMyUnitsSet = new HashSet<>();
    public static final Map<Units, List<UnitInPool>> allEnemiesMap = new HashMap<>();
    public static final List<Unit> enemyAttacksAir = new ArrayList<>();
    public static final List<Unit> enemyIsGround = new ArrayList<>();
    public static final List<Unit> enemyIsAir = new ArrayList<>();
    public static final List<Unit> enemyDetector = new ArrayList<>();
    public static final List<EnemyMapping> enemyMappingList = new ArrayList<>();

    public static final Map<Units, List<Unit>> allMyUnitsMap = new HashMap<>(); //ignore my structures with buildProgress < 1
    public static Unit defaultRallyNode;
    public static List<Unit> wallStructures = new ArrayList<>();
    public static List<Unit> burningStructures = new ArrayList<>();

    public static void onGameStart() {
        allMyUnitsSet.addAll(Bot.OBS.getUnits(Alliance.SELF));
        onStepStart();
    }


    public static void onStepStart() {
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
        allMyUnitsMap.clear();
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
                enemy.getLastSeenGameLoop() + Time.toFrames(75) < Time.nowFrames()); //75s memory to clean up uncleared units

        for (UnitInPool unitInPool: Bot.OBS.getUnits()) {
            Unit unit = unitInPool.unit();

            //ignore specified units
            if (Ignored.contains((unit.getTag()))) {
                continue;
            }

            if (unit.getType() instanceof Units.Other) {
                DebugHelper.boxUnit(unit);
                continue;
            }
            Units unitType = (Units)unit.getType();
            Alliance alliance = unit.getAlliance();

            //treat parasitic'ed unit as an enemy to dodge
            if (unit.getBuffs().contains(Buffs.PARASITIC_BOMB)) {
                enemyMappingList.add(new EnemyMappingParasitic(unit));
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
                        if (UnitUtils.isRampWallStructure(unit) || UnitUtils.isNatWallStructure(unit)) {
                            wallStructures.add(unit);
                        }
                        else if (unit.getBuildProgress() == 1.0f && UnitUtils.getHealthPercentage(unit) <= 35) {
                            burningStructures.add(unit);
                        }
                    }

                    //map of every friendly unit
                    if (!allMyUnitsMap.containsKey(unitType)) {
                        allMyUnitsMap.put(unitType, new ArrayList<>());
                    }
                    allMyUnitsMap.get(unitType).add(unit);

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
                            if (UnitUtils.isRampWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            break;
                        case TERRAN_ENGINEERING_BAY:
                            if (UnitUtils.isRampWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            break;
                        case TERRAN_BARRACKS:
                            if (UnitUtils.isRampWallStructure(unit)) {
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
                    if (PosConstants.opponentRace == Race.RANDOM) {
                        PosConstants.opponentRace = Bot.OBS.getUnitTypeData(false).get(unitType).getRace().get();
                        Chat.tag("VS_" + PosConstants.opponentRace);
                        PosConstants.setEnemyTypes();
                        if (PosConstants.opponentRace == Race.TERRAN) {
                            Strategy.gamePlan = GamePlan.TANK_VIKING;
                            Strategy.DO_USE_CYCLONES = false;
                            Strategy.useTankVikingAdjustments();
                        }
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
                        if (!Strategy.gamePlan.toString().contains("RAVEN") && !Strategy.gamePlan.toString().contains("TURTLE")) {
                            Strategy.DO_DEFENSIVE_TANKS = false;
                            Strategy.DO_DEFENSIVE_LIBS = false;
                        }
                        if (PosConstants.opponentRace != Race.TERRAN) {
                            Strategy.DO_OFFENSIVE_TANKS = false;
                        }
                    }

                    //set enemy cloaked variable
                    if (!Switches.doNeedDetection) {
                        switch (unitType) {
                            case PROTOSS_DARK_TEMPLAR: case PROTOSS_DARK_SHRINE: case PROTOSS_MOTHERSHIP:
                            case ZERG_LURKER_DEN_MP: case ZERG_LURKER_MP: case ZERG_LURKER_MP_EGG:
                            case ZERG_LURKER_MP_BURROWED: case TERRAN_BANSHEE: case TERRAN_WIDOWMINE:
                            case TERRAN_WIDOWMINE_BURROWED: case TERRAN_GHOST: case TERRAN_GHOST_ACADEMY:
                            case ZERG_INFESTOR_BURROWED: case ZERG_BANELING_BURROWED: case ZERG_DRONE_BURROWED:
                            case ZERG_HYDRALISK_BURROWED: case ZERG_QUEEN_BURROWED: case ZERG_SWARM_HOST_BURROWED_MP:
                            case ZERG_ZERGLING_BURROWED: case ZERG_INFESTOR_TERRAN_BURROWED: case ZERG_RAVAGER_BURROWED:
                            case ZERG_ROACH_BURROWED: case ZERG_ULTRALISK_BURROWED:
                                Chat.chatNeverRepeat("Sneaky boy. Looks like detection is needed.");
                                Switches.doNeedDetection = true;
                                //if burrowed unit, or cloaked unit without ghosts available
                                if (Strategy.gamePlan != GamePlan.BC_RUSH &&
                                        (Strategy.gamePlan != GamePlan.GHOST_HELLBAT ||
                                        PosConstants.opponentRace == Race.ZERG ||
                                        UnitUtils.WIDOW_MINE_TYPE.contains(unitType))) {
                                    purchaseEmergencyRaven();
                                }
                        }
                    }

                    switch (unitType) {
                        //change base viking:banshee ratio once tempests hit the field
                        case PROTOSS_TEMPEST:
                            Chat.tag("VS_TEMPESTS");
                            Switches.enemyHasTempests = true;
                            Strategy.VIKING_BANSHEE_RATIO = 1f;
//turned off to test factory production vs tempests
//                            Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_FACTORY)
//                                    .forEach(factory -> BuildManager.liftFactory(factory.unit()));
                            break;
                        //ignore phoenixes until one is verify as real
                        case PROTOSS_PHOENIX:
                            if (!Switches.phoenixAreReal) {
                                continue;
                            }
                            break;
                        case PROTOSS_DARK_TEMPLAR:
                            Chat.tag("VS_DARK_TEMPLAR");
                            break;
                    }

                    //map of every enemy unit
                    allVisibleEnemiesList.add(unitInPool);
                    allEnemiesList.add(unitInPool);
                    if (!allVisibleEnemiesMap.containsKey(unitType)) {
                        allVisibleEnemiesMap.put(unitType, new ArrayList<>());
                    }
                    allVisibleEnemiesMap.get(unitType).add(unitInPool);

                    if (!unit.getFlying().orElse(false)) {
                        enemyIsGround.add(unit);
                    }
                    else {
                        //air units
                        enemyIsAir.add(unit);
                    }
                    if (UnitUtils.DETECTOR_TYPES.contains(unit.getType())) {
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
        for (Base base : baseList) { //TODO: handle FlyingCCs
            //ignore bases that aren't visible
            if (!base.isMyBase() &&
                    Bot.OBS.getVisibility(base.getCcPos()) != Visibility.VISIBLE &&
                    Bot.OBS.getVisibility(base.getResourceMidPoint()) != Visibility.VISIBLE &&
                    base.getMineralPatchUnits().stream().noneMatch(patch -> patch.getDisplayType() == DisplayType.VISIBLE) &&
                    (base.lastScoutedFrame != 0 || !base.isDriedUp())) {
                continue;
            }
            if (Bot.OBS.getVisibility(base.getCcPos()) == Visibility.VISIBLE) {
                base.lastScoutedFrame = Time.nowFrames();
            }

            //update cc
            base.setCc(base.getUpdatedUnit(Units.TERRAN_PLANETARY_FORTRESS, base.getCc(), base.getCcPos()));

            //set who owns the base
            if (!base.isEnemyBase || Bot.OBS.getVisibility(base.getCcPos()) == Visibility.VISIBLE) {
                base.isEnemyBase = !UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.COMMAND_STRUCTURE_TYPE, base.getCcPos(), 2)
                        .isEmpty();
            }

            //update mineral nodes
            base.getMineralPatches().forEach(mineralPatch -> mineralPatch.updateUnit());
            base.getMineralPatches().removeIf(mineralPatch -> mineralPatch.getNode() == null);

            //update gas nodes
            base.getGases().forEach(gas -> gas.updateUnit());

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
            if (!base.getMineralPatchUnits().isEmpty()) {
                if (defaultRallyNode == null) {
                    defaultRallyNode = base.getRallyNode();
                }
            }

            //update turret/tank from inMineralPosition
            for (DefenseUnitPositions unit : base.getInMineralLinePositions()) {
                unit.setUnit(base.getUpdatedUnit(Units.TERRAN_MISSILE_TURRET, unit.getUnit(), unit.getPos()), base);
            }

            //update turret from inFrontPosition
            for (DefenseUnitPositions turret : base.getInFrontPositions()) {
                turret.setUnit(base.getUpdatedUnit(Units.TERRAN_MISSILE_TURRET, turret.getUnit(), turret.getPos()), base);
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
            defaultRallyNode = UnitUtils.getClosestUnitOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_WALL_TYPE, PosConstants.baseLocations.get(0));
        }

        //loop through effects (bile and nukes handled separately)
        for (EffectLocations effect : Bot.OBS.getEffects()) {
            if (effect.getAlliance().orElse(Alliance.SELF) == Alliance.ENEMY) {
                switch ((Effects) effect.getEffect()) {
                    case SCANNER_SWEEP:
                        if (!EnemyScan.contains(effect)) {
                            EnemyScan.add(effect);
                        }
                        break;
                    case PSI_STORM_PERSISTENT:
                    case LIBERATOR_TARGET_MORPH_DELAY_PERSISTENT:
                    case LIBERATOR_TARGET_MORPH_PERSISTENT:
                        enemyMappingList.add(new EnemyMappingEffect(effect));
                        break;
                }
            }
        }

        //add biles
        BileTracker.activeBiles.forEach(bile -> enemyMappingList.add(new EnemyMappingEffect(bile.getEffect())));

        //add adept shades that about to jump
        AdeptShadeTracker.activeShades.stream()
                .filter(shade -> shade.doConsiderThreat())
                .forEach(shade -> enemyMappingList.add(new EnemyMappingShade(shade.getShadeUip().unit())));

        //add scans to enemyMappingList
        EnemyScan.enemyScanSet.stream().forEach(enemyScan -> enemyMappingList.add(new EnemyMappingEffect(enemyScan.scanEffect)));

        //add all enemies to the enemyMappingList (include enemies that entered fog within last 5sec)
        //TODO: include snapshot units like cannons and high ground marines
        //TODO: remember unit positions for units that can't move like siege tanks / burrowed_lurkers
        //      but forget the unit when its position becomes visible, or the unit is seen again elsewhere (this may be auto-done)
        Predicate<UnitInPool> enemyMappingPredicate = enemy -> enemy.getLastSeenGameLoop() +
                (UnitUtils.LONG_RANGE_ENEMIES.contains(enemy.unit().getType()) ?
                        Strategy.MAP_ENEMIES_IN_FOG_DURATION :
                        24
                ) >= Time.nowFrames();
        allEnemiesList.stream()
                //filter to all visible enemies and non-visible tempests that have entered the fog within the last 5sec
                .filter(enemyMappingPredicate)
                .forEach(enemy -> enemyMappingList.add(new EnemyMappingUnit(enemy.unit())));

        Ignored.ignoredUnits.stream()
                .map(ignored -> Bot.OBS.getUnit(ignored.unitTag))
                .filter(u -> u != null && u.isAlive() && u.unit().getAlliance() == Alliance.ENEMY)
                .filter(enemyMappingPredicate)
                .forEach(enemy -> enemyMappingList.add(new EnemyMappingUnit(enemy.unit())));

        NukeTracker.activeNukes.stream()
                .filter(nuke -> nuke.doConsiderThreat())
                .forEach(nuke -> enemyMappingList.add(new EnemyMappingEffect(nuke.getEffect())));

        //add siege tanks and lurkers that are no longer visible
        EnemyUnitMemory.onStep();

        //check for fungal mapping
        Infestor.onStep();

        //set detected and air attack cells on the map
        buildInfluenceMap();

        //update lists of vikings and banshees that are to dive on detectors
        updateDiverStatus();
    }

    private static void updateDiverStatus() { //TODO: make method callable with unit type rather than hardcoded viking and banshee
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
        if (Switches.vikingDiveTarget == null && PosConstants.opponentRace == Race.PROTOSS && !GameCache.vikingList.isEmpty()) {
            List<UnitInPool> tempests = getProtossCapitalShips();
            if (!tempests.isEmpty()) {
                UnitInPool closestTempest = tempests.stream()
                        .filter(u -> u.unit().getType() == Units.PROTOSS_MOTHERSHIP)
                        .findAny()
                        .orElse(UnitUtils.getClosestUnitFromUnitList(tempests, Position.midPointUnitsMedian(GameCache.vikingList)));
                if (tempests.stream().anyMatch(u -> u.unit().getType() == Units.PROTOSS_MOTHERSHIP)) {
                    Chat.tag("VS_Mothership");
                }
                if (closestTempest != null) {
                    Point2d closestTempestPos = closestTempest.unit().getPosition().toPoint2d();
                    List<UnitInPool> nearbyVikings = UnitUtils.getUnitsNearbyOfType(
                            Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, closestTempestPos, Strategy.TEMPEST_DIVE_RANGE);
                    List<UnitInPool> nearbyCyclones = UnitUtils.getUnitsNearbyOfType(
                            Alliance.SELF, Units.TERRAN_CYCLONE, closestTempestPos, Strategy.TEMPEST_DIVE_RANGE);
                    int numVikingsNearby = nearbyVikings.size() + nearbyCyclones.size() / 2; //include cyclones at half value in calculation
                    if (ArmyManager.shouldDiveTempests(closestTempestPos, numVikingsNearby)) {
                        Switches.vikingDiveTarget = closestTempest;
                        if (Switches.vikingDiveTarget != null) {
                            Switches.isDivingTempests = true;
                            Chat.chatWithoutSpam(Chat.VIKING_DIVE, 7);
                        }
                    }
                }
            }
        }
        //start viking dive vs detector
        if (Switches.vikingDiveTarget == null) {
            //don't dive detector if vs terran, no banshees, or if there are still voids or phoenix visible
            if (Strategy.DO_DIVE_MOBILE_DETECTORS && //TODO: temp for vs ANIBot
                    Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
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
                                    UnitUtils.scan(Position.towards(Switches.vikingDiveTarget.unit(), ArmyManager.retreatPos, -2));
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

    public static void buildInfluenceMap() {
        int xMin = InfluenceMaps.toMapCoord(PosConstants.MIN_X);
        int xMax = InfluenceMaps.toMapCoord(PosConstants.MAX_X);
        int yMin = InfluenceMaps.toMapCoord(PosConstants.MIN_Y);
        int yMax = InfluenceMaps.toMapCoord(PosConstants.MAX_Y);

        InfluenceMaps.pointDetected = new boolean[800][800];
        InfluenceMaps.pointIn5RangeVsGround = new boolean[800][800];
        InfluenceMaps.point6RangevsGround = new boolean[800][800];
        InfluenceMaps.point2RangevsGround = new boolean[800][800];
        InfluenceMaps.pointInRavenCastRange = new boolean[800][800];
        InfluenceMaps.pointIn5RangeVsBoth = new boolean[800][800];
        InfluenceMaps.pointIn7RangeVsBoth = new boolean[800][800];
        InfluenceMaps.pointInEnemyVision = new boolean[800][800];
        InfluenceMaps.enemyInVikingRange = new boolean[800][800];
        InfluenceMaps.enemyInMissileTurretRange = new boolean[800][800];
        InfluenceMaps.pointThreatToAirPlusBufferValue = new int[800][800];
        InfluenceMaps.pointThreatToAirPlusBuffer = new boolean[800][800];
        InfluenceMaps.pointSupplyInSeekerRange = new float[800][800];
        InfluenceMaps.pointEmpValue = new float[800][800];
        InfluenceMaps.pointAutoturretValue = new float[800][800];
        InfluenceMaps.pointThreatToAirValue = new int[800][800];
        InfluenceMaps.pointEnemyAttackersWith10Range = new int[800][800];
        InfluenceMaps.pointThreatToAir = new boolean[800][800];
        InfluenceMaps.pointThreatToAirFromGround = new int[800][800];
        InfluenceMaps.pointThreatToGroundFromGround = new int[800][800];
        InfluenceMaps.pointThreatToGroundValue = new int[800][800];
        InfluenceMaps.pointDamageToGroundValue = new int[800][800];
        InfluenceMaps.pointDamageToAirValue = new int[800][800];
        InfluenceMaps.pointThreatToGround = new boolean[800][800];
        InfluenceMaps.pointThreatToGroundPlusBuffer = new boolean[800][800];
        InfluenceMaps.pointThreatToGroundPlusBufferValue = new int[800][800];
        InfluenceMaps.pointPersistentDamageToGround = new boolean[800][800];
        InfluenceMaps.pointPersistentDamageToAir = new boolean[800][800];
        InfluenceMaps.pointPFTargetValue = new int[800][800];
        InfluenceMaps.pointGroundUnitWithin13 = new boolean[800][800];
        InfluenceMaps.pointRaiseDepots = new boolean[800][800];
        InfluenceMaps.pointVikingsStayBack = new boolean[800][800];

        boolean hasGhosts = !Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_GHOST).isEmpty();
        float empRadius = Bot.OBS.getUpgrades().contains(Upgrades.ENHANCED_SHOCKWAVES) ? 1.5f : 1f; //subtracting 0.5 for rounding/projectile-time reasons
        int autoturretRange = Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING) ? 8 : 7; //+1 for turret radius

        for (EnemyMapping enemy : enemyMappingList) {
            //only look at box of max range around the enemy
            int xStart = Math.max(InfluenceMaps.toMapCoord(enemy.x - enemy.maxRange), xMin);
            int yStart = Math.max(InfluenceMaps.toMapCoord(enemy.y - enemy.maxRange), yMin);
            int xEnd = Math.min(InfluenceMaps.toMapCoord(enemy.x + enemy.maxRange), xMax);
            int yEnd = Math.min(InfluenceMaps.toMapCoord(enemy.y + enemy.maxRange), yMax);

            //loop through box
            for (int x = xStart; x <= xEnd; x++) {
                for (int y = yStart; y <= yEnd; y++) {
                    double distance = Position.distance(x/2f, y/2f, enemy.x, enemy.y);

                    //depot raising
                    if (!enemy.isAir && enemy.canMove &&
                            distance < Strategy.DISTANCE_RAISE_DEPOT) {
                        if (enemy.isArmy || doCloseWallToAllUnits()) {
                            InfluenceMaps.pointRaiseDepots[x][y] = true;
                        }
                    }

                    //viking keeping distance vs tempests
                    if (distance < 15 + Strategy.KITING_BUFFER) {
                        InfluenceMaps.pointVikingsStayBack[x][y] = true;
                    }

                    if (enemy.unitType != Units.INVALID &&
                            enemy.isTargettableUnit() &&
                            (enemy.groundAttackRange > 0 || enemy.airAttackRange > 0) &&
                            distance <= 10) {
                        InfluenceMaps.pointEnemyAttackersWith10Range[x][y]++;
                    }

                    //threat level to ground
                    if (distance < enemy.groundAttackRange &&
                            (distance > 2.5 || enemy.unitType != Units.TERRAN_SIEGE_TANK_SIEGED)) { //siege tank min range = 2 (+ unit radius)
                        InfluenceMaps.pointThreatToGroundValue[x][y] += enemy.threatLevel;
                        InfluenceMaps.pointDamageToGroundValue[x][y] += enemy.groundDamage;
                        InfluenceMaps.pointThreatToGround[x][y] = true;
                        if (enemy.isPersistentDamage) {
                            InfluenceMaps.pointPersistentDamageToGround[x][y] = true;
                        }
                    }

                    //ground threat range + extra buffer
                    if (enemy.groundAttackRange != 0 &&
                            distance < enemy.groundAttackRange + (enemy.canMove ? Strategy.RAVEN_DISTANCING_BUFFER : 0)) {
                        InfluenceMaps.pointThreatToGroundPlusBufferValue[x][y] += enemy.threatLevel;
                        InfluenceMaps.pointThreatToGroundPlusBuffer[x][y] = true;
                    }

                    //threat to air
                    if (distance < enemy.airAttackRange) {
                        InfluenceMaps.pointThreatToAirValue[x][y] += enemy.threatLevel;
                        InfluenceMaps.pointThreatToAir[x][y] = true;
                        InfluenceMaps.pointDamageToAirValue[x][y] += enemy.airDamage;
                        if (enemy.isPersistentDamage) {
                            InfluenceMaps.pointPersistentDamageToAir[x][y] = true;
                        }
                    }

                    //air threat range + extra buffer
                    if (enemy.airAttackRange != 0 &&
                            distance < enemy.airAttackRange + (enemy.canMove ? Strategy.RAVEN_DISTANCING_BUFFER : 0)) {
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
                    if (distance <= enemy.sightRange &&
                            (enemy.isAir || Bot.OBS.terrainHeight(Point2d.of(enemy.x, enemy.y)) + 1 > Bot.OBS.terrainHeight(Point2d.of(x/2f, y/2f)))) {
                        InfluenceMaps.pointInEnemyVision[x][y] = true;
                        //DebugHelper.drawBox(x/2f, y/2f, Color.GRAY, 0.25f);
                    }

                    //autoturret cast range
                    if (distance < Strategy.RAVEN_CAST_RANGE && !UnitUtils.IGNORED_TARGETS.contains(enemy.unitType) && !enemy.isTumor) {
                        InfluenceMaps.pointInRavenCastRange[x][y] = true;
                    }

                    //marine range
                    if (distance < Strategy.MARINE_RANGE && !enemy.isEffect && enemy.isTargettableUnit()) {
                        InfluenceMaps.pointIn5RangeVsBoth[x][y] = true;
                    }

                    //ghost range
                    if (distance < Strategy.GHOST_RANGE && !enemy.isEffect && enemy.isTargettableUnit()) {
                        InfluenceMaps.pointIn7RangeVsBoth[x][y] = true;
                    }

                    //emp value
                    if (distance < empRadius + enemy.unitRadius && !enemy.isEffect) {
                        InfluenceMaps.pointEmpValue[x][y] += enemy.empValue;
                    }

                    //autoturret value
                    if (distance < autoturretRange + enemy.unitRadius &&
                            !enemy.isEffect &&
                            enemy.isTargettableUnit() &&
                            enemy.threatLevel != 0) {
                        InfluenceMaps.pointAutoturretValue[x][y] += enemy.supply;
                    }

                    if (enemy.isAir) {

                        //viking range
                        if (distance < Strategy.VIKING_RANGE && !enemy.isEffect && enemy.isTargettableUnit()) {
                            InfluenceMaps.enemyInVikingRange[x][y] = true;
                        }

                        //missile turret range
                        if (!enemy.isEffect && distance < 8 + (Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING) ? 1 : 0)) {
                            InfluenceMaps.enemyInMissileTurretRange[x][y] = true;
                        }
                    }
                    else { //ground unit or effect

                        //hellion range
                        if (distance < Strategy.HELLION_RANGE && !enemy.isEffect && enemy.isTargettableUnit()) {
                            InfluenceMaps.pointIn5RangeVsGround[x][y] = true;
                        }

                        //banshee range
                        if (distance < Strategy.BANSHEE_RANGE && !enemy.isEffect && enemy.isTargettableUnit()) {
                            InfluenceMaps.point6RangevsGround[x][y] = true;
                        }

                        //hellbat range
                        if (distance < Strategy.HELLION_RANGE && !enemy.isEffect && enemy.isTargettableUnit()) {
                            InfluenceMaps.point2RangevsGround[x][y] = true;
                        }

                        //threat to air from ground
                        float airAttackRange = (enemy.unitType == Units.TERRAN_CYCLONE) ? 9 : enemy.airAttackRange;
                        if (distance < airAttackRange) {
                            InfluenceMaps.pointThreatToAirFromGround[x][y] += enemy.threatLevel;
                        }

                        //threat to ground from ground
                        float groundAttackRange = (enemy.unitType == Units.TERRAN_CYCLONE) ? 9 : enemy.groundAttackRange;
                        if (distance < groundAttackRange) {
                            InfluenceMaps.pointThreatToGroundFromGround[x][y] += enemy.threatLevel;
                        }

                        //PF target value (for PF & tank targetting)
                        if (!enemy.isEffect && enemy.isTargettableUnit() && distance < 1.25) {
                            InfluenceMaps.pointPFTargetValue[x][y] += enemy.pfTargetLevel;
                        }

                        //ground unit within 13 range
                        if (distance < 13 && !enemy.isEffect && enemy.isTargettableUnit()) {
                            InfluenceMaps.pointGroundUnitWithin13[x][y] = true;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x/2-0.15f,y/2-0.15f, z), Point.of(x/2+0.15f,y/2+0.15f, z), Color.GREEN);
                        }
                    }
                }
            }
        }
    }

    private static boolean doCloseWallToAllUnits() {
        return Strategy.WALL_OFF_IMMEDIATELY || (Strategy.gamePlan == GamePlan.MARINE_RUSH &&
                UnitMicroList.numOfUnitClass(MarineBasic.class) < MarineAllIn.MIN_MARINES_TO_ATTACK);
    }

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

    private static void purchaseEmergencyRaven() {
        //do nothing if I already have a raven
        if (UnitUtils.numMyUnits(Units.TERRAN_RAVEN, true) > 0) {
            return;
        }

        //make a starport available and purchase a raven
        Optional<Unit> starport = UnitUtils.getEmergencyProductionStructure(Units.TERRAN_RAVEN);
        if (starport.isPresent()) {
            if (UnitUtils.getOrder(starport.get()) != null) {
                ActionHelper.unitCommand(starport.get(), Abilities.CANCEL_LAST, false);
            }
            KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_RAVEN, starport.get()));
        }
    }

    public static void setInitialEnemyBases() {
        //set enemy main and nat true for isEnemyBase
        Base enemyMain = baseList.get(GameCache.baseList.size() - 1);
        enemyMain.isEnemyBase = true;
        Base enemyNat = baseList.get(GameCache.baseList.size() - 2);
        enemyNat.isEnemyBase = true;

        //set 2 closest 3rd bases true for isEnemyBase
        baseList.stream()
                .filter(base -> !base.equals(enemyMain) && !base.equals(enemyNat))
                .sorted(Comparator.comparing(base -> base.getCcPos().distance(enemyNat.getCcPos())))
                .limit(2)
                .forEach(base -> base.isEnemyBase = true);
    }
}
