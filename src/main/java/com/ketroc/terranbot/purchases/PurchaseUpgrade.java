package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.ArrayList;
import java.util.List;

public class PurchaseUpgrade implements Purchase {
    private UnitInPool structure;
    private Upgrades upgrade;
    private Cost cost;
    public static final List<Upgrades> armoryUpgrades = (!Strategy.ARCHON_MODE)
            ?
            new ArrayList<>(List.of(
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1, Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2, Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3, Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3))
            :
            new ArrayList<>(List.of(
                    Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
                    Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
                    Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3
            ));

    // ============ CONSTRUCTORS =============

    public PurchaseUpgrade(Upgrades upgrade, UnitInPool structure) {

        this.upgrade = upgrade;
        this.structure = structure;
        setCost();
    }

    // ========= GETTERS AND SETTERS =========

    public UnitInPool getStructure() {
        return structure;
    }

    public void setStructure(UnitInPool structure) {
        this.structure = structure;
    }

    public Upgrades getUpgrade() {
        return upgrade;
    }

    public void setUpgrade(Upgrades upgrade) {
        this.upgrade = upgrade;
    }

    public Cost getCost() {
        return cost;
    }

    // ============= METHODS ==============
    public PurchaseResult build() {
        if (!structure.isAlive()) {
            GameCache.upgradesPurchased.remove(upgrade);
            if (structure.unit().getType() == Units.TERRAN_ARMORY) {
                armoryUpgrades.add(0, upgrade);
            }
            return PurchaseResult.CANCEL;
        }
        if (!canAfford()) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }
        //if structure not producing unit/upgrade
        if (structure.unit().getOrders().isEmpty()) {
            System.out.println("sending action @" + Bot.OBS.getGameLoop() + this.upgrade);
            Abilities upgradeAbility = (Abilities) Bot.OBS.getUpgradeData(false).get(upgrade).getAbility().orElse(Abilities.INVALID);
            switch (upgradeAbility) {
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                    upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1;
                    break;
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                    upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2;
                    break;
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                    upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3;
                    break;
            }
            Bot.ACTION.unitCommand(structure.unit(), upgradeAbility, false);
            Cost.updateBank(cost);
            GameCache.upgradesPurchased.add(upgrade);
            return PurchaseResult.SUCCESS;
        }
        GameCache.upgradesPurchased.remove(upgrade);
        if (structure.unit().getType() == Units.TERRAN_ARMORY) armoryUpgrades.add(0, upgrade);
        return PurchaseResult.CANCEL;
    }

    @Override
    public boolean canAfford() {
        return GameCache.mineralBank >= cost.minerals && GameCache.gasBank >= cost.gas;
    }

    @Override
    public void setCost() {
        cost = Cost.getUpgradeCost(upgrade);
    }

    @Override
    public String getType() {
        return upgrade.toString();
    }
}
