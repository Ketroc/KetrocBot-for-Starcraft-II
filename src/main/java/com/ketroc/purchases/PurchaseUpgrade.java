package com.ketroc.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.BuildManager;
import com.ketroc.models.Cost;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.Print;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.Set;

public class PurchaseUpgrade implements Purchase {
    private static final Set<Upgrades> LOW_PRIORITY_UPGRADES = Set.of(
            Upgrades.CYCLONE_LOCK_ON_DAMAGE_UPGRADE,
            //Upgrades.INFERNAL_PRE_IGNITERS,
            Upgrades.HISEC_AUTO_TRACKING,
            Upgrades.TERRAN_BUILDING_ARMOR,
            Upgrades.BANSHEE_SPEED
    );

    private UnitInPool productionStructure;
    private Upgrades upgrade;
    private Cost cost;

    // ============ CONSTRUCTORS =============

    public PurchaseUpgrade(Upgrades upgrade) {
        this.upgrade = upgrade;
        setCost();
    }

    public PurchaseUpgrade(Upgrades upgrade, UnitInPool productionStructure) {
        this.upgrade = upgrade;
        this.productionStructure = productionStructure;
        setCost();
    }

    // ========= GETTERS AND SETTERS =========

    public UnitInPool getProductionStructure() {
        return productionStructure;
    }

    public void setProductionStructure(UnitInPool productionStructure) {
        this.productionStructure = productionStructure;
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
        if (productionStructure != null && !productionStructure.isAlive()) {
            productionStructure = null;
        }
        if (doDelayThisUpgrade()) {
            return PurchaseResult.WAITING;
        }
        if (canAfford()) {
            if (productionStructure == null) {
                selectProductionStructure();
            }
            if (productionStructure == null) {
                return PurchaseResult.WAITING;
            }

            //if structure not producing upgrade
            if (!productionStructure.unit().getActive().orElse(true)) {
                Print.print("sending action " + upgrade);
                Ability upgradeAbility = getUpgradeAbility();
                ActionHelper.unitCommand(productionStructure.unit(), upgradeAbility, false);
                Cost.updateBank(cost);
                return PurchaseResult.SUCCESS;
            }
        }
        if (productionStructure != null) {
            Cost.updateBank(cost);
        }
        return PurchaseResult.WAITING;
    }

    //prioritize producing army units over certain upgrades
    private boolean doDelayThisUpgrade() {
        return ((LOW_PRIORITY_UPGRADES.contains(upgrade) || !hasRelatedArmyUnits()) &&
                Bot.OBS.getFoodUsed() < 197 &&
                !BuildManager.isAllProductionStructuresBusy());
    }

    private boolean hasRelatedArmyUnits() {
        switch (upgrade) { //TODO: complete for all upgrades (that I don't currently use)
            case CYCLONE_LOCK_ON_DAMAGE_UPGRADE:
                return UnitUtils.numMyUnits(Units.TERRAN_CYCLONE, true) > 0;
            case INFERNAL_PRE_IGNITERS:
                return UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, true) > 0;
            case BANSHEE_SPEED: case BANSHEE_CLOAK:
                return UnitUtils.numMyUnits(Units.TERRAN_BANSHEE, true) > 0;
            case HISEC_AUTO_TRACKING: case TERRAN_BUILDING_ARMOR:
                return UnitUtils.numMyUnits(Units.TERRAN_PLANETARY_FORTRESS, true) > 0;
        }
        return true;
    }

    private Ability getUpgradeAbility() {
        if (upgrade == Upgrades.INFERNAL_PRE_IGNITERS) {
            return Abilities.RESEARCH_INFERNAL_PREIGNITER;
        }

        Ability upgradeAbility = Bot.OBS.getUpgradeData(false).get(upgrade).getAbility().orElse(Abilities.INVALID);
        if (upgradeAbility instanceof Abilities) {
            switch ((Abilities) upgradeAbility) {
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                    upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING;
                    break;
                case RESEARCH_TERRAN_VEHICLE_WEAPONS_LEVEL1:
                case RESEARCH_TERRAN_VEHICLE_WEAPONS_LEVEL2:
                case RESEARCH_TERRAN_VEHICLE_WEAPONS_LEVEL3:
                    upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_WEAPONS;
                    break;
            }
        }
        else { //missing Abilities enum
            Print.print("Unknown Ability: " + upgradeAbility.getAbilityId() + " for upgrade: " + upgrade);
        }
        return upgradeAbility;
    }

    private Units getRequiredStructureType() {
        switch(upgrade) { //TODO: complete for all upgrades (that I don't currently use)
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3:
            case TERRAN_VEHICLE_WEAPONS_LEVEL1: case TERRAN_VEHICLE_WEAPONS_LEVEL2: case TERRAN_VEHICLE_WEAPONS_LEVEL3:
            case TERRAN_SHIP_WEAPONS_LEVEL1: case TERRAN_SHIP_WEAPONS_LEVEL2: case TERRAN_SHIP_WEAPONS_LEVEL3:
                return Units.TERRAN_ARMORY;
            case INFERNAL_PRE_IGNITERS: case CYCLONE_LOCK_ON_DAMAGE_UPGRADE:
                return Units.TERRAN_FACTORY_TECHLAB;
            case HISEC_AUTO_TRACKING: case TERRAN_BUILDING_ARMOR:
                return Units.TERRAN_ENGINEERING_BAY;
            case BANSHEE_CLOAK: case BANSHEE_SPEED:
                return Units.TERRAN_STARPORT_TECHLAB;
        }
        return Units.INVALID;
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

    private void selectProductionStructure() {
        Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == getRequiredStructureType()).stream()
                .filter(structure -> !PurchaseUpgrade.contains(structure))
                .min(Comparator.comparing(u -> UnitUtils.secondsUntilAvailable(u.unit())))
                .ifPresent(structure -> productionStructure = structure);
    }


    //******************************************
    //************ STATIC METHODS **************
    //******************************************

    public static void add(Upgrades newUpgrade) {
        if (!Purchase.isUpgradeQueued(newUpgrade) && !Bot.OBS.getUpgrades().contains(newUpgrade)) {
            KetrocBot.purchaseQueue.add(new PurchaseUpgrade(newUpgrade));
        }
    }

    public static void add(Upgrades newUpgrade, UnitInPool structure) {
        if (!Purchase.isUpgradeQueued(newUpgrade) && !Bot.OBS.getUpgrades().contains(newUpgrade)) {
            KetrocBot.purchaseQueue.add(new PurchaseUpgrade(newUpgrade, structure));
        }
    }

    public static boolean contains(UnitInPool structure) {
        return KetrocBot.purchaseQueue.stream()
                .anyMatch(purchase -> purchase instanceof PurchaseUpgrade &&
                        ((PurchaseUpgrade) purchase).getProductionStructure() != null &&
                        ((PurchaseUpgrade) purchase).getProductionStructure().getTag().equals(structure.getTag()));
    }

}
