package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;

import java.text.DecimalFormat;

public class Cost {
    public int minerals;
    public int gas;
    public float supply;
    public static final DecimalFormat supplyFormat = new DecimalFormat("0.#");

    public Cost() {

    }

    public Cost(int minerals, int gas) {
        this(minerals, gas, 0);
    }

    public Cost(int minerals, int gas, float supply) {
        this.minerals = minerals;
        this.gas = gas;
        this.supply = supply;
    }

    // =========== METHODS ===========

    public void add(Cost addCost) {
        minerals += addCost.minerals;
        gas += addCost.gas;
        supply += addCost.supply;
    }

    public void add(UnitInPool uip) {
        add(uip.unit().getType(), 1);
    }

    public void add(Unit unit) {
        add(unit.getType(), 1);
    }

    public void add(UnitType unitType) {
        add(unitType, 1);
    }

    public void add(UnitInPool uip, int numUnits) {
        add(uip.unit().getType(), numUnits);
    }

    public void add(Unit unit, int numUnits) {
        add(unit.getType(), numUnits);
    }

    public void add(UnitType unitType, int numUnits) {
        UnitTypeData unitTypeData = Bot.OBS.getUnitTypeData(false).get(unitType);
        minerals += unitTypeData.getMineralCost().orElse(0) * numUnits;
        gas += unitTypeData.getVespeneCost().orElse(0) * numUnits;
        supply += unitTypeData.getFoodRequired().orElse(0f) * numUnits;
    }


    // =========== STATIC METHODS ===========

    public static Cost getUnitCost(UnitType unitType) {
        if (unitType == Units.INVALID) {
            return null;
        }
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(unitType);
        Cost unitCost = new Cost(
                unitData.getMineralCost().orElse(0),
                unitData.getVespeneCost().orElse(0),
                unitData.getFoodRequired().orElse(0f).intValue()
        );
        if (unitType == Units.TERRAN_ORBITAL_COMMAND || unitType == Units.TERRAN_PLANETARY_FORTRESS) {
            unitCost.minerals -= 400;
        }
        return unitCost;
    }

    public static Cost getUnitCost(Ability ability) {
        if (ability == Abilities.INVALID) {
            return null;
        }
        return getUnitCost(Bot.abilityToUnitType.get(ability));
    }

    public static Cost getUpgradeCost(Upgrades upgrade) {
        if (upgrade == Upgrades.INFERNAL_PRE_IGNITERS) {
            return new Cost(100, 100);
        }
        UpgradeData upgradeData = Bot.OBS.getUpgradeData(false).get(upgrade);
        return new Cost(upgradeData.getMineralCost().orElse(0), upgradeData.getVespeneCost().orElse(0));
    }

    public static void updateBank(Units unitType) {
        updateBank(getUnitCost(unitType));
    }

    public static void updateBank(Ability ability) {
        updateBank(getUnitCost(ability));
    }

    public static void updateBank(Cost cost) {
        GameCache.mineralBank -= cost.minerals;
        GameCache.gasBank -= Math.max(0, cost.gas);
        GameCache.freeSupply -= cost.supply;
    }

    public static boolean isGasBroke() {
        return isGasBroke(1);
    }

    public static boolean isGasBroke(int gasBankBelow) {
        return Bot.OBS.getVespene() < gasBankBelow && Bot.OBS.getScore().getDetails().getCollectionRateVespene() == 0;
    }

    public static boolean isMineralBroke() {
        return isMineralBroke(1);
    }

    public static boolean isMineralBroke(int mineralBankBelow) {
        return Bot.OBS.getMinerals() < mineralBankBelow && Bot.OBS.getScore().getDetails().getCollectionRateMinerals() == 0;
    }

    @Override
    public String toString() {
        return minerals + "m/" + gas + "g/" + supplyFormat.format(supply) + "s";
    }

    public float getValue() {
        return minerals + gas*1.2f;
    }
}
