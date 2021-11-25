package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.ArmyManager;
import com.ketroc.managers.BuildManager;
import com.ketroc.managers.StructureSize;
import com.ketroc.models.Base;
import com.ketroc.models.Cost;
import com.ketroc.models.Ignored;
import com.ketroc.models.StructureScv;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseUnit;
import com.ketroc.strategies.Strategy;

import java.util.*;
import java.util.function.Predicate;
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

    public static final Set<Units> MINERAL_NODE_TYPE_SMALL = new HashSet<>(Set.of(
            Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD750,
            Units.NEUTRAL_PURIFIER_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750,
            Units.NEUTRAL_LAB_MINERAL_FIELD750, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750));

    public static final Set<Units> COMMAND_STRUCTURE_TYPE_TERRAN = new HashSet<>(Set.of(
            Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS,
            Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING));

    public static final Set<Units> COMMAND_STRUCTURE_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS,
            Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING,
            Units.PROTOSS_NEXUS, Units.ZERG_HATCHERY, Units.ZERG_LAIR, Units.ZERG_HIVE));

    public static final Set<Units> SUPPLY_DEPOT_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_SUPPLY_DEPOT, Units.TERRAN_SUPPLY_DEPOT_LOWERED));

    public static final Set<Units> COMMAND_CENTER_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_COMMAND_CENTER, Units.TERRAN_COMMAND_CENTER_FLYING));

    public static final Set<Units> ORBITAL_COMMAND_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_ORBITAL_COMMAND_FLYING));

    public static final Set<Units> BARRACKS_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_BARRACKS, Units.TERRAN_BARRACKS_FLYING));

    public static final Set<Units> FACTORY_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_FACTORY, Units.TERRAN_FACTORY_FLYING));

    public static final Set<Units> STARPORT_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_STARPORT, Units.TERRAN_STARPORT_FLYING));

    public static final Set<Units> HELLION_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_HELLION, Units.TERRAN_HELLION_TANK));

    public static final Set<Units> WIDOW_MINE_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_WIDOWMINE, Units.TERRAN_WIDOWMINE_BURROWED));

    public static final Set<Units> SIEGE_TANK_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_SIEGE_TANK, Units.TERRAN_SIEGE_TANK_SIEGED));

    public static final Set<Units> LIBERATOR_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_LIBERATOR, Units.TERRAN_LIBERATOR_AG));

    public static final Set<Units> VIKING_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_VIKING_ASSAULT));

    public static final Set<Units> THOR_TYPE = new HashSet<>(Set.of(
            Units.TERRAN_THOR, Units.TERRAN_THOR_AP));

    public static final Set<Units> OBSERVER_TYPE = new HashSet<>(Set.of(
            Units.PROTOSS_OBSERVER, Units.PROTOSS_OBSERVER_SIEGED));

    public static final Set<Units> OVERLORD_TYPE = new HashSet<>(Set.of(
            Units.ZERG_OVERLORD, Units.ZERG_OVERSEER, Units.ZERG_OVERLORD_COCOON,
            Units.ZERG_OVERLORD_TRANSPORT, Units.ZERG_OVERSEER_SIEGED));

    public static final Set<Units> DETECTION_REQUIRED_TYPE = new HashSet<>(Set.of(
            Units.PROTOSS_OBSERVER, Units.PROTOSS_OBSERVER_SIEGED, Units.TERRAN_BANSHEE, Units.TERRAN_GHOST,
            Units.PROTOSS_DARK_TEMPLAR, Units.ZERG_LURKER_MP, Units.PROTOSS_MOTHERSHIP));

    public static final Set<Units> REPAIR_BAY_TYPES = new HashSet<>(Set.of(
            Units.TERRAN_CYCLONE, Units.TERRAN_HELLION, Units.TERRAN_WIDOWMINE, Units.TERRAN_WIDOWMINE_BURROWED));


//    public static final Set<Units> STRUCTURE_TYPE = new HashSet<>();
//    public static final Set<Units> STRUCTURE_TYPE = new HashSet<>(Set.of(
//            Units.TERRAN_FUSION_CORE, Units.TERRAN_SUPPLY_DEPOT, Units.TERRAN_SUPPLY_DEPOT_LOWERED,
//            Units.TERRAN_ENGINEERING_BAY, Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND,
//            Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING,
//            Units.TERRAN_ARMORY, Units.TERRAN_MISSILE_TURRET, Units.TERRAN_BUNKER,
//            Units.TERRAN_GHOST_ACADEMY, Units.TERRAN_SENSOR_TOWER, Units.TERRAN_BARRACKS,
//            Units.TERRAN_BARRACKS_FLYING, Units.TERRAN_FACTORY, Units.TERRAN_FACTORY_FLYING,
//            Units.TERRAN_STARPORT, Units.TERRAN_STARPORT_FLYING, Units.TERRAN_REFINERY,
//            Units.TERRAN_REFINERY_RICH, Units.TERRAN_BARRACKS_TECHLAB, Units.TERRAN_FACTORY_TECHLAB,
//            Units.TERRAN_STARPORT_TECHLAB, Units.TERRAN_TECHLAB, Units.TERRAN_BARRACKS_REACTOR,
//            Units.TERRAN_FACTORY_REACTOR, Units.TERRAN_STARPORT_REACTOR, Units.TERRAN_REACTOR));

    public static Set<Units> EVIDENCE_OF_AIR = new HashSet<>(Set.of(
            Units.TERRAN_FUSION_CORE, Units.TERRAN_BANSHEE, Units.TERRAN_BATTLECRUISER,
            Units.TERRAN_LIBERATOR, Units.TERRAN_LIBERATOR_AG,
            Units.PROTOSS_STARGATE, Units.PROTOSS_FLEET_BEACON, Units.PROTOSS_TEMPEST,
            Units.PROTOSS_ORACLE, Units.PROTOSS_ORACLE_STASIS_TRAP, Units.PROTOSS_VOIDRAY,
            Units.ZERG_SPIRE, Units.ZERG_GREATER_SPIRE, Units.ZERG_MUTALISK,
            Units.ZERG_CORRUPTOR, Units.ZERG_BROODLORD, Units.ZERG_BROODLORD_COCOON));

    public static final Set<Units> INFESTOR_TYPE = new HashSet<>(Set.of(
            Units.ZERG_INFESTOR, Units.ZERG_INFESTOR_BURROWED));

    public static final Set<Units> IGNORED_TARGETS = new HashSet<>(Set.of(
            Units.ZERG_LARVA, Units.ZERG_EGG, Units.ZERG_BROODLING, Units.TERRAN_AUTO_TURRET,
            Units.ZERG_LOCUS_TMP, Units.ZERG_LOCUS_TMP_FLYING, Units.PROTOSS_DISRUPTOR_PHASED,
            Units.PROTOSS_ADEPT_PHASE_SHIFT, Units.TERRAN_KD8CHARGE, Units.INVALID));

    public static final Set<Units> UNTARGETTABLES = new HashSet<>(Set.of(
            Units.PROTOSS_DISRUPTOR_PHASED, Units.PROTOSS_ADEPT_PHASE_SHIFT, Units.TERRAN_KD8CHARGE, Units.INVALID));

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
            Units.PROTOSS_OBSERVER, Units.PROTOSS_OBSERVER_SIEGED));

    public static final Set<Units> VIKING_PEEL_TARGET_TYPES = new HashSet<>(Set.of(
            Units.ZERG_OVERLORD, Units.ZERG_TRANSPORT_OVERLORD_COCOON, Units.ZERG_OVERSEER, Units.ZERG_OVERSEER_SIEGED,
            Units.ZERG_OVERLORD_TRANSPORT, Units.ZERG_OVERLORD_COCOON, Units.ZERG_MUTALISK,
            Units.PROTOSS_OBSERVER_SIEGED, Units.PROTOSS_ORACLE, Units.PROTOSS_WARP_PRISM,
            Units.PROTOSS_WARP_PRISM_PHASING, //Units.PROTOSS_OBSERVER, viking dive code seems better atm
            Units.TERRAN_MEDIVAC, Units.TERRAN_BANSHEE, Units.TERRAN_LIBERATOR, Units.TERRAN_LIBERATOR_AG));

    public static final Set<Units> DETECTOR_TYPES = new HashSet<>(Set.of(
            Units.TERRAN_RAVEN, Units.TERRAN_MISSILE_TURRET, Units.PROTOSS_OBSERVER, Units.PROTOSS_OBSERVER_SIEGED,
            Units.PROTOSS_PHOTON_CANNON, Units.ZERG_OVERSEER, Units.ZERG_OVERSEER_SIEGED, Units.ZERG_SPORE_CRAWLER));

    public static final Set<Units> MOBILE_DETECTOR_TYPES = new HashSet<>(Set.of(
            Units.TERRAN_RAVEN, Units.PROTOSS_OBSERVER, Units.PROTOSS_OBSERVER_SIEGED,
            Units.ZERG_OVERSEER, Units.ZERG_OVERSEER_SIEGED));

    public static final Set<Units> CREEP_TUMOR_TYPES = new HashSet<>(Set.of(
            Units.ZERG_CREEP_TUMOR, Units.ZERG_CREEP_TUMOR_BURROWED, Units.ZERG_CREEP_TUMOR_QUEEN));

    public static final Set<Units> BASE_BLOCKERS = new HashSet<>(Set.of(
            Units.ZERG_CREEP_TUMOR, Units.ZERG_CREEP_TUMOR_BURROWED, Units.ZERG_CREEP_TUMOR_QUEEN,
            Units.PROTOSS_DARK_TEMPLAR, Units.TERRAN_WIDOWMINE_BURROWED, Units.PROTOSS_ORACLE_STASIS_TRAP,
            Units.ZERG_ZERGLING_BURROWED));

    public static final Set<Units> DESTRUCTIBLES = new HashSet<>(Set.of(
            Units.NEUTRAL_UNBUILDABLE_BRICKS_DESTRUCTIBLE, Units.NEUTRAL_UNBUILDABLE_PLATES_DESTRUCTIBLE,
            Units.NEUTRAL_DESTRUCTIBLE_DEBRIS6X6, Units.NEUTRAL_DESTRUCTIBLE_ROCK6X6,
            Units.NEUTRAL_DESTRUCTIBLE_DEBRIS_RAMP_DIAGONAL_HUGE_BL_UR, Units.NEUTRAL_DESTRUCTIBLE_DEBRIS_RAMP_DIAGONAL_HUGE_UL_BR,
            Units.NEUTRAL_DESTRUCTIBLE_ROCK_EX1_DIAGONAL_HUGE_BL_UR));

    public static Set<Units> enemyCommandStructures;
    public static Units enemyWorkerType;

    //includes units in Ignored List
    public static int numMyUnits(Units unitType, boolean includeProducing) {
        int numUnits = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == unitType).size();
        if (includeProducing) {
            if (isStructure(unitType)) {
                numUnits += numStructuresProducingOrQueued(unitType);
            } else {
                numUnits += numInProductionOfType(unitType);
            }
        }
        if (unitType == Units.TERRAN_MARINE) { //FIXME: assuming bunkers only contain marines
            numUnits += UnitUtils.getMyUnitsOfType(Units.TERRAN_BUNKER).stream()
                    .mapToInt(bunker -> bunker.getCargoSpaceTaken().orElse(0))
                    .sum();
        }
        return numUnits;
    }

    public static int numMyUnits(Set<Units> unitTypes, boolean includeProducing) { //includeProducing==true will make in-production command centers and refineries counted twice
        int numUnits = 0;
        for (Units unitType : unitTypes) {
            //numUnits += getFriendlyUnitsOfType(unitType).size();
            numUnits += Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == unitType && u.unit().getBuildProgress() == 1).size();
            if (includeProducing) {
                if (isStructure(unitType)) {
                    numUnits += numStructuresProducingOrQueued(unitType);
                } else {
                    numUnits += numInProductionOfType(unitType);
                }
            }
        }
        return numUnits;
    }

    public static int numStructuresProducingOrQueued(Units unitType) {
        return StructureScv.numInProductionOfType(unitType) + Purchase.numStructuresQueuedOfType(unitType);
    }

    public static boolean canAfford(Units unitType) {
        return canAfford(unitType, false);
    }

    public static boolean canAfford(Units unitType, boolean doIgnorePurchaseQueueSaving) {
        return canAfford(unitType,
                doIgnorePurchaseQueueSaving ? Bot.OBS.getMinerals() : GameCache.mineralBank,
                doIgnorePurchaseQueueSaving ? Bot.OBS.getVespene() : GameCache.gasBank,
                GameCache.freeSupply);
    }

    public static boolean canAfford(Units unitType, int minerals, int gas, int supply) {
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(unitType);
        int mineralCost = unitData.getMineralCost().orElse(0);
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
            return Bot.OBS.getUnits(alliance, unit ->
                    unit.unit().getType() == unitType &&
                            UnitUtils.getDistance(unit.unit(), position) < distance);
        } catch (Exception e) {
            Error.onException(e);
        }
        return new ArrayList<>();
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
        return (int) getMyUnitsOfType(Units.TERRAN_SCV).stream()
                .filter(scv ->
                        ActionIssued.getCurOrder(scv).stream()
                                .anyMatch(curAction -> curAction.ability == Abilities.EFFECT_REPAIR &&
                                        repairTarget.getTag().equals(curAction.targetTag)) ||
                                scv.getOrders().stream() //TODO: try without this?? (realtime issue??)
                                        .anyMatch(order -> order.getAbility() == Abilities.EFFECT_REPAIR &&
                                                repairTarget.getTag().equals(order.getTargetedUnitTag().orElse(null))))
                .count();
    }

    public static float getHealthPercentage(Unit unit) {
        return unit.getHealth().get() * 100 / unit.getHealthMax().get();
    }

    public static int numIdealScvsToRepair(Unit unit) {
        float structureHealth = getHealthPercentage(unit);
        if (structureHealth == 100) {
            return 0;
        }

        if (GameCache.wallStructures.contains(unit)) {
            if (structureHealth > 75) {
                return (Strategy.WALL_OFF_IMMEDIATELY) ? 2 : 1;
            } else {
                return (Strategy.WALL_OFF_IMMEDIATELY) ? 3 : 2;
            }
        }
        switch ((Units) unit.getType()) {
            case TERRAN_COMMAND_CENTER:
                if (UnitUtils.getOrder(unit) != Abilities.MORPH_PLANETARY_FORTRESS) {
                    return 1;
                } //else continue into PF case
            case TERRAN_PLANETARY_FORTRESS:
                return 2 * (int) Math.ceil((100 - structureHealth) / 10) + 1; //90-100% = 1scv, 80-90% = 3scvs, 70-80% = 5scvs, etc
            case TERRAN_MISSILE_TURRET:
                return (InfluenceMaps.getThreatToStructure(unit) != 0 ||
                        InfluenceMaps.getValue(InfluenceMaps.enemyInMissileTurretRange, unit.getPosition().toPoint2d()))
                        ? 6 : 0;
            case TERRAN_LIBERATOR_AG:
            case TERRAN_SIEGE_TANK_SIEGED:
                return 2;
            case TERRAN_BUNKER:
                return 3;
            default: //other burning structures
                return InfluenceMaps.getThreatToStructure(unit) == 0 ? 1 : 0;
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
        if (unit.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE)) {
            return 0;
        }
        if (unit.getType().toString().contains("CHANGELING")) {
            return 0;
        }
        switch ((Units) unit.getType()) { //these types do not have a Weapon in the api
            case TERRAN_BUNKER:
                attackRange = Strategy.DO_IGNORE_BUNKERS ? 0 : 6;
                break;
            case TERRAN_BATTLECRUISER:
            case PROTOSS_HIGH_TEMPLAR:
            case PROTOSS_VOIDRAY:
            case TERRAN_CYCLONE:
                attackRange = 6;
                break;
            case PROTOSS_SENTRY:
            case TERRAN_WIDOWMINE_BURROWED:
                attackRange = 5;
                break;
            case PROTOSS_ORACLE:
                if (targetType == Weapon.TargetType.GROUND && unit.getBuffs().contains(Buffs.ORACLE_WEAPON)) {
                    attackRange = 4;
                }
                break;
            case ZERG_BANELING:
            case ZERG_BANELING_BURROWED:
                if (targetType == Weapon.TargetType.GROUND) {
                    attackRange = 2; //real range is 0.25 (2.2splash)
                }
                break;
            case TERRAN_PLANETARY_FORTRESS:
                if (targetType == Weapon.TargetType.GROUND) {
                    attackRange = (unit.getAlliance() == Alliance.SELF &&
                            Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) ? 7 : 6;
                }
                break;
            case TERRAN_MISSILE_TURRET:
                if (targetType == Weapon.TargetType.AIR) {
                    attackRange = (unit.getAlliance() == Alliance.SELF &&
                            Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) ? 8 : 7;
                }
                break;
            default:
                attackRange = Bot.OBS.getUnitTypeData(false).get(unit.getType()).getWeapons().stream()
                        .filter(weapon -> weapon.getTargetType() == targetType ||
                                weapon.getTargetType() == Weapon.TargetType.ANY)
                        .findFirst()
                        .map(Weapon::getRange)
                        .orElse(0f);
                break;
        }
        //make melee units 1.5 range
        if (attackRange > 0 && attackRange < 1.5) {
            attackRange = 1.5f;
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
        return (float) unit1.getPosition().toPoint2d().distance(point);
    }

    public static List<Unit> toUnitList(List<UnitInPool> unitInPoolList) {
        return unitInPoolList.stream()
                .map(UnitInPool::unit)
                .collect(Collectors.toList());
    }

    public static boolean hasDecloakBuff(Unit unit) {
        Set<Buff> buffs = unit.getBuffs();
        return buffs.contains(Buffs.EMP_DECLOAK) || buffs.contains(Buffs.ORACLE_REVELATION) || buffs.contains(Buffs.FUNGAL_GROWTH);
    }

    public static boolean isInFogOfWar(UnitInPool unitInPool) {
        return unitInPool.isAlive() && unitInPool.getLastSeenGameLoop() != Time.nowFrames();
    }

    //needed cuz high ground enemy attackers aren't snapshots
    public static boolean isSnapshot(Unit unit) {
        if (unit.getDisplayType() == DisplayType.SNAPSHOT) {
            return true;
        }
        float unitRadius = unit.getRadius();
        Point2d unitPos = unit.getPosition().toPoint2d();
        return Bot.OBS.getVisibility(unitPos.add(unitRadius, 0)) != Visibility.VISIBLE &&
                Bot.OBS.getVisibility(unitPos.add(unitRadius * -1, 0)) != Visibility.VISIBLE &&
                Bot.OBS.getVisibility(unitPos.add(0, unitRadius)) != Visibility.VISIBLE &&
                Bot.OBS.getVisibility(unitPos.add(0, unitRadius * -1)) != Visibility.VISIBLE;
    }

    public static boolean canMove(Unit unit) {
        return !unit.getBuffs().contains(Buffs.ORACLE_STASIS_TRAP_TARGET) &&
                !unit.getBuffs().contains(Buffs.NEURAL_PARASITE) &&
                Bot.OBS.getUnitTypeData(false).get(unit.getType()).getMovementSpeed().orElse(0f) > 0f;
    }

    public static boolean canCast(Unit unit) {
        return !unit.getBuffs().contains(Buffs.ORACLE_STASIS_TRAP_TARGET) &&
                !unit.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE) &&
                !unit.getBuffs().contains(Buffs.NEURAL_PARASITE) &&
                unit.getEnergy().isPresent();
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
                .filter(u -> !UnitUtils.isInFogOfWar(u))
                .min(Comparator.comparing(u -> getDistance(u.unit(), pos)))
                .orElse(null);
    }

    public static void removeDeadUnits(List<UnitInPool> unitList) {
        if (unitList != null) {
            unitList.removeIf(unitInPool -> !unitInPool.isAlive());
        }
    }

    public static boolean isStructure(UnitType unitType) {
        return Bot.OBS.getUnitTypeData(false).get(unitType).getAttributes().contains(UnitAttribute.STRUCTURE) &&
                unitType != Units.TERRAN_AUTO_TURRET;
    }

    public static boolean canScan() {
        List<Unit> orbitals = getMyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
        return orbitals.stream().anyMatch(unit -> unit.getEnergy().orElse(0f) >= 50);
    }

    public static List<UnitInPool> getEnemyUnitsOfType(Units unitType) {
        return GameCache.allEnemiesMap.getOrDefault(unitType, new ArrayList<>());
    }

    public static List<UnitInPool> getEnemyUnitsOfType(Collection<Units> unitTypes) {
        List<UnitInPool> result = new ArrayList<>();
        for (Units unitType : unitTypes) {
            if (!GameCache.allEnemiesMap.getOrDefault(unitType, new ArrayList<>()).isEmpty()) {
                result.addAll(GameCache.allEnemiesMap.getOrDefault(unitType, new ArrayList<>()));
            }
        }
        return result;
    }


    public static List<Unit> getVisibleEnemyUnitsOfType(Units unitType) {
        return GameCache.allVisibleEnemiesMap.getOrDefault(unitType, new ArrayList<>());
    }

    public static List<Unit> getVisibleEnemyUnitsOfType(Set<Units> unitTypes) {
        List<Unit> result = new ArrayList<>();
        for (Units unitType : unitTypes) {
            List<Unit> enemyOfTypeList = GameCache.allVisibleEnemiesMap.getOrDefault(unitType, new ArrayList<>());
            if (!enemyOfTypeList.isEmpty()) {
                result.addAll(enemyOfTypeList);
            }
        }
        return result;
    }

    public static List<Unit> getMyUnitsOfType(Units unitType) {
        return GameCache.allMyUnitsMap.getOrDefault(unitType, new ArrayList<>());
    }

    public static List<Unit> getMyUnitsOfType(Set<Units> unitTypes) {
        List<Unit> result = new ArrayList<>();
        for (Units unitType : unitTypes) {
            List<Unit> myUnitsOfType = GameCache.allMyUnitsMap.getOrDefault(unitType, new ArrayList<>());
            if (!myUnitsOfType.isEmpty()) {
                result.addAll(myUnitsOfType);
            }
        }
        return result;
    }

    public static int numInProductionOfType(Units unitType) {
        return GameCache.inProductionMap.getOrDefault(unitType, 0);
    }

    public static void queueUpAttackOfEveryBase(List<Unit> units) {
        ActionHelper.unitCommand(units, Abilities.ATTACK, LocationConstants.baseLocations.get(2), false);
        for (int i = 3; i < LocationConstants.baseLocations.size(); i++) {
            Point2d basePos = LocationConstants.baseLocations.get(i);
            ActionHelper.unitCommand(units, Abilities.ATTACK, LocationConstants.baseLocations.get(i), true);
        }
    }

    public static boolean canAttackGround(Unit unit) {
        if (unit.getType().toString().contains("CHANGELING")) {
            return false;
        }
        switch ((Units) unit.getType()) {
            case TERRAN_WIDOWMINE_BURROWED:
            case ZERG_BANELING:
            case ZERG_BANELING_BURROWED:
            case PROTOSS_DISRUPTOR_PHASED:
                return true;
            case TERRAN_BUNKER:
                return !Strategy.DO_IGNORE_BUNKERS;
        }
        return Bot.OBS.getUnitTypeData(false).get(unit.getType())
                .getWeapons().stream().anyMatch(weapon -> weapon.getTargetType() == Weapon.TargetType.GROUND || weapon.getTargetType() == Weapon.TargetType.ANY);
    }

    public static boolean canAttackAir(Unit unit) {
        if (unit.getType().toString().contains("CHANGELING")) {
            return false;
        }
        switch ((Units) unit.getType()) {
            case TERRAN_WIDOWMINE_BURROWED:
                return true;
            case TERRAN_BUNKER:
                return !Strategy.DO_IGNORE_BUNKERS;
        }
        return Bot.OBS.getUnitTypeData(false).get(unit.getType())
                .getWeapons().stream().anyMatch(weapon -> weapon.getTargetType() == Weapon.TargetType.AIR || weapon.getTargetType() == Weapon.TargetType.ANY);
    }

    public static boolean isCarryingResources(Unit worker) {
        return worker.getBuffs().stream().anyMatch(buff -> buff.toString().startsWith("CARRY_"));
    }

    public static Unit getSafestMineralPatch() {
        List<Unit> mineralPatches = GameCache.baseList.stream()
                .filter(base -> base.isMyBase() && !base.getMineralPatchUnits().isEmpty())
                .findFirst()
                .map(Base::getMineralPatchUnits)
                .orElse(null);
        if (mineralPatches == null) {
            return null;
        } else {
            return mineralPatches.get(0);
        }
    }

    public static boolean isAttacking(Unit unit, Unit target) {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(order -> order.ability == Abilities.ATTACK && target.getTag().equals(order.targetTag));
    }

    public static boolean isAttacking(Unit unit) {
        return ActionIssued.getCurOrder(unit).stream().anyMatch(order -> order.ability == Abilities.ATTACK);
    }

    public static boolean hasOrderTarget(Unit unit) {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(order -> order.targetTag != null);
    }

    public static boolean isWallUnderAttack() { //TODO: make more accurate
        //if depot is raised then unsafe
        return !GameCache.wallStructures.isEmpty() && GameCache.wallStructures.stream().allMatch(structure ->
                InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, structure.getPosition().toPoint2d()));
    }

    public static float getStructureRadius(Units structureType) {
        StructureSize size = getSize(structureType);
        switch (size) {
            case _1x1:
                return 0.5f;
            case _2x2:
                return 1;
            case _3x3:
                return 1.5f;
            default: //_5x5
                return 2.5f;
        }
    }

    public static StructureSize getSize(Units structureType) {
        switch (structureType) {
            case TERRAN_COMMAND_CENTER:
            case TERRAN_ORBITAL_COMMAND:
            case TERRAN_PLANETARY_FORTRESS:
            case TERRAN_COMMAND_CENTER_FLYING:
            case TERRAN_ORBITAL_COMMAND_FLYING:
                return StructureSize._5x5;
            case TERRAN_ENGINEERING_BAY:
            case TERRAN_BARRACKS:
            case TERRAN_BUNKER:
            case TERRAN_ARMORY:
            case TERRAN_FACTORY:
            case TERRAN_STARPORT:
            case TERRAN_FUSION_CORE:
            case TERRAN_GHOST_ACADEMY:
            case TERRAN_BARRACKS_FLYING:
            case TERRAN_FACTORY_FLYING:
            case TERRAN_STARPORT_FLYING:
                return StructureSize._3x3;
            case TERRAN_MISSILE_TURRET:
            case TERRAN_SUPPLY_DEPOT:
            case TERRAN_TECHLAB:
            case TERRAN_BARRACKS_TECHLAB:
            case TERRAN_FACTORY_TECHLAB:
            case TERRAN_STARPORT_TECHLAB:
            case TERRAN_REACTOR:
            case TERRAN_BARRACKS_REACTOR:
            case TERRAN_FACTORY_REACTOR:
            case TERRAN_STARPORT_REACTOR:
            case TERRAN_AUTO_TURRET:
            case NEUTRAL_XELNAGA_TOWER:
                return StructureSize._2x2;
            default: //case TERRAN_SENSOR_TOWER:
                return StructureSize._1x1;
        }
    }

    public static StructureSize getSize(Unit structure) {
        float structureSize = structure.getRadius();
        if (structureSize > 2.75) {
            return StructureSize._6x6;
        } else if (structureSize > 2.25) {
            return StructureSize._5x5;
        } else if (structureSize > 1.75) {
            return StructureSize._4x4;
        } else if (structureSize > 1.25) {
            return StructureSize._3x3;
        } else if (structureSize > 0.75) {
            return StructureSize._2x2;
        } else {
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
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() - 2.5f, expansionPos.getY() + 2.5f)) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() + 2.5f, expansionPos.getY())) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX() - 2.5f, expansionPos.getY())) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX(), expansionPos.getY() + 2.5f)) ||
                Bot.OBS.hasCreep(Point2d.of(expansionPos.getX(), expansionPos.getY() - 2.5f));
        return b;
    }

    public static List<UnitInPool> getEnemyTargetsInRange(Unit unit) {
        return getEnemyTargetsInRange(unit, target -> true);
    }

    public static List<UnitInPool> getEnemyTargetsInRange(Unit unit, Predicate<UnitInPool> targetFilter) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy -> ((enemy.unit().getFlying().orElse(true) &&
                        getDistance(enemy.unit(), unit) <= getAirAttackRange(unit) + enemy.unit().getRadius()) ||
                        (!enemy.unit().getFlying().orElse(true) &&
                                getDistance(enemy.unit(), unit) <= getGroundAttackRange(unit) + enemy.unit().getRadius())) &&
                        !IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        !enemy.unit().getHallucination().orElse(false) &&
                        !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD) &&
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        !UnitUtils.isSnapshot(enemy.unit()))
                .stream()
                .filter(targetFilter).
                collect(Collectors.toList());
    }

    public static Optional<UnitInPool> getNeutralTargetInRange(Unit unit) {
        return getNeutralTargetInRange(unit, target -> true);
    }

    public static Optional<UnitInPool> getNeutralTargetInRange(Unit unit, Predicate<UnitInPool> targetFilter) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, neutralUip -> ((neutralUip.unit().getFlying().orElse(true) &&
                        getDistance(neutralUip.unit(), unit) <= getAirAttackRange(unit) + neutralUip.unit().getRadius()) ||
                        (!neutralUip.unit().getFlying().orElse(true) &&
                                getDistance(neutralUip.unit(), unit) <= getGroundAttackRange(unit) + neutralUip.unit().getRadius())) &&
                        neutralUip.unit().getDisplayType() == DisplayType.VISIBLE &&
                        !UnitUtils.isSnapshot(neutralUip.unit()) &&
                        neutralUip.unit().getHealth().orElse(0f) > 0f)
                .stream()
                .filter(targetFilter)
                .findFirst();
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
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE);
    }

    public static List<UnitInPool> getEnemyGroundTargetsNear(Unit unit, float range) {
        return getEnemyGroundTargetsNear(unit.getPosition().toPoint2d(), range);
    }

    public static List<UnitInPool> getEnemyGroundTargetsNear(Point2d pos, float range) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                !enemy.unit().getFlying().orElse(true) &&
                        !IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        !enemy.unit().getHallucination().orElse(false) &&
                        !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD) &&
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        getDistance(enemy.unit(), pos) <= range);
    }

    public static Abilities getOrder(Unit unit) {
        Ability curOrder = ActionIssued.getCurOrder(unit).map(actionIssued -> actionIssued.ability).orElse(null);
        if (curOrder instanceof Abilities) {
            return (Abilities) curOrder;
        }
        return null;
    }

    public static void patrolInPlace(Unit unit, Point2d pos) {
        ActionHelper.unitCommand(unit, Abilities.PATROL, Position.towards(pos, LocationConstants.mainBaseMidPos, 1.5f), true);
    }

    public static void patrolInPlace(List<Unit> unitList, Point2d pos) {
        ActionHelper.unitCommand(unitList, Abilities.PATROL, Position.towards(pos, LocationConstants.mainBaseMidPos, 1.5f), true);
    }

    public static Unit getEnemyInRange(Unit myUnit) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        !enemy.unit().getHallucination().orElse(false) &&
                                (myUnit.getFlying().orElse(false) ? getAirAttackRange(enemy.unit()) : getGroundAttackRange(enemy.unit()))
                                        > UnitUtils.getDistance(myUnit, enemy.unit()))
                .stream()
                .findFirst()
                .map(UnitInPool::unit)
                .orElse(null);
    }

    public static Unit getClosestEnemyThreat(Unit myUnit) {
        return getClosestEnemyThreat(myUnit.getPosition().toPoint2d(), myUnit.getFlying().orElse(false));
    }

    public static Unit getClosestEnemyThreat(Point2d pos, boolean isThreatToAir) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        !enemy.unit().getHallucination().orElse(false) &&
                                enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                                isThreatToAir ? canAttackAir(enemy.unit()) : canAttackGround(enemy.unit()))
                .stream()
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), pos)))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    public static Set<Units> getUnitTypeSet(Units unitType) {
        if (unitType == null) {
            return null;
        }
        switch (unitType) {
            case TERRAN_SUPPLY_DEPOT_LOWERED:
            case TERRAN_SUPPLY_DEPOT:
                return SUPPLY_DEPOT_TYPE;
            case TERRAN_COMMAND_CENTER_FLYING:
            case TERRAN_COMMAND_CENTER:
                return COMMAND_STRUCTURE_TYPE_TERRAN;
            case TERRAN_ORBITAL_COMMAND_FLYING:
            case TERRAN_ORBITAL_COMMAND:
                return ORBITAL_COMMAND_TYPE;
            case TERRAN_BARRACKS_FLYING:
            case TERRAN_BARRACKS:
                return BARRACKS_TYPE;
            case TERRAN_FACTORY_FLYING:
            case TERRAN_FACTORY:
                return FACTORY_TYPE;
            case TERRAN_STARPORT_FLYING:
            case TERRAN_STARPORT:
                return STARPORT_TYPE;
            case TERRAN_HELLION_TANK:
            case TERRAN_HELLION:
                return HELLION_TYPE;
            case TERRAN_WIDOWMINE_BURROWED:
            case TERRAN_WIDOWMINE:
                return WIDOW_MINE_TYPE;
            case TERRAN_SIEGE_TANK_SIEGED:
            case TERRAN_SIEGE_TANK:
                return SIEGE_TANK_TYPE;
            case TERRAN_THOR_AP:
            case TERRAN_THOR:
                return THOR_TYPE;
            case TERRAN_VIKING_ASSAULT:
            case TERRAN_VIKING_FIGHTER:
                return VIKING_TYPE;
            case TERRAN_LIBERATOR_AG:
            case TERRAN_LIBERATOR:
                return LIBERATOR_TYPE;
        }
        return Set.of(unitType);
    }

    public static boolean isWeaponAvailable(Unit myUnit) {
        //if matrixed
        if (myUnit.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE)) {
            return false;
        }

        //if under blinding cloud TODO: change to do melee range attacks?
        if (!myUnit.getFlying().orElse(false) && myUnit.getBuffs().contains(Buffs.BLINDING_CLOUD)) {
            return false;
        }

        //if unit has no weapon
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(myUnit.getType()).getWeapons();
        if (weapons.isEmpty()) {
            return false;
        }

        //if weapon will be ready to fire next step
        float curCooldownInFrames = myUnit.getWeaponCooldown().orElse(0f);
        return curCooldownInFrames <= Launcher.STEP_SIZE;
    }

    public static UnitInPool getClosestUnit(List<UnitInPool> unitList, Unit targetUnit) {
        return getClosestUnit(unitList, targetUnit.getPosition().toPoint2d());
    }

    public static UnitInPool getClosestUnit(List<UnitInPool> unitList, Point2d targetPos) {
        return unitList.stream()
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), targetPos)))
                .orElse(null);
    }

    public static boolean isScvRepairing(Unit scv) {
        return ActionIssued.getCurOrder(scv).stream()
                .anyMatch(order -> order.ability == Abilities.EFFECT_REPAIR);
    }

    public static float getUnitSpeed(UnitType unitType) {
        return Bot.OBS.getUnitTypeData(false).get(unitType).getMovementSpeed().orElse(0f);
    }

    public static Unit getCompletedNatBunker() {
        return getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL, 3f).stream()
                .map(UnitInPool::unit)
                .filter(unit -> unit.getBuildProgress() == 1)
                .findFirst()
                .orElse(null);
    }

    public static boolean isInMyMainOrNat(Unit unit) {
        return isInMyMainOrNat(unit.getPosition().toPoint2d());
    }

    public static boolean isInMyMain(Unit unit) {
        return isInMyMain(unit.getPosition().toPoint2d());
    }

    public static boolean isInMyMain(Point2d unitPos) {
        return InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, unitPos);
    }

    public static boolean isInMyMainOrNat(Point2d unitPos) {
        return InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, unitPos) ||
                InfluenceMaps.getValue(InfluenceMaps.pointInNat, unitPos);

//        Point2d mainPos = GameCache.baseList.get(0).getCcPos();
//        Point2d natPos = GameCache.baseList.get(1).getCcPos();
//
//        float unitHeight = Bot.OBS.terrainHeight(unitPos);
//        float mainHeight = Bot.OBS.terrainHeight(mainPos);
//        float natHeight = Bot.OBS.terrainHeight(natPos);
//
//        boolean isInMain = unitPos.distance(mainPos) < 30 && Math.abs(unitHeight - mainHeight) < 1.2;
//        boolean isInNat = unitPos.distance(natPos) < 16 && Math.abs(unitHeight - natHeight) < 1.2;
//
//        return isInMain || isInNat;
    }

    public static int getMineralCost(Unit unit) {
        return Bot.OBS.getUnitTypeData(false).get(unit.getType()).getMineralCost().orElse(0);
    }

    public static int getGasCost(Unit unit) {
        return Bot.OBS.getUnitTypeData(false).get(unit.getType()).getVespeneCost().orElse(0);
    }

    public static Cost getCost(Unit unit) {
        return Cost.getUnitCost(unit.getType());
    }

    public static float getVisionRange(Unit unit) {
        return Bot.OBS.getUnitTypeData(false).get(unit.getType()).getSightRange().orElse(0f);
    }

    public static Tag getTargetUnitTag(Unit unit) {
        return ActionIssued.getCurOrder(unit).map(actionIssued -> actionIssued.targetTag).orElse(null);
    }

    public static float rangeToSee(Unit unit, Unit targetUnit) {
        return unit.getRadius() + targetUnit.getRadius() + Bot.OBS.getUnitTypeData(false).get(unit.getType()).getSightRange().orElse(0f);
    }

    public static List<UnitInPool> getIdleScvs() {
        return Bot.OBS.getUnits(Alliance.SELF, scv ->
                scv.unit().getType() == Units.TERRAN_SCV &&
                        ActionIssued.getCurOrder(scv).isEmpty() &&
                        !Ignored.contains(scv.getTag()) &&
                        !Base.isMining(scv));
    }

    public static boolean isEnemyEnteringDetection(Unit enemy) {
        return !Bot.OBS.getUnits(Alliance.SELF, u ->
                (u.unit().getType() == Units.TERRAN_MISSILE_TURRET || u.unit().getType() == Units.TERRAN_RAVEN) &&
                        u.unit().getBuildProgress() == 1 &&
                        UnitUtils.getDistance(u.unit(), enemy) < 10 && UnitUtils.getDistance(u.unit(), enemy) > 9.6).isEmpty(); // > 9.6 is to handle halluc phoenix in range of a missile turret as it completes which registers as a false positive
    }

    public static boolean isInMyDetection(Point2d pos) {
        return !Bot.OBS.getUnits(Alliance.SELF, u ->
                (u.unit().getType() == Units.TERRAN_MISSILE_TURRET || u.unit().getType() == Units.TERRAN_RAVEN) &&
                        u.unit().getBuildProgress() == 1 &&
                        UnitUtils.getDistance(u.unit(), pos) <= 10).isEmpty();
    }

    public static int numScvs(boolean includeProducing) {
        return Bot.OBS.getFoodWorkers() + (includeProducing ? numInProductionOfType(Units.TERRAN_SCV) : 0);
    }

    public static Point2d getRandomPathablePos() {
        Point2d randomPos = Bot.OBS.getGameInfo().findRandomLocation();
        while (!Bot.OBS.isPathable(randomPos)) {
            randomPos = Bot.OBS.getGameInfo().findRandomLocation();
        }
        return randomPos;
    }

    public static Point2d getPosLeadingUnit(Unit myUnit, Unit targetUnit) {
        Point2d targetPos = targetUnit.getPosition().toPoint2d();
        //float targetFacingAngle = Position.getFacingAngle(targetUnit);
        float distance = Bot.OBS.getUnitTypeData(false).get(targetUnit.getType()).getMovementSpeed().orElse(0f); //use speed as distance to lead
        Point2d leadPos = Position.getDestinationByAngle(targetPos, targetUnit.getFacing(), distance);
        if (!myUnit.getFlying().orElse(true) && !Bot.OBS.isPathable(leadPos)) {
            return targetPos;
        }
        return leadPos;
    }

    public static boolean hasOrderPosition(Unit unit, Point2d pos) {
        return unit.getOrders().stream()
                .anyMatch(unitOrder -> unitOrder.getTargetedWorldSpacePosition().isPresent() &&
                        unitOrder.getTargetedWorldSpacePosition().get().toPoint2d().distance(pos) < 1);
    }

    public static boolean justLostVisionOf(UnitInPool unit) {
        return unit.unit().getDisplayType() != DisplayType.SNAPSHOT &&
                unit.isAlive() &&
                unit.getLastSeenGameLoop() < Time.nowFrames() &&
                unit.getLastSeenGameLoop() >= (Time.nowFrames() - Launcher.STEP_SIZE);
    }

    public static boolean canAttack(UnitType unitType) {
        if (unitType.toString().contains("CHANGELING")) {
            return false;
        }
        if (unitType == Units.TERRAN_BUNKER && !Strategy.DO_IGNORE_BUNKERS) {
            return true;
        }
        return !Bot.OBS.getUnitTypeData(false).get(unitType)
                .getWeapons().isEmpty();
    }

    public static Set<UnitAttribute> getAttributes(Unit unit) {
        return Bot.OBS.getUnitTypeData(false).get(unit.getType()).getAttributes();
    }

    public static float getTotalHealth(Unit unit) {
        return unit.getHealth().orElse(0f) + unit.getShield().orElse(0f);
    }

    //just checks placement grid and creep in observation()
    public static boolean isPlaceable(Units structureType, Point2d structurePos) {
        float structureRadius = getStructureRadius(structureType) - 0.05f;
        float x = structurePos.getX();
        float y = structurePos.getY();
        Point2d top = Point2d.of(x, y + structureRadius);
        Point2d bottom = Point2d.of(x, y - structureRadius);
        Point2d left = Point2d.of(x - structureRadius, y);
        Point2d right = Point2d.of(x + structureRadius, y);
        Point2d center = structurePos;
        Point2d topLeft = Point2d.of(x - structureRadius, y + structureRadius);
        Point2d topRight = Point2d.of(x + structureRadius, y + structureRadius);
        Point2d botLeft = Point2d.of(x - structureRadius, y - structureRadius);
        Point2d botRight = Point2d.of(x + structureRadius, y - structureRadius);

        return Bot.OBS.isPlacable(topLeft) && Bot.OBS.isPlacable(top) && Bot.OBS.isPlacable(topRight) &&
                Bot.OBS.isPlacable(left) && Bot.OBS.isPlacable(center) && Bot.OBS.isPlacable(right) &&
                Bot.OBS.isPlacable(botLeft) && Bot.OBS.isPlacable(bottom) && Bot.OBS.isPlacable(botRight) &&

                !Bot.OBS.hasCreep(topLeft) && !Bot.OBS.hasCreep(top) && !Bot.OBS.hasCreep(topRight) &&
                !Bot.OBS.hasCreep(left) && !Bot.OBS.hasCreep(center) && !Bot.OBS.hasCreep(right) &&
                !Bot.OBS.hasCreep(botLeft) && !Bot.OBS.hasCreep(bottom) && !Bot.OBS.hasCreep(botRight);
    }

    public static Point2d getRandomUnownedBasePos() {
        List<Base> notMyBases = GameCache.baseList.stream()
                .filter(base -> !base.isMyBase())
                .collect(Collectors.toList());
        if (!notMyBases.isEmpty()) {
            Random r = new Random();
            return notMyBases.get(r.nextInt(notMyBases.size())).getCcPos();
        }
        return null;
    }

    public static boolean isWallComplete() {
        return Bot.OBS.getUnits(Alliance.SELF, u -> isStructure(u.unit().getType()) && isRampWallStructure(u.unit())).size() >= 3;
    }

    public static boolean isReaperWallStructure(Unit structure) {
        return isReaperWallStructure(structure.getPosition().toPoint2d());
    }

    public static boolean isReaperWallStructure(Point2d structurePos) {
        return LocationConstants.reaperBlockDepots.stream().anyMatch(p -> p.distance(structurePos) < 1) ||
                LocationConstants.reaperBlock3x3s.stream().anyMatch(p -> p.distance(structurePos) < 1);
    }

    public static boolean isRampWallStructure(Unit structure) {
        return isRampWallStructure(structure.getPosition().toPoint2d());
    }

    public static boolean isRampWallStructure(Point2d structurePos) {
        return structurePos.distance(LocationConstants.WALL_2x2) < 1 ||
                structurePos.distance(LocationConstants.WALL_3x3) < 1 ||
                structurePos.distance(LocationConstants.MID_WALL_2x2) < 1 ||
                structurePos.distance(LocationConstants.MID_WALL_3x3) < 1;
    }

    public static boolean isWallingStructure(Unit structure) {
        return isWallingStructure(structure.getPosition().toPoint2d());
    }

    public static boolean isWallingStructure(Point2d structurePos) {
        return isRampWallStructure(structurePos) ||
                (LocationConstants.opponentRace == Race.TERRAN && isReaperWallStructure(structurePos));
    }

    public static boolean myUnitWithin1ShotThreat(Unit myUnit) {
        return myUnitWithin1ShotThreat(myUnit, myUnit.getPosition().toPoint2d());
    }

    public static boolean myUnitWithin1ShotThreat(Unit myUnit, Point2d pos) {
        boolean isGround = !myUnit.getFlying().orElse(true);
        int[][] threatMap = isGround ? InfluenceMaps.pointDamageToGroundValue : InfluenceMaps.pointDamageToAirValue;
        return myUnit.getHealth().orElse(0f) < InfluenceMaps.getValue(threatMap, pos);
    }

    public static boolean myUnitWithin2ShotThreat(Unit myUnit) {
        return myUnitWithin2ShotThreat(myUnit, myUnit.getPosition().toPoint2d());
    }

    public static boolean myUnitWithin2ShotThreat(Unit myUnit, Point2d pos) {
        boolean isGround = !myUnit.getFlying().orElse(true);
        int[][] threatMap = isGround ? InfluenceMaps.pointDamageToGroundValue : InfluenceMaps.pointDamageToAirValue;
        return myUnit.getHealth().orElse(0f) < InfluenceMaps.getValue(threatMap, pos)*2;
    }

    public static boolean canOneShotEnemy(Unit myUnit, Unit enemyUnit) {
        return numShotsToKill(myUnit, enemyUnit) <= 1;
    }

    public static int numShotsToKill(Unit myUnit, Unit enemyUnit) {
        Weapon myWeapon = getWeapon(myUnit, enemyUnit).orElse(null);
        if (myWeapon == null) {
            return 9999;
        }
        UnitTypeData enemyData = Bot.OBS.getUnitTypeData(false).get(enemyUnit.getType());
        Set<UnitAttribute> enemyAttributes = enemyData.getAttributes();
        float enemyArmor = enemyData.getArmor().orElse(0f);
        int enemyArmorUpgradeLevel = enemyUnit.getArmorUpgradeLevel().orElse(0);

        int myAttackUpgradeLevel = myUnit.getAttackUpgradeLevel().orElse(0);
        float myDamage = myWeapon.getDamage() + myAttackUpgradeLevel;
        myDamage += myWeapon.getDamageBonuses().stream()
                .filter(damageBonus -> enemyAttributes.contains(damageBonus.getAttribute()))
                .mapToDouble(damageBonus -> damageBonus.getBonus() + myAttackUpgradeLevel)
                .sum();
        myDamage -= enemyArmor + enemyArmorUpgradeLevel;
        myDamage *= myWeapon.getAttacks();

        return (int) Math.ceil(enemyUnit.getHealth().orElse(9999f) / myDamage);
    }

    public static Optional<Weapon> getWeapon(Unit attackingUnit, Unit targetUnit) {
        return getWeapon(attackingUnit, targetUnit.getFlying().orElse(false));
    }

    public static Optional<Weapon> getWeapon(Unit attackingUnit, boolean isTargetFlying) {
        if (attackingUnit.getType().toString().contains("CHANGELING")) {
            return Optional.empty();
        }
        return Bot.OBS.getUnitTypeData(false).get(attackingUnit.getType()).getWeapons().stream()
                .filter(weapon -> weapon.getTargetType() !=
                        (isTargetFlying ? Weapon.TargetType.GROUND : Weapon.TargetType.AIR))
                .findAny();
    }

    //enemy units shooting from high ground are DisplayType of VISIBLE (not SNAPSHOT)
    public static boolean isUnitPositionVisible(Unit unit) {
        return Bot.OBS.getVisibility(unit.getPosition().toPoint2d()) == Visibility.VISIBLE;
    }

    public static boolean isAnyStarportIdle() {
        return GameCache.starportList.stream().anyMatch(u -> !u.unit().getActive().get());
    }

    public static boolean isAnyFactoryIdle() {
        return GameCache.factoryList.stream().anyMatch(u -> !u.unit().getActive().get());
    }

    public static boolean isAnyBarracksIdle() {
        return GameCache.barracksList.stream().anyMatch(u -> !u.unit().getActive().get());
    }

    public static int numScansAvailable() {
        return getMyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND).stream()
                .mapToInt(oc -> (int) (oc.getEnergy().orElse(1f) / 50))
                .sum();
    }

    public static void scan(Point2d pos) {
        getMyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND).stream()
                .filter(oc -> oc.getEnergy().orElse(0f) >= 50)
                .max(Comparator.comparing(oc -> oc.getEnergy().get()))
                .ifPresent(oc -> {
                    ActionHelper.unitCommand(oc, Abilities.EFFECT_SCAN, pos, false);
                    ArmyManager.prevScanFrame = Time.nowFrames();
                });
    }

    public static boolean requiresDetection(Unit enemy) {
        return enemy.getDisplayType() == DisplayType.HIDDEN ||
                UnitUtils.DETECTION_REQUIRED_TYPE.contains((Units) enemy.getType()) ||
                enemy.getType().toString().endsWith("_BURROWED");
    }

    public static float getEnemySupply() {
        return (float) GameCache.allEnemiesList.stream()
                .mapToDouble(u -> Bot.OBS.getUnitTypeData(false).get(u.unit().getType()).getFoodRequired().orElse(0f))
                .sum();
    }

    public static float getVisibleEnemySupplyInMyMainorNat() {
        return (float) GameCache.allVisibleEnemiesList.stream()
                .filter(enemy -> UnitUtils.isInMyMainOrNat(enemy.unit()))
                .mapToDouble(enemy -> {
                    if (enemy.unit().getType() == Units.TERRAN_BUNKER && enemy.unit().getBuildProgress() == 1) {
                        return 4;
                    }
                    return Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType()).getFoodRequired().orElse(0f);
                })
                .sum();
    }

    public static Optional<UnitInPool> getAddOn(Unit structure) {
        //return complete add-on
        if (structure.getAddOnTag().isPresent()) {
            return structure.getAddOnTag().map(tag -> Bot.OBS.getUnit(tag));
        }

        //find add-on in progress
        if (structure.getOrders().stream().anyMatch(unitOrder -> unitOrder.getAbility().toString().contains("BUILD"))) {
            UnitOrder curOrder = structure.getOrders().get(0);
            String addOnType = curOrder.getAbility() == Abilities.BUILD_TECHLAB ? "TECHLAB" : "REACTOR";
            return Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType().toString().contains(addOnType) &&
                            UnitUtils.getDistance(u.unit(), getAddonPos(structure)) < 1)
                    .stream()
                    .findFirst();
        }

        return Optional.empty();
    }

    public static Point2d getAddonPos(Unit structure) {
        return structure.getPosition().toPoint2d().add(2.5f, -0.5f);
    }

    public static boolean isOutOfGas() {
        return Cost.isGasBroke(25) && !Strategy.MARINE_ALLIN && Time.nowFrames() > Time.toFrames("12:00");
    }

    //TODO: correctly identify this, rather than using time
    public static boolean withinOpeningBuildOrder() {
        return Time.nowFrames() < Time.toFrames("3:30");
    }

    //time (s) until a structure is available
    public static int secondsUntilAvailable(Unit structure) {
        //include time of units in purchase queue for this structure
        int timeForQueuedUnits = KetrocBot.purchaseQueue.stream()
                .filter(purchase -> purchase instanceof PurchaseUnit)
                .map(purchase -> (PurchaseUnit) purchase)
                .filter(purchaseUnit -> purchaseUnit.getProductionStructure() != null &&
                        purchaseUnit.getProductionStructure().getTag().equals(structure.getTag()))
                .mapToInt(purchaseUnit -> secondsToProduce(purchaseUnit.getUnitType()))
                .sum();

        // if under construction
        if (structure.getBuildProgress() != 1) {
            return timeForQueuedUnits + (int) (secondsToProduce(structure.getType()) * (1 - structure.getBuildProgress()));
        }

        //TODO: include structure morphs (command centers)

        //if available now
        if (!structure.getActive().orElse(true)) {
            return timeForQueuedUnits;
        }

        if (!structure.getOrders().isEmpty()) {
            UnitOrder curOrder = structure.getOrders().get(0);

            // if currently training a unit
            if (curOrder.getProgress().isPresent() && curOrder.getAbility().toString().contains("TRAIN")) {
                Units unitTypeInProduction = Bot.abilityToUnitType.get(curOrder.getAbility());
                return timeForQueuedUnits + (int) (secondsToProduce(unitTypeInProduction) * (1 - curOrder.getProgress().get()));
            }

            // if currently building an add-on
            else if (curOrder.getAbility().toString().contains("BUILD")) {
                UnitInPool addOn = getAddOn(structure).orElse(null);
                if (addOn == null) {
                    return timeForQueuedUnits + secondsToProduce(addOn.unit().getType());
                } else {
                    return timeForQueuedUnits + (int) (secondsToProduce(addOn.unit().getType()) * (1 - addOn.unit().getBuildProgress()));
                }
            }

            // if currently researching an upgrade
            else if (curOrder.getAbility().toString().contains("RESEARCH")) {
                Upgrades upgradeInProduction = Bot.abilityToUpgrade.get(curOrder.getAbility());
                return (int) (secondsToProduce(upgradeInProduction) * (1 - curOrder.getProgress().get()));
            }
        }
        return 0;
    }

    public static int secondsToProduce(UnitType unitType) {
        return Time.toSeconds(
                Bot.OBS.getUnitTypeData(false).get(unitType).getBuildTime().orElse(0f).intValue()
        );
    }

    public static int secondsToProduce(Upgrade upgrade) {
        return Time.toSeconds(
                Bot.OBS.getUpgradeData(false).get(upgrade).getResearchTime().orElse(0f).intValue()
        );
    }

    public static int secondsToProduce(Abilities ability) {
        if (ability.toString().startsWith("BUILD")) {
            return secondsToProduce(Bot.abilityToUnitType.get(ability));
        } else {
            return secondsToProduce(Bot.abilityToUpgrade.get(ability));
        }
    }

    public static int numMySiegedTanks() {
        return Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED).size();
    }

    public static boolean isRepairBaySafe() {
        return !InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, LocationConstants.REPAIR_BAY) &&
                !InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, LocationConstants.REPAIR_BAY);
    }

    public static boolean isAnyBaseUnderAttack() {
        return GameCache.baseList.stream().anyMatch(base -> base.isUnderAttack());
    }

    public static List<UnitInPool> getEnemyGroundArmyUnitsNearby(Point2d origin, int range) {
        return Bot.OBS.getUnits(Alliance.ENEMY, u ->
                !u.unit().getFlying().orElse(true) &&
                        canAttack(u.unit().getType()) &&
                        UnitUtils.getDistance(u.unit(), origin) < range);
    }

    public static boolean isExpansionNeeded() {
        return BuildManager.getNextAvailableExpansionPosition() != null &&
                Base.scvsReqForMyBases() < Math.min(Strategy.maxScvs, UnitUtils.numScvs(true) + (Base.numMyBases() * 4));
    }

    public static boolean isEnemyRetreating(Unit enemyUnit, Point2d myUnitPos) {
        //always consider burrowed units and units unable to move as: not retreating
        if (enemyUnit.getType().toString().endsWith("_BURROWED") || !canMove(enemyUnit)) {
            return false;
        }

        //check facing angle to tell if unit is retreating
        float facing = (float) Math.toDegrees(enemyUnit.getFacing());
        float attackAngle = Position.getAngle(enemyUnit.getPosition().toPoint2d(), myUnitPos);
        float angleDiff = Position.getAngleDifference(facing, attackAngle);
        return angleDiff > 100;
    }

    public static Optional<UnitInPool> getNatBunker() {
        return Bot.OBS.getUnits(Alliance.SELF, bunker -> bunker.unit().getType() == Units.TERRAN_BUNKER &&
                        getDistance(bunker.unit(), LocationConstants.BUNKER_NATURAL) < 1 &&
                        ActionIssued.getCurOrder(bunker).stream()
                                .noneMatch(actionIssued -> actionIssued.ability == Abilities.EFFECT_SALVAGE))
                .stream()
                .findFirst();
    }

    public static Set<Unit> getRepairBayTargets(Point2d repairBayPos) {
        return Bot.OBS.getUnits(Alliance.SELF, u ->
                        (UnitUtils.REPAIR_BAY_TYPES.contains(u.unit().getType()) ||
                                (!Strategy.DO_OFFENSIVE_TANKS && UnitUtils.SIEGE_TANK_TYPE.contains(u.unit().getType()))) &&
                                UnitUtils.getDistance(u.unit(), repairBayPos) <= 4 &&
                                UnitUtils.getHealthPercentage(u.unit()) < 100)
                .stream()
                .map(UnitInPool::unit)
                .collect(Collectors.toSet());
    }

    public static void returnAndStopScv(UnitInPool scv) {
        if (isCarryingResources(scv.unit())) {
            ActionHelper.unitCommand(scv.unit(), Abilities.HARVEST_RETURN, false);
            ActionHelper.unitCommand(scv.unit(), Abilities.STOP, true);
        } else {
            ActionHelper.unitCommand(scv.unit(), Abilities.STOP, false);
        }
    }

    public static boolean requiresTechLab(Units unitType) {
        switch (unitType) {
            case TERRAN_MARAUDER:
            case TERRAN_GHOST:
            case TERRAN_CYCLONE:
            case TERRAN_SIEGE_TANK:
            case TERRAN_THOR:
            case TERRAN_BANSHEE:
            case TERRAN_RAVEN:
            case TERRAN_BATTLECRUISER:
                return true;
            default:
                return false;
        }
    }

    public static float getTrainingTimeRemaining(Unit structure) {
        return structure.getOrders().stream()
                .findFirst()
                .flatMap(order -> order.getProgress())
                .orElse(0f);
    }

    //gets production structure that is available or best one to cancel current production of
    public static Optional<Unit> getEmergencyProductionStructure(Units unitToTrain) {
        Units structureType = getRequiredStructureType(unitToTrain);
        boolean requiresTechLab = requiresTechLab(unitToTrain);
        return getMyUnitsOfType(structureType).stream()
                .filter(structure -> !requiresTechLab || structure.getAddOnTag().isPresent())
                .min(Comparator.comparing(structure -> getTrainingTimeRemaining(structure)));
    }

    public static Units getRequiredStructureType(Units unitType) {
        switch (unitType) {
            case TERRAN_SCV:
            case TERRAN_ORBITAL_COMMAND:
            case TERRAN_PLANETARY_FORTRESS:
                return Units.TERRAN_COMMAND_CENTER;
            case TERRAN_MARINE:
            case TERRAN_MARAUDER:
            case TERRAN_GHOST:
            case TERRAN_REAPER:
            case TERRAN_BARRACKS_TECHLAB:
            case TERRAN_BARRACKS_REACTOR:
                return Units.TERRAN_BARRACKS;
            case TERRAN_HELLION:
            case TERRAN_HELLION_TANK:
            case TERRAN_CYCLONE:
            case TERRAN_SIEGE_TANK:
            case TERRAN_THOR:
            case TERRAN_WIDOWMINE:
            case TERRAN_FACTORY_TECHLAB:
            case TERRAN_FACTORY_REACTOR:
                return Units.TERRAN_FACTORY;
            default: //starport units
                return Units.TERRAN_STARPORT;
        }
    }

    public static Base getNextEnemyBase() {
        return getNextEnemyBase(null);
    }

    public static Base getNextEnemyBase(Point2d curEnemyBasePos) {
        List<Base> enemyBases = GameCache.baseList.stream()
                .filter(base -> base.isEnemyBase)
                .collect(Collectors.toList());
        if (enemyBases.isEmpty()) {
            return null;
        }
        if (curEnemyBasePos == null || //no cur base
                enemyBases.get(enemyBases.size() - 1).getCcPos().distance(curEnemyBasePos) < 1) { //cur base = enemy main
            return enemyBases.get(0);
        }
        //otherwise get the next newest enemy base
        for (int i = 0; i < enemyBases.size(); i++) {
            if (enemyBases.get(i).getCcPos().distance(curEnemyBasePos) < 1) {
                return enemyBases.get(i + 1);
            }
        }
        //curEnemyBasePos is no longer an enemy base
        return enemyBases.get(0);
    }

    //only valid to use on speed-mining scv, that has an order, and isn't carrying resources
    //identifies mining scvs that were bumped and are now following the cc
    public static boolean isMiningScvStuck(Unit scv) {
        ActionIssued curOrder = ActionIssued.getCurOrder(scv).get();
        return curOrder.ability == Abilities.MOVE &&
                curOrder.targetTag != null &&
                Bot.OBS.getUnit(curOrder.targetTag) != null &&
                UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN.contains(Bot.OBS.getUnit(curOrder.targetTag).unit().getType());
    }

    public static boolean isEnemyUnitSolo(Unit enemyUnit) {
        return InfluenceMaps.getValue(InfluenceMaps.pointEnemyAttackersWith10Range, enemyUnit.getPosition().toPoint2d()) < 2;
    }

    public static Point2d getReachableAttackPos(Unit enemyUnit, Unit myUnit) {
        int attackRange = (int) (enemyUnit.getRadius() +
                UnitUtils.getAttackRange(
                        myUnit,
                        enemyUnit.getFlying().get() ? Weapon.TargetType.AIR : Weapon.TargetType.GROUND
                ));
        return Position.getSpiralList(enemyUnit.getPosition().toPoint2d(), attackRange).stream()
                .filter(p -> Bot.OBS.isPathable(p) && UnitUtils.getDistance(enemyUnit, p) <= attackRange)
                .min(Comparator.comparing(p -> UnitUtils.getDistance(myUnit, p)))
                .orElse(null);
    }

    public static boolean isReachableToAttack(Unit enemyUnit, float attackRange) {
        float finalAttackRange = attackRange + enemyUnit.getRadius();
        return Position.getSpiralList(enemyUnit.getPosition().toPoint2d(), (int) Math.ceil(finalAttackRange)).stream()
                .anyMatch(p -> Bot.OBS.isPathable(p) && UnitUtils.getDistance(enemyUnit, p) <= finalAttackRange);
    }

    public static Point2d getBehindBunkerPos() {
        return Position.towards(LocationConstants.BUNKER_NATURAL, GameCache.baseList.get(1).getCcPos(), 1.9f);
    }

    public static boolean isDestructible(UnitInPool uip) {
        return isDestructible(uip.unit());
    }

    public static boolean isDestructible(Unit unit) {
        return isDestructible((Units) unit.getType());
    }

    public static boolean isDestructible(Units unitType) {
        String unitName = unitType.toString();
        return (unitName.contains("_DESTRUCTIBLE") || unitName.contains("_COLLAPSIBLE")) &&
                //TODO: remove below when I can handle rectangular and diagonal layouts
                !unitName.contains("_VERTICAL") && !unitName.contains("_HORIZONTAL") && !unitName.contains("_DIAGONAL");
    }

    public static float getRange(Unit unit1, Unit unit2) {
        return getDistance(unit1, unit2) - unit1.getRadius() - unit2.getRadius();
    }

    //TODO: consider damage upgrades and armor upgrades
    //TODO: include abilities like widow mine and baneling attack
    public static float getDps(Unit unit, Unit target) {
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(unit.getType());
        UnitTypeData targetData = Bot.OBS.getUnitTypeData(false).get(target.getType());
        Weapon unitWeapon = UnitUtils.getWeapon(unit, target).orElse(null);
        if (unitWeapon == null) {
            return 0;
        }
        float weaponDmg = unitWeapon.getDamage();
        weaponDmg += unitWeapon.getDamageBonuses().stream()
                .filter(damageBonus -> targetData.getAttributes().stream()
                        .anyMatch(attrib -> attrib.equals(damageBonus.getAttribute())))
                .mapToDouble(DamageBonus::getBonus)
                .sum();
        return weaponDmg / unitWeapon.getSpeed();
    }
}