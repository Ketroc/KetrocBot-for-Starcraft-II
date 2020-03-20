package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UnitUtils {
    public static void onFrame() {
        updateAllUnits();
    }

    private static void updateAllUnits() {
        //loop through all friendly units
        List<UnitInPool> unitList = Bot.OBS.getUnits(Alliance.SELF);

        for (UnitInPool unitInPool : unitList) {
            Unit unit = unitInPool.unit();

            switch ((Units)unit.getType()) {

            }

        }

        //loop through all enemy units
        unitList = Bot.OBS.getUnits(Alliance.ENEMY);

        for (UnitInPool unitInPool : unitList) {
            Unit unit = unitInPool.unit();

            switch ((Units)unit.getType()) {

            }

        }

        //loop through all neutral units
        unitList = Bot.OBS.getUnits(Alliance.NEUTRAL);

        for (UnitInPool unitInPool : unitList) {
            Unit unit = unitInPool.unit();

            switch ((Units)unit.getType()) {
                case NEUTRAL_MINERAL_FIELD: case NEUTRAL_MINERAL_FIELD750: case NEUTRAL_RICH_MINERAL_FIELD:
                case NEUTRAL_RICH_MINERAL_FIELD750: case NEUTRAL_BATTLE_STATION_MINERAL_FIELD: case NEUTRAL_BATTLE_STATION_MINERAL_FIELD750:

            }

        }

    }

    public static boolean hasTechToBuild(Abilities abilityType) {
        return hasTechToBuild(Bot.abilityToUnitType.get(abilityType));
    }
    public static boolean hasTechToBuild(Units unitType) { //TODO: create map of requirements
        switch (unitType) {
            case TERRAN_ORBITAL_COMMAND:
                return !GameState.barracksList.isEmpty();
            case TERRAN_PLANETARY_FORTRESS:
                for (UnitInPool unit : GameState.otherFriendliesList) {
                    if (unit.unit().getType() == Units.TERRAN_ENGINEERING_BAY) {
                        return true;
                    }
                }
                break;
            case TERRAN_STARPORT:
                return !GameState.factoryList.isEmpty();
        }
        return false;
    }
    public static int getNumUnitsOfType(Units unitType) {
        return GameState.allFriendliesMap.getOrDefault(unitType, Collections.emptyList()).size();
    }

    public static boolean canAfford(Units unitType) {
        return canAfford(unitType, GameState.mineralBank, GameState.gasBank);
    }

    public static boolean canAfford(Units unitType, int minerals, int gas) {
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(unitType);
        int mineralCost =  unitData.getMineralCost().get();
        int gasCost = unitData.getVespeneCost().get();
        switch (unitType) {
            case TERRAN_PLANETARY_FORTRESS:
            case TERRAN_ORBITAL_COMMAND:
                mineralCost -= 400; //TODO: this is hardcoded
        }
        return minerals >= mineralCost && gas >= gasCost;
    }

    public static boolean isUnitTypesNearby(UnitType unitType, Point2d position, int distance) {
        return !getUnitsNearbyOfType(unitType, position, distance).isEmpty();
    }

    public static List<UnitInPool> getUnitsNearbyOfType(UnitType unitType, Point2d position, int distance) {
        return Bot.OBS.getUnits(unit -> unit.unit().getType() == unitType && unit.unit().getPosition().toPoint2d().distance(position) < distance);
    }

    public static boolean isUnitTypesNearby(List<UnitType> unitTypes, Point2d position, int distance) {
        return !getUnitsNearbyOfType(unitTypes, position, distance).isEmpty();
    }

    public static List<UnitInPool> getUnitsNearbyOfType(List<UnitType> unitTypes, Point2d position, int distance) {
        return Bot.OBS.getUnits(unit -> unitTypes.contains(unit.unit().getType()) && unit.unit().getPosition().toPoint2d().distance(position) < distance);
    }

    public static int numRepairingScvs(Unit repairTarget) {
        int repairingScvs = 0;
        for (UnitInPool scv : GameState.allFriendliesMap.getOrDefault(Units.TERRAN_SCV, Collections.emptyList())) {
            if (!scv.unit().getOrders().isEmpty()) {
                UnitOrder order = scv.unit().getOrders().get(0);
                if (order.getAbility() == Abilities.EFFECT_REPAIR && order.getTargetedUnitTag().get().equals(repairTarget.getTag())) {
                    repairingScvs++;
                }
            }
        }
        return repairingScvs;
    }

    public static int getHealthPercentage(Unit unit) {
        return unit.getHealth().get().intValue() * 100 / unit.getHealthMax().get().intValue();
    }

    public static int getIdealScvsToRepair(Unit unit) {
        switch ((Units)unit.getType()) {
            case TERRAN_PLANETARY_FORTRESS:
                int pfHealthPercentage = getHealthPercentage(unit);
                if (pfHealthPercentage > 95) {
                    return 5;
                }
                else if (pfHealthPercentage > 80) {
                    return 10;
                }
                else {
                    return Integer.MAX_VALUE;
                }
            case TERRAN_MISSILE_TURRET: case TERRAN_SIEGE_TANK_SIEGED:
                return 5;
        }
        return 0;
    }

    public static float getAirAttackRange(Unit unit) {
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.getType()).getWeapons();
        for (Weapon weapon : weapons) {
            if (weapon.getTargetType() == Weapon.TargetType.AIR || weapon.getTargetType() == Weapon.TargetType.ANY) {
                return weapon.getRange();
            }
        }
        return 0;
    }

    public static float getGroundAttackRange(Unit unit) {
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.getType()).getWeapons();
        for (Weapon weapon : weapons) {
            if (weapon.getTargetType() == Weapon.TargetType.GROUND || weapon.getTargetType() == Weapon.TargetType.ANY) {
                return weapon.getRange();
            }
        }
        return 0;
    }
}
