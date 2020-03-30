package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.ketroc.terranbot.Bot;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.GameState;

public class PurchaseUpgrade implements Purchase {
    private UnitInPool structure;
    private Upgrades upgrade;
    private Cost cost;

    // ============ CONSTRUCTORS =============
//    public PurchaseUpgrade(Abilities upgrade) {
//        this(upgrade, null);
//    }
    public PurchaseUpgrade(Upgrades upgrade, UnitInPool structure) {
        this.upgrade = upgrade;
//        if (structure == null) {
//            structure = selectStructureUnit();
//        }
        this.structure = structure;
        setCost();
    }
//    public Unit selectStructureUnit() { //TODO: does api even provide a mapping of upgrade to unit type???
//        return null;
//    }

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
            return PurchaseResult.SUCCESS;
        }
        return PurchaseResult.CANCEL;
    }

    @Override
    public boolean canAfford() {
        return GameState.mineralBank >= cost.minerals && GameState.gasBank >= cost.gas;
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
