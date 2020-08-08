package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.*;
import java.util.stream.Collectors;

public class GameCache {

    public static int mineralBank;
    public static int gasBank;
    public static int freeSupply;

    public static final List<Upgrades> upgradesPurchased = new ArrayList<>(); //any upgrade that is complete, in production, or queued up for purchase
    public static final List<Upgrades> upgradesCompleted = new ArrayList<>(); //completed upgrades

    public static boolean[][] pointDetected = null;
    public static boolean[][] pointInBansheeRange = null;
    public static boolean[][] pointInVikingRange = null;
    public static short[][] pointThreatToAir = null;
    public static short[][] pointThreatToAirFromGround = null;
    public static boolean[][] pointThreatToAirPlusBuffer = null;
    public static float[][] pointSupplyInSeekerRange = null;
    public static float[][] pointThreatToGround = null;
    public static int[][] pointPFTargetValue = null;
    public static boolean[][] pointGroundUnitWithin13 = null;
    public static boolean[][] pointRaiseDepots = null;
    public static boolean[][] pointVikingsStayBack = null;

    public static int numMacroOCs = 0;
    public static final List<Unit> ccList = new ArrayList<>();
    public static List<Tag> buildingScvTags;
    public static final List<UnitInPool> scvMineralList = new ArrayList<>();
    public static final List<UnitInPool> scvIdleList = new ArrayList<>();
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

        //free up ignored units
        IgnoredUnit.ignoredUnits.removeIf(ignoredUnit -> ignoredUnit.doReleaseUnit());

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
        scvIdleList.clear();
        scvMineralList.clear();
        allFriendliesMap.clear();
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

        for (int i = 0; i< allEnemiesList.size(); i++) {
            //if currently on screen or if dead, remove it
            if (allEnemiesList.get(i).getLastSeenGameLoop() == Bot.OBS.getGameLoop() || !allEnemiesList.get(i).isAlive()) {
                allEnemiesList.remove(i--);
            }
        }

        for (UnitInPool unitInPool: Bot.OBS.getUnits()) {
            Unit unit = unitInPool.unit();

            //ignore specified units
            if (IgnoredUnit.ignoredUnits.stream().anyMatch(ignoredUnit -> ignoredUnit.unitTag.equals(unit.getTag()))) {
                continue;
            }

            if (unit.getType() instanceof Units.Other) {
                float x = unit.getPosition().getX();
                float y = unit.getPosition().getY();
                if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.3f,y-0.3f, Position.getZ(x, y)), Point.of(x+0.3f,y+0.3f, Position.getZ(x, y)), Color.GREEN);
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
                    //ignore selected friendly units when playing archon mode
                    if (Strategy.ARCHON_MODE && !UnitUtils.STRUCTURE_TYPE.contains(unitType) && unit.getSelected().orElse(false)) {
                        continue;
                    }

                    //skip most structures in production
                    if (unit.getBuildProgress() < 1.0f) {
                        inProductionList.add(unit);
                        inProductionMap.put((Units)unit.getType(), inProductionMap.getOrDefault((Units)unit.getType(), 0) + 1);
                        switch (unitType) {
                            case TERRAN_REFINERY: case TERRAN_REFINERY_RICH: case TERRAN_REFINERY_RICH_410: case TERRAN_COMMAND_CENTER:
                                break;
                            default: //ignore in-production units except the ones above
                                continue;
                        }
                    }

                    //see what repair is required for wall or burning structures
                    if (UnitUtils.isStructure(unitType)) {
                        if (LocationConstants.isWallStructure(unit)) {
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
                    switch (unitType) {
                        case TERRAN_COMMAND_CENTER: case TERRAN_PLANETARY_FORTRESS: case TERRAN_ORBITAL_COMMAND:
                        case TERRAN_COMMAND_CENTER_FLYING: case TERRAN_ORBITAL_COMMAND_FLYING:
                            ccList.add(unit);
                            break;
                        case TERRAN_REFINERY: case TERRAN_REFINERY_RICH: case TERRAN_REFINERY_RICH_410:
                            refineryList.add(unit);
                            break;
                        case TERRAN_SUPPLY_DEPOT:
                            if (LocationConstants.isWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            break;
                        case TERRAN_ENGINEERING_BAY:
                            if (LocationConstants.isWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            break;
                        case TERRAN_BARRACKS:
                            if (LocationConstants.isWallStructure(unit)) {
                                GameCache.wallStructures.add(unit);
                            }
                            if (!unit.getOrders().isEmpty()) {
                                Units unitProducing = Bot.abilityToUnitType.get(unit.getOrders().get(0).getAbility());
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_BARRACKS_FLYING:
                            barracksList.add(unitInPool);
                            break;
                        case TERRAN_FACTORY:
                            if (!unit.getOrders().isEmpty()) {
                                Units unitProducing = Bot.abilityToUnitType.get(unit.getOrders().get(0).getAbility());
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_FACTORY_FLYING:
                            factoryList.add(unitInPool);
                            break;
                        case TERRAN_STARPORT:
                            if (!unit.getOrders().isEmpty()) {
                                Units unitProducing = Bot.abilityToUnitType.get(unit.getOrders().get(0).getAbility());
                                inProductionMap.put(unitProducing, inProductionMap.getOrDefault(unitProducing, 0) + 1);
                            }
                        case TERRAN_STARPORT_FLYING:
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
                            siegeTankList.add(unit);
                            break;
                        case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG:
                            liberatorList.add(unit);
                            break;
                        case TERRAN_BANSHEE:
                            bansheeList.add(unit);
                            break;
                        case TERRAN_RAVEN:
                            ravenList.add(unit);
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
                        LocationConstants.initEnemyRaceSpecifics();
                    }

                    //ignore hallucinations
                    if (unit.getHallucination().orElse(false)) {
                        continue;
                    }

                    //check if enemy can create air units
                    if (!Switches.enemyCanProduceAir && UnitUtils.EVIDENCE_OF_AIR.contains(unitType)) {
                        Bot.ACTION.sendChat("Have our viking pilots updated their wills?  I smell enemy air units.", ActionChat.Channel.BROADCAST);
                        Switches.enemyCanProduceAir = true;
                        Strategy.DO_INCLUDE_LIBS = false;
                        Strategy.DO_INCLUDE_TANKS = false;
                    }

                    //set enemy cloaked variable TODO: zerg burrow upgrade not accounted for
                    switch (unitType) {
                        case PROTOSS_DARK_TEMPLAR: case PROTOSS_DARK_SHRINE: case PROTOSS_MOTHERSHIP:
                        case ZERG_LURKER_DEN_MP: case ZERG_LURKER_MP: case ZERG_LURKER_MP_EGG: case ZERG_LURKER_MP_BURROWED:
                        case TERRAN_BANSHEE: case TERRAN_WIDOWMINE: case TERRAN_WIDOWMINE_BURROWED: case TERRAN_GHOST: case TERRAN_GHOST_ACADEMY:
                            if (!Switches.enemyHasCloakThreat) {
                                Bot.ACTION.sendChat("I can't see those. Time to up my detection.", ActionChat.Channel.BROADCAST);
                            }
                            Switches.enemyHasCloakThreat = true;
                            break;
                    }

                    switch (unitType) {
                        //change base viking:banshee ratio once tempests hit the field
                        case PROTOSS_TEMPEST:
                            Strategy.VIKING_BANSHEE_RATIO = 1f;
                            break;
                        //are phoenixes real?
                        case PROTOSS_PHOENIX:
                            if (!Switches.phoenixAreReal && !unit.getHallucination().orElse(false)) {
                                if (!Bot.OBS.getUnits(Alliance.SELF, u ->
                                        (u.unit().getType() == Units.TERRAN_MISSILE_TURRET || u.unit().getType() == Units.TERRAN_RAVEN) &&
                                        u.unit().getBuildProgress() == 1 &&
                                        UnitUtils.getDistance(u.unit(), unit) < 10).isEmpty()) {
                                    Switches.phoenixAreReal = true;
                                    if (!Switches.enemyCanProduceAir) {
                                        UnitUtils.EVIDENCE_OF_AIR.add(Units.PROTOSS_PHOENIX);
                                    }
                                }
                                continue; //ignore phoenixes until one is verify as real (not halluc)
                            }
                            break;
                    }

                    //map of every enemy unit
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

                        //if enemy has any air threat
                        if (Strategy.enemyHasAirThreat == false && !unit.getHallucination().orElse(false) && unit.getType() != Units.ZERG_OVERLORD) {
                            Strategy.enemyHasAirThreat = true;
                        }
                    }
                    if (unit.getDetectRange().orElse(0f) > 0f || unit.getType() == Units.PROTOSS_OBSERVER) { // || unit.getType() == Units.PROTOSS_TEMPEST) {
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
            if (unitInPool.unit().getType() instanceof Units) { //TODO: try removing this when rich gas IDs are fixed in ocraft api
                Units unitType = (Units) unitInPool.unit().getType();
                if (!allEnemiesMap.containsKey(unitType)) {
                    allEnemiesMap.put(unitType, new ArrayList<>());
                }
                allEnemiesMap.get(unitType).add(unitInPool);
            }
        }

        //************************
        // *** BUILD BASE LIST ***
        //************************
        List<UnitInPool> enemyCCs = Bot.OBS.getUnits(Alliance.ENEMY, enemyCC -> UnitUtils.enemyCommandStructures.contains(enemyCC.unit().getType())); //TODO: refactor when allEnemiesList doesn't duplicate snapshots
        for (Base base : baseList) { //TODO: handle FlyingCCs
            //ignore bases that aren't mine and aren't visible
            if (base.getCc().isEmpty() && Bot.OBS.getVisibility(base.getCcPos()) != Visibility.VISIBLE) {
                continue;
            }
            base.lastScoutedFrame = Bot.OBS.getGameLoop();

            //update cc
            base.setCc(base.getUpdatedUnit(Units.TERRAN_PLANETARY_FORTRESS, base.getCc(), base.getCcPos()));

            //set who owns the base
            base.isEnemyBase = enemyCCs.stream().anyMatch(enemyCC -> UnitUtils.getDistance(enemyCC.unit(), base.getCcPos()) < 2);

            //set mineral nodes
            base.getMineralPatches().clear();
            base.getMineralPatches().addAll(mineralNodeList.stream()
                    .filter(node -> UnitUtils.getDistance(node, base.getCcPos()) < 10)
                    .collect(Collectors.toList()));

            //check if base is dry
            if (!base.isDryedUp() && base.getMineralPatches().isEmpty()) { //TODO: check gas (need workaround for geyser snapshot always having zero gas)
                base.setDryedUp(true);
            }

            //nothing else required if enemy base or untaken base
            if (base.isEnemyBase || base.getCc().isEmpty()) {
                continue;
            }

            //set this base's rally node
            if (!base.getMineralPatches().isEmpty()) {
                base.setRallyNode(base.getMineralPatches().get(0));
            }

            //set default rally node for any base
            Unit cc = base.getCc().get().unit();
            if ((cc.getAssignedHarvesters().get() < cc.getIdealHarvesters().get()) && !base.getMineralPatches().isEmpty()) {
                if (GameCache.defaultRallyNode == null) {
                    GameCache.defaultRallyNode = base.getRallyNode();
                }
            }

            //set geyser nodes and refineries
            base.getGases().clear();
            geyserList.stream()
                    .filter(geyser -> UnitUtils.getDistance(geyser, base.getCcPos()) < 10)
                    .forEach(geyser -> {
                        Gas gas = new Gas(geyser);
                        refineryList.stream()
                                .filter(refinery -> UnitUtils.getDistance(geyser, refinery) < 1)
                                .findFirst()
                                .ifPresent(refinery -> gas.setRefinery(refinery));
                        base.getGases().add(gas);
                    });


            //update turret
            if (!base.isMyMainBase()) { //skip main base
                for (DefenseUnitPositions turret : base.getTurrets()) {
                    turret.setUnit(base.getUpdatedUnit(Units.TERRAN_MISSILE_TURRET, turret.getUnit(), turret.getPos()));
                }
            }

        }

        //if all my bases have no minerals left, set rally position to nearest mineral patch elsewhere
        if (GameCache.defaultRallyNode == null) {
            GameCache.defaultRallyNode = UnitUtils.getClosestUnitOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_NODE_TYPE, LocationConstants.baseLocations.get(0));
        }


        //loop through effects
        for (EffectLocations effect : Bot.OBS.getEffects()) {
            if (effect.getAlliance().orElse(Alliance.SELF) == Alliance.ENEMY) {
                switch ((Effects) effect.getEffect()) {
                    case SCANNER_SWEEP:
                    case RAVAGER_CORROSIVE_BILE_CP:
                    case PSI_STORM_PERSISTENT:
                        enemyMappingList.add(new EnemyUnit(effect));
                        break;
                }
            }
        }

        //add all enemies to the enemyMappingList (include enemies that entered fog within last 5sec)
        allEnemiesList.stream()
                //filter to all visible enemies and non-visible tempests that have entered the fog within the last 5sec
                .filter(enemy -> enemy.getLastSeenGameLoop() +
                        ((enemy.unit().getType() == Units.PROTOSS_TEMPEST) ? Strategy.MAP_ENEMIES_IN_FOG_DURATION : 0) >= Bot.OBS.getGameLoop())
                .forEach(enemy -> enemyMappingList.add(new EnemyUnit(enemy.unit())));

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

        // === vikings ===
        //start viking dive vs tempests
        if (Switches.vikingDiveTarget == null && LocationConstants.opponentRace == Race.PROTOSS && !GameCache.vikingList.isEmpty()) {
            List<UnitInPool> tempests = getProtossCapitalShips();
            if (!tempests.isEmpty()) {
                UnitInPool closestTempest = UnitUtils.getClosestUnitFromUnitList(tempests, Position.midPointUnitsMedian(GameCache.vikingList));
                if (closestTempest != null) {
                    Point2d closestTempestPos = closestTempest.unit().getPosition().toPoint2d();
                    List<UnitInPool> nearbyVikings = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, closestTempestPos, Strategy.TEMPEST_DIVE_RANGE);
                    if (ArmyManager.shouldDiveTempests(closestTempestPos, nearbyVikings.size())) {
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
            //don't dive observer if there are still voids or phoenix visible
            if (LocationConstants.opponentRace != Race.PROTOSS ||
                    (UnitUtils.getVisibleEnemyUnitsOfType(Units.PROTOSS_PHOENIX).size() +
                            UnitUtils.getVisibleEnemyUnitsOfType(Units.PROTOSS_VOIDRAY).size() == 0)) {
                for (Unit detector : GameCache.enemyDetector) {
                    if (detector.getFlying().orElse(false)) {
                        if (ArmyManager.shouldDive(Units.TERRAN_VIKING_FIGHTER, detector)) {
                            Switches.vikingDiveTarget = Bot.OBS.getUnit(detector.getTag());
                            //scan if needed to target (cloaked observer)
                            if (Switches.vikingDiveTarget.unit().getType() == Units.PROTOSS_OBSERVER &&
                                    Switches.vikingDiveTarget.unit().getCloakState().orElse(CloakState.CLOAKED_DETECTED) == CloakState.CLOAKED) {
                                if (UnitUtils.canScan()) {
                                    List<Unit> orbitals = GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_ORBITAL_COMMAND, Collections.emptyList());
                                    Bot.ACTION.unitCommand(orbitals, Abilities.EFFECT_SCAN, Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), ArmyManager.retreatPos, -2), false);
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
                    (!Switches.vikingDiveTarget.isAlive() || Switches.vikingDiveTarget.getLastSeenGameLoop() != Bot.OBS.getGameLoop())) {
                //end dive logic
                Switches.vikingDiveTarget = null;
            }
            else {
                //if tempest target is dead or if vikings lost the target in the fog, get another
                if (Switches.isDivingTempests) {
                    if (!Switches.vikingDiveTarget.isAlive() ||
                            (Switches.vikingDiveTarget.getLastSeenGameLoop() != Bot.OBS.getGameLoop() &&
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
        int xMin = (int) LocationConstants.SCREEN_BOTTOM_LEFT.getX();
        int xMax = (int) LocationConstants.SCREEN_TOP_RIGHT.getX();
        int yMin = (int) LocationConstants.SCREEN_BOTTOM_LEFT.getY();
        int yMax = (int) LocationConstants.SCREEN_TOP_RIGHT.getY();

        pointDetected = new boolean[400][400];
        pointInBansheeRange = new boolean[400][400];
        pointInVikingRange = new boolean[400][400];
        pointThreatToAirPlusBuffer = new boolean[400][400];
        pointSupplyInSeekerRange = new float[400][400];
        pointThreatToAir = new short[400][400];
        pointThreatToAirFromGround = new short[400][400];
        pointThreatToGround = new float[400][400];
        pointPFTargetValue = new int[400][400];
        pointGroundUnitWithin13 = new boolean[400][400];
        pointRaiseDepots = new boolean[400][400];
        pointVikingsStayBack = new boolean[400][400];

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
                    //depot raising
                    if (!enemy.isAir && enemy.isArmy && distance < Strategy.DISTANCE_RAISE_DEPOT) {
                        pointRaiseDepots[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.3f,y-0.3f, Position.getZ(x, y)), Point.of(x+0.3f,y+0.3f, Position.getZ(x, y)), Color.GREEN);
                    }
                    //viking keeping distance vs tempests
                    if (distance < (15 + Strategy.KITING_BUFFER)) {
                        pointVikingsStayBack[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.2f,y-0.2f, Position.getZ(x, y)), Point.of(x+0.3f,y+0.3f, Position.getZ(x, y)), Color.PURPLE);
                    }

                    //threat to ground
                    if (distance < enemy.groundAttackRange) {
                        pointThreatToGround[x][y] += enemy.threatLevel;
                    }

                    //air threat
                    if (enemy.airAttackRange != 0 && distance < enemy.airAttackRange + Strategy.RAVEN_DISTANCING_BUFFER) {
                        pointThreatToAirPlusBuffer[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.3f,y-0.3f, Position.getZ(x, y)), Point.of(x+0.3f,y+0.3f, Position.getZ(x, y)), Color.GREEN);
                    }

                    //seeker missile target
                    if (distance < Strategy.SEEKER_RADIUS && enemy.isArmy && !enemy.isSeekered) {
                        pointSupplyInSeekerRange[x][y] += enemy.supply;
                    }

                    //detection
                    if (enemy.isDetector && distance < enemy.detectRange) {
                        pointDetected[x][y] = true;
                        //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.3f,y-0.3f, Position.getZ(x, y)), Point.of(x+0.3f,y+0.3f, Position.getZ(x, y)), Color.BLUE);
                    }

                    if (enemy.isAir) {

                        //viking range
                        if (distance < Strategy.VIKING_RANGE) {
                            pointInVikingRange[x][y] = true;
                        }

                        //threat to air from air
                        if (distance < enemy.airAttackRange) {
//                            pointUnsafeFromAir[x][y] = true;
                            pointThreatToAir[x][y] += enemy.threatLevel;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.2f,y-0.2f, Position.getZ(x, y)), Point.of(x+0.2f,y+0.2f, Position.getZ(x, y)), Color.PURPLE);
                        }
                    }
                    else { //ground unit or effect

                        //banshee range
                        if (distance < Strategy.BANSHEE_RANGE && !enemy.isEffect) {
                            pointInBansheeRange[x][y] = true;
                        }

                        //threat to air from ground
                        if (distance < enemy.airAttackRange) {
//                            pointUnsafeFromGround[x][y] = true;
                            pointThreatToAir[x][y] += enemy.threatLevel;
                            pointThreatToAirFromGround[x][y] += enemy.threatLevel;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.1f,y-0.1f, Position.getZ(x, y)), Point.of(x+0.1f,y+0.1f, Position.getZ(x, y)), Color.RED);
                        }

                        //PF target value (for PF & tank targetting)
                        if (distance < 1.25) {
                            pointPFTargetValue[x][y] += enemy.pfTargetLevel;
                        }

                        //ground unit within 13 range
                        if (distance < 13) {
                            pointGroundUnitWithin13[x][y] = true;
                            //if (Bot.isDebugOn) Bot.DEBUG.debugBoxOut(Point.of(x-0.15f,y-0.15f, Position.getZ(x, y)), Point.of(x+0.15f,y+0.15f, Position.getZ(x, y)), Color.GREEN);
                        }
                    }
                }
            }
        }

//        //debug threat text
        if (Bot.isDebugOn) {
            for (int x = xMin; x <= xMax; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    if (pointThreatToAir[x][y] > 150) {
                        //Bot.DEBUG.debugTextOut(String.valueOf(pointPFTargetValue[x][y]), Point.of(x, y, Position.getZ(x, y)), Color.RED, 12);
                        Bot.DEBUG.debugBoxOut(Point.of(x-0.17f,y-0.17f, Position.getZ(x, y)), Point.of(x+0.17f,y+0.17f, Position.getZ(x, y)), Color.RED);
                    }
                }
            }
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

    public static float distance(int x1, int y1, float x2, float y2) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        return (float)Math.sqrt(width*width + height*height);
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
        if ((cc.getAssignedHarvesters().get() < cc.getIdealHarvesters().get()) && !base.getMineralPatches().isEmpty()) {
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
