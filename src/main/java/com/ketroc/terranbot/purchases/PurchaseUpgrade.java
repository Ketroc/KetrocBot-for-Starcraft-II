package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.*;

public class PurchaseUpgrade implements Purchase {
    private UnitInPool structure;
    private Upgrades upgrade;
    private Cost cost;

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
    //TODO: have build method find structure rather than constructor passing it in
    public PurchaseResult build() {
        if (!structure.isAlive()) {
            return PurchaseResult.CANCEL;
        }
        if (!canAfford()) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }
        //if structure not producing unit/upgrade
        if (ActionIssued.getCurOrder(structure.unit()).isEmpty()) {
            Print.print("sending action " + this.upgrade);
            Ability upgradeAbility = Bot.OBS.getUpgradeData(false).get(upgrade).getAbility().orElse(Abilities.INVALID);
            if (upgradeAbility instanceof Abilities) {
                switch ((Abilities) upgradeAbility) {
                    case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                    case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                    case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                        upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING;
                        break;
                }
            }
            else { //missing Abilities enum
                Print.print("Unknown Ability: " + upgradeAbility.getAbilityId() + " for upgrade: " + upgrade);
            }
            ActionHelper.unitCommand(structure.unit(), upgradeAbility, false);
            Cost.updateBank(cost);
            return PurchaseResult.SUCCESS;
        }
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
