package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.*;
import com.ketroc.terranbot.Bot;
import com.ketroc.terranbot.GameState;

public class Cost {
    public int minerals;
    public int gas;

    public Cost(int minerals, int gas) {
        this.minerals = minerals;
        this.gas = gas;
    }

    // =========== METHODS ===========

    public static Cost getUnitCost(Units unitType) {
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(unitType);
        Cost unitCost = new Cost(unitData.getMineralCost().orElse(0), unitData.getVespeneCost().orElse(0));
        switch (unitType) {
            case TERRAN_ORBITAL_COMMAND: case TERRAN_PLANETARY_FORTRESS:
                unitCost.minerals -= 400;
        }
        return unitCost;
    }

    public static Cost getUpgradeCost(Upgrades upgrade) {
        UpgradeData upgradeData = Bot.OBS.getUpgradeData(false).get(upgrade);
        return new Cost(upgradeData.getMineralCost().orElse(0), upgradeData.getVespeneCost().orElse(0));
    }
    public static void updateBank(Units unitType) {
        updateBank(getUnitCost(unitType));
    }

    public static void updateBank(Cost cost) {
        GameState.mineralBank -= cost.minerals;
        GameState.gasBank -= cost.gas;
    }
}
