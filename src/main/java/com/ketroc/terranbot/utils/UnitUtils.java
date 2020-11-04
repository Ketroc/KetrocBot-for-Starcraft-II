package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.StructureSize;
import com.ketroc.terranbot.models.Base;

import java.util.*;
import java.util.stream.Collectors;

public class UnitUtils {

    public static final Set<Units> WORKER_TYPE = new HashSet<>(Set.of(
            Units.ZERG_DRONE, Units.ZERG_DRONE_BURROWED, Units.PROTOSS_PROBE, Units.TERRAN_SCV, Units.TERRAN_MULE));
    public static final Set<Units> GAS_GEYSER_TYPE = new HashSet<>(Set.of(
            Units.NEUTRAL_RICH_VESPENE_GEYSER, Units.NEUTRAL_SPACE_PLATFORM_GEYSER, Units.NEUTRAL_VESPENE_GEYSER,
            Units.NEUTRAL_PROTOSS_VESPENE_GEYSER, Units.NEUTRAL_PURIFIER_VESPENE_GEYSER, Units.NEUTRAL_SHAKURAS_VESPENE_GEYSER));
    public static final Set<Units> GAS_STRUCTURE_TYPES = new HashSet<>(Set.of(
            Units.TERRAN_REFINERY, Units.TERRAN_REFINERY_RICH,
            Units.ZERG_EXTRACTOR, Units.ZERG_EXTRACTOR_RICH,
            Units.PROTOSS_ASSIMILATOR, Units.PROTOSS_ASSIMILATOR_RICH));
    public static final Set<Units> REFINERY_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_REFINERY, Units.TERRAN_REFINERY_RICH));
    public static final Set<Units> MINERAL_NODE_TYPE = new HashSet<>(Set.of(
            Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750,
            Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750,
            Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750,
            Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750,
            Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750,
            Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750,
            Units.NEUTRAL_MINERAL_FIELD_OPAQUE, Units.NEUTRAL_MINERAL_FIELD_OPAQUE900, Units.NEUTRAL_MINERAL_FIELD450));
    public static final Set<Units> MINERAL_WALL_TYPE = new HashSet<>(Set.of(
            Units.NEUTRAL_MINERAL_FIELD_OPAQUE, Units.NEUTRAL_MINERAL_FIELD_OPAQUE900, Units.NEUTRAL_MINERAL_FIELD450));
    public static final Set<Units> MINERAL_NODE_TYPE_LARGE = new HashSet<>(Set.of(
            Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD,
            Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD,
            Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD));
    public static final Set<Units> COMMAND_CENTER_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS,
            Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING));
    public static final Set<Units> COMMAND_STRUCTURE_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS,
            Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING,
            Units.PROTOSS_NEXUS, Units.ZERG_HATCHERY, Units.ZERG_LAIR, Units.ZERG_HIVE));
    public static final Set<Units> SIEGE_TANK_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_SIEGE_TANK, Units.TERRAN_SIEGE_TANK_SIEGED));
    public static final Set<Units> LIBERATOR_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_LIBERATOR, Units.TERRAN_LIBERATOR_AG));
    public static final Set<Units> WIDOW_MINE_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_WIDOWMINE, Units.TERRAN_WIDOWMINE_BURROWED));
    public static final Set<Units> STRUCTURE_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_FUSION_CORE, Units.TERRAN_SUPPLY_DEPOT, Units.TERRAN_SUPPLY_DEPOT_LOWERED,
            Units.TERRAN_ENGINEERING_BAY, Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND,
            Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING,
            Units.TERRAN_ARMORY, Units.TERRAN_MISSILE_TURRET, Units.TERRAN_BUNKER,
            Units.TERRAN_GHOST_ACADEMY, Units.TERRAN_SENSOR_TOWER, Units.TERRAN_BARRACKS,
            Units.TERRAN_BARRACKS_FLYING, Units.TERRAN_FACTORY, Units.TERRAN_FACTORY_FLYING,
            Units.TERRAN_STARPORT, Units.TERRAN_STARPORT_FLYING, Units.TERRAN_REFINERY,
            Units.TERRAN_REFINERY_RICH, Units.TERRAN_BARRACKS_TECHLAB, Units.TERRAN_FACTORY_TECHLAB,
            Units.TERRAN_STARPORT_TECHLAB, Units.TERRAN_TECHLAB, Units.TERRAN_BARRACKS_REACTOR,
            Units.TERRAN_FACTORY_REACTOR, Units.TERRAN_STARPORT_REACTOR, Units.TERRAN_REACTOR));
    public static Set<Units> EVIDENCE_OF_AIR = new HashSet<>(Set.of(
            Units.TERRAN_FUSION_CORE, Units.TERRAN_BANSHEE, Units.TERRAN_BATTLECRUISER,
            Units.PROTOSS_STARGATE, Units.PROTOSS_FLEET_BEACON, Units.PROTOSS_TEMPEST,
            Units.PROTOSS_ORACLE,Units.PROTOSS_ORACLE_STASIS_TRAP, Units.PROTOSS_VOIDRAY,
            Units.ZERG_SPIRE, Units.ZERG_GREATER_SPIRE, Units.ZERG_MUTALISK,
            Units.ZERG_CORRUPTOR, Units.ZERG_BROODLORD, Units.ZERG_BROODLORD_COCOON));
    public static final Set<Units> INFESTOR_TYPE = new HashSet<>(Set.of(
            Units.ZERG_INFESTOR, Units.ZERG_INFESTOR_BURROWED));
    public static final Set<Units> IGNORED_TARGETS = new HashSet<>(Set.of(
            Units.ZERG_LARVA, Units.ZERG_EGG, Units.ZERG_BROODLING, Units.TERRAN_AUTO_TURRET,
            Units.ZERG_LOCUS_TMP, Units.ZERG_LOCUS_TMP_FLYING));
    public static final Set<Units> LONG_RANGE_ENEMIES = new HashSet<>(Set.of(
            Units.PROTOSS_TEMPEST, Units.PROTOSS_OBSERVER, Units.ZERG_OVERSEER,
            Units.TERRAN_RAVEN, Units.TERRAN_THOR, Units.TERRAN_THOR_AP));
    public static final Set<Abilities> BUILD_ABILITIES = new HashSet<>(Set.of(
            Abilities.BUILD_SUPPLY_DEPOT, Abilities.BUILD_REFINERY, Abilities.BUILD_COMMAND_CENTER,
            Abilities.BUILD_BARRACKS, Abilities.BUILD_ENGINEERING_BAY, Abilities.BUILD_MISSILE_TURRET,
            Abilities.BUILD_BUNKER, Abilities.BUILD_SENSOR_TOWER, Abilities.BUILD_GHOST_ACADEMY,
            Abilities.BUILD_FACTORY, Abilities.BUILD_STARPORT, Abilities.BUILD_ARMORY,
            Abilities.BUILD_FUSION_CORE));
    public static final Set<Units> NO_THREAT_ENEMY_AIR = new HashSet<>(Set.of(
            Units.ZERG_OVERLORD, Units.ZERG_OVERSEER, Units.ZERG_OVERSEER_SIEGED,
            Units.PROTOSS_OBSERVER, Units.PROTOSS_OBSERVER_SIEGED, Units.PROTOSS_ORACLE
    ));
    public static final Set<Units> CREEP_TUMOR = new HashSet<>(Set.of(
            Units.ZERG_CREEP_TUMOR, Units.ZERG_CREEP_TUMOR_BURROWED, Units.ZERG_CREEP_TUMOR_QUEEN
    ));
    public static final Set<Units> BASE_BLOCKERS = new HashSet<>(Set.of(
            Units.ZERG_CREEP_TUMOR, Units.ZERG_CREEP_TUMOR_BURROWED, Units.ZERG_CREEP_TUMOR_QUEEN,
            Units.PROTOSS_DARK_TEMPLAR, Units.TERRAN_WIDOWMINE_BURROWED, Units.PROTOSS_ORACLE_STASIS_TRAP
    ));
    public static Set<Units> enemyCommandStructures;
    public static Units enemyWorkerType;


    public static boolean hasTechToBuild(Abilities abilityType) {
        return hasTechToBuild(Bot.abilityToUnitType.get(abilityType));
    }

    public static boolean hasTechToBuild(Units unitType) { //TODO: create map of requirements
        switch (unitType) {
            case TERRAN_ORBITAL_COMMAND:
                return !GameCache.barracksList.isEmpty();
            case TERRAN_PLANETARY_FORTRESS:
                if (!getFriendlyUnitsOfType(Units.TERRAN_ENGINEERING_BAY).isEmpty()) {
                    return true;
                }
                break;
            case TERRAN_STARPORT:
                return !GameCache.factoryList.isEmpty();
        }
        return false;
    }

    public static int getNumFriendlyUnits(Units unitType, boolean includeProducing) { //includeProducing==true will make in-production command centers and refineries counted twice
        int numUnits = UnitUtils.getFriendlyUnitsOfType(unitType).size();
        if (includeProducing) {
            numUnits += numInProductionOfType(unitType);
        }
        return numUnits;
    }

    public static int getNumFriendlyUnits(Set<Units> unitTypes, boolean includeProducing) { //includeProducing==true will make in-production command centers and refineries counted twice
        int numUnits = 0;
        for (Units unitType : unitTypes) {
            numUnits += getFriendlyUnitsOfType(unitType).size();
            if (includeProducing) {
                numUnits += numInProductionOfType(unitType);
            }
        }
        return numUnits;
    }

    public static boolean canAfford(Units unitType) {
        return canAfford(unitType, GameCache.mineralBank, GameCache.gasBank, GameCache.freeSupply);
    }

    public static boolean canAfford(Units unitType, int minerals, int gas, int supply) {
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(unitType);
        int mineralCost =  unitData.getMineralCost().orElse(0);
        int gasCost = unitData.getVespeneCost().orElse(0);
        int supplyCost = unitData.getFoodRequired().orElse(0f).intValue();
        switch (unitType) {
            case TERRAN_PLANETARY_FORTRESS:
            case TERRAN_ORBITAL_COMMAND:
                mineralCost -= 400; //TODO: this is hardcoded
        }
        return minerals >= mineralCost && gas >= gasCost && supply >= supplyCost;
    }

    public static boolean isUnitTypesNearby(Alliance alliance, Units unitType, Point2d position, float distance) {
        return !getUnitsNearbyOfType(alliance, unitType, position, distance).isEmpty();
    }

    public static List<UnitInPool> getUnitsNearbyOfType(Alliance alliance, Units unitType, Point2d position, float distance) {
        try {
            return Bot.OBS.getUnits(alliance, unit -> unit.unit().getType() == unitType && unit.unit().getPosition().toPoint2d().distance(position) < distance);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public static boolean isUnitTypesNearby(Alliance alliance, Set<Units> unitTypes, Point2d position, float distance) {
        return !getUnitsNearbyOfType(alliance, unitTypes, position, distance).isEmpty();
    }

    public static List<UnitInPool> getUnitsNearbyOfType(Alliance alliance, Set<Units> unitTypes, Point2d position, float distance) {
        return Bot.OBS.getUnits(alliance, unit -> unitTypes.contains(unit.unit().getType()) && UnitUtils.getDistance(unit.unit(), position) < distance);
    }

    public static List<UnitInPool> getUnitsNearby(Alliance alliance, Point2d position, float distance) {
        return Bot.OBS.getUnits(alliance, unit -> UnitUtils.getDistance(unit.unit(), position) < distance);
    }

    public static int numRepairingScvs(Unit repairTarget) {
        return (int)getFriendlyUnitsOfType(Units.TERRAN_SCV).stream()
                .filter(scv -> !scv.getOrders().isEmpty() &&
                        scv.getOrders().get(0).getAbility() == Abilities.EFFECT_REPAIR &&
                        scv.getOrders().get(0).getTargetedUnitTag().orElse(Tag.of(0L)).equals(repairTarget.getTag()))
                .count();
    }

    public static int getHealthPercentage(Unit unit) {
        return unit.getHealth().get().intValue() * 100 / unit.getHealthMax().get().intValue();
    }

    public static int getIdealScvsToRepair(Unit unit) {
        int pfHealthPercentage;

        if (GameCache.wallStructures.contains(unit)) {
            pfHealthPercentage = getHealthPercentage(unit);
            if (pfHealthPercentage > 75) {
                return 1;
            }
            else  {
                return 2;
            }
        }
        switch ((Units)unit.getType()) {
            case TERRAN_PLANETARY_FORTRESS:
                pfHealthPercentage = getHealthPercentage(unit);
                if (pfHealthPercentage > 95) {
                    return 5;
                }
                else if (pfHealthPercentage > 70) {
                    return 10;
                }
                else {
                    return Integer.MAX_VALUE;
                }
            case TERRAN_MISSILE_TURRET: case TERRAN_BUNKER:
                return 6;
            case TERRAN_LIBERATOR_AG: case TERRAN_SIEGE_TANK_SIEGED:
                return 2;
            default: //other burning structures
                return 1;
        }
    }

    public static float getAirAttackRange(Unit unit) {
        return getAttackRange(unit, Weapon.TargetType.AIR);
    }

    public static float getGroundAttackRange(Unit unit) {
        return getAttackRange(unit, Weapon.TargetType.GROUND);
    }

    public static float getAttackRange(Unit unit, Weapon.TargetType targetType) {
        float attackRange = 0;
        switch ((Units)unit.getType()) { //these types do not have a Weapon in the api
            case TERRAN_BUNKER:
                attackRange = 8;
                break;
            case TERRAN_BATTLECRUISER: case PROTOSS_HIGH_TEMPLAR: case PROTOSS_VOIDRAY:
                attackRange = 6;
                break;
            case PROTOSS_SENTRY:
                attackRange = 5;
                break;
        }
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.getType()).getWeapons();
        for (Weapon weapon : weapons) {
            if (weapon.getTargetType() == targetType || weapon.getTargetType() == Weapon.TargetType.ANY) {
                attackRange = weapon.getRange() + unit.getRadius();
            }
        }
        if (attackRange > 0) {
            attackRange += unit.getRadius();
        }
        return attackRange;
    }

    public static float getDistance(Unit unit1, Unit unit2) {
        return getDistance(unit1, unit2.getPosition().toPoint2d());
    }

    public static float getDistance(Unit unit1, Point2d point) {
        return (float)unit1.getPosition().toPoint2d().distance(point);
    }

    public static List<Unit> toUnitList(List<UnitInPool> unitInPoolList) {
        return unitInPoolList.stream().map(UnitInPool::unit).collect(Collectors.toList());
    }

    public static boolean hasDecloakBuff(Unit unit) {
        Set<Buff> buffs = unit.getBuffs();
        return buffs.contains(Buffs.EMP_DECLOAK) || buffs.contains(Buffs.ORACLE_REVELATION) || buffs.contains(Buffs.FUNGAL_GROWTH);
    }

    public static boolean isVisible(UnitInPool unitInPool) {
        return unitInPool.getLastSeenGameLoop() == Time.nowFrames();
    }

    public static boolean canMove(UnitType unitType) {
        return Bot.OBS.getUnitTypeData(false).get(unitType).getMovementSpeed().orElse(0f) > 0f;
    }

    public static Unit getClosestEnemyOfType(Units unitType, Point2d pos) {
        List<UnitInPool> enemyList = getUnitsNearbyOfType(Alliance.ENEMY, unitType, pos, Integer.MAX_VALUE);
        if (enemyList.isEmpty()) {
            return null;
        }
        UnitInPool closestEnemy = enemyList.remove(0);
        double closestDistance = closestEnemy.unit().getPosition().toPoint2d().distance(pos);
        for (UnitInPool enemy : enemyList) {
            double distance = enemy.unit().getPosition().toPoint2d().distance(pos);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEnemy = enemy;
            }
        }
        return closestEnemy.unit();
    }

    public static UnitInPool getClosestEnemyUnitOfType(Units unitType, Point2d pos) {
        return getEnemyUnitsOfType(unitType).stream()
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), ArmyManager.retreatPos))).orElse(null);
    }

    public static Unit getClosestUnitOfType(Alliance alliance, Units unitType, Point2d pos) {
        List<UnitInPool> unitList = getUnitsNearbyOfType(alliance, unitType, pos, Integer.MAX_VALUE);
        UnitInPool result = getClosestUnitFromUnitList(unitList, pos);
        return (result == null) ? null : result.unit();
    }

    public static Unit getClosestUnitOfType(Alliance alliance, Set<Units> unitType, Point2d pos) {
        List<UnitInPool> unitList = getUnitsNearbyOfType(alliance, unitType, pos, Integer.MAX_VALUE);
        UnitInPool result = getClosestUnitFromUnitList(unitList, pos);
        return (result == null) ? null : result.unit();

    }

    public static UnitInPool getClosestUnitFromUnitList(List<UnitInPool> unitList, Point2d pos) {
        return unitList.stream()
                .filter(u -> UnitUtils.isVisible(u))
                .min(Comparator.comparing(u -> getDistance(u.unit(), pos)))
                .orElse(null);
    }

    public static void removeDeadUnits(List<UnitInPool> unitList) {
        if (unitList != null) {
            unitList.removeIf(unitInPool -> !unitInPool.isAlive());
        }
    }

    public static boolean isStructure(Units unitType) {
        return STRUCTURE_TYPE.contains(unitType);
    }

    public static boolean canScan() {
        List<Unit> orbitals = getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
        return orbitals.stream().anyMatch(unit -> unit.getEnergy().orElse(0f) >= 50);
    }

    public static List<UnitInPool> getEnemyUnitsOfType(Units unitType) {
        return GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList());
    }

    public static List<UnitInPool> getEnemyUnitsOfTypes(Collection<Units> unitTypes) {
        List<UnitInPool> result = new ArrayList<>();
        for (Units unitType : unitTypes) {
            if (!GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList()).isEmpty()) {
                result.addAll(GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList()));
            }
        }
        return result;
    }


    public static List<Unit> getVisibleEnemyUnitsOfType(Units unitType) {
        return GameCache.allVisibleEnemiesMap.getOrDefault(unitType, Collections.emptyList());
    }

    public static List<Unit> getVisibleEnemyUnitsOfType(Set<Units> unitTypes) {
        List<Unit> result = new ArrayList<>();
        for (Units unitType : unitTypes) {
            List<Unit> enemyOfTypeList = GameCache.allVisibleEnemiesMap.getOrDefault(unitType, Collections.emptyList());
            if (!enemyOfTypeList.isEmpty()) {
                result.addAll(enemyOfTypeList);
            }
        }
        return result;
    }

    public static List<Unit> getFriendlyUnitsOfType(Units unitType) {
        return GameCache.allFriendliesMap.getOrDefault(unitType, Collections.emptyList());
    }

    public static int numInProductionOfType(Units unitType) {
        return GameCache.inProductionMap.getOrDefault(unitType, 0);
    }

    public static void queueUpAttackOfEveryBase(List<Unit> units) {
        Bot.ACTION.unitCommand(units, Abilities.ATTACK, LocationConstants.baseLocations.get(2), false);
        for (int i=3; i<LocationConstants.baseLocations.size(); i++) {
            Point2d basePos = LocationConstants.baseLocations.get(i);
            Bot.ACTION.unitCommand(units, Abilities.ATTACK, LocationConstants.baseLocations.get(i), true);
        }
    }

    public static boolean doesAttackGround(Unit unit) {
        return Bot.OBS.getUnitTypeData(false).get(unit.getType())
                .getWeapons().stream().anyMatch(weapon -> weapon.getTargetType() == Weapon.TargetType.GROUND || weapon.getTargetType() == Weapon.TargetType.GROUND);
    }

    public static boolean isCarryingResources(Unit worker) {
        return worker.getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS) || worker.getBuffs().contains(Buffs.CARRY_HIGH_YIELD_MINERAL_FIELD_MINERALS) ||
                worker.getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS) || worker.getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS_PROTOSS) ||
                worker.getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS_ZERG);
    }

    public static Unit getSafestMineralPatch() {
        List<Unit> mineralPatches = GameCache.baseList.stream()
                .filter(base -> base.isMyBase() && !base.getMineralPatches().isEmpty())
                .findFirst()
                .map(Base::getMineralPatches)
                .orElse(null);
        if (mineralPatches == null) {
            return null;
        }
        else {
            return mineralPatches.get(0);
        }
    }

    public static boolean isAttacking(Unit unit, Unit target) {
        return !unit.getOrders().isEmpty() &&
                unit.getOrders().get(0).getAbility() == Abilities.ATTACK &&
                target.getTag().equals(unit.getOrders().get(0).getTargetedUnitTag().orElse(null));
    }

    public static boolean hasOrderTarget(Unit unit) {
        return !unit.getOrders().isEmpty() &&
                unit.getOrders().get(0).getTargetedUnitTag().isPresent();
    }

    public static boolean isWallUnderAttack() {
        return GameCache.wallStructures.stream().anyMatch(unit -> unit.getType() == Units.TERRAN_SUPPLY_DEPOT); //if depot is raised then unsafe to expand
    }

    public static float getStructureRadius(Abilities buildAction) {
        StructureSize size = getSize(buildAction);
        switch (size) {
            case _1x1:
                return 0.3f; //0.5 actual
            case _2x2:
                return 0.7f; //1 actual
            case _3x3:
                return 1.1f; //1.5 actual
            default: //_5x5
                return 2.2f; //2.5 actual
        }
    }

    public static StructureSize getSize(Abilities buildAction) {
        switch (buildAction) {
            case BUILD_COMMAND_CENTER:
                return StructureSize._5x5;
            case BUILD_ENGINEERING_BAY: case BUILD_BARRACKS: case BUILD_BUNKER: case BUILD_ARMORY: case BUILD_FACTORY:
            case BUILD_STARPORT: case BUILD_FUSION_CORE: case BUILD_GHOST_ACADEMY:
                return StructureSize._3x3;
            case BUILD_MISSILE_TURRET: case BUILD_SUPPLY_DEPOT:
                return StructureSize._2x2;
            default: //case BUILD_SENSOR_TOWER:
                return StructureSize._1x1;
        }
    }

    public static boolean isUnitTrapped(Unit unit) {
        boolean cantPathToMain = Bot.QUERY.pathingDistance(unit, LocationConstants.insideMainWall) == 0;

        //check path to front of natural if main base's wall is closed
        if (cantPathToMain && isWallUnderAttack()) {
            Point2d atNatPos = Position.towards(GameCache.baseList.get(1).getCcPos(),
                    GameCache.baseList.get(1).getResourceMidPoint(), 4f);
            return Bot.QUERY.pathingDistance(unit, atNatPos) == 0;
        }

        return cantPathToMain;
    }

    public static boolean isExpansionCreepBlocked(Point2d expansionPos) {
        boolean b = Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() + 2.5f, expansionPos.getY() + 2.5f)) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() - 2.5f, expansionPos.getY() - 2.5f)) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() + 2.5f, expansionPos.getY() - 2.5f)) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() - 2.5f, expansionPos.getY() + 2.5f));
        return b;
    }

    public static List<UnitInPool> getEnemyTargetsNear(Unit unit, float range) {
        return getEnemyTargetsNear(unit.getPosition().toPoint2d(), range);
    }

    public static List<UnitInPool> getEnemyTargetsNear(Point2d pos, float range) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                getDistance(enemy.unit(), pos) <= range &&
                !IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                !enemy.unit().getHallucination().orElse(false) &&
                !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD) &&
                enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                enemy.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED);
    }
}
