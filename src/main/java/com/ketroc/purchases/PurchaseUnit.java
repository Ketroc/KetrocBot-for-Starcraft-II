package com.ketroc.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.BuildManager;
import com.ketroc.models.Cost;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;

public class PurchaseUnit implements Purchase {
    private Cost cost;
    private Units unitType;
    private UnitInPool productionStructure;

    public PurchaseUnit(Units unitType) {
        this.unitType = unitType;
        BuildManager.openingStarportUnits.remove(unitType);
        BuildManager.openingFactoryUnits.remove(unitType);
        setCost();
    }

    public PurchaseUnit(Units unitType, Unit productionStructure) {
        this.unitType = unitType;
        this.productionStructure = Bot.OBS.getUnit(productionStructure.getTag());
        setCost();
    }

    public PurchaseUnit(Units unitType, UnitInPool productionStructure) {
        this.unitType = unitType;
        this.productionStructure = productionStructure;
        setCost();
    }

    @Override
    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public Units getUnitType() {
        return unitType;
    }

    public void setUnitType(Units unitType) {
        this.unitType = unitType;
    }

    public UnitInPool getProductionStructure() {
        return productionStructure;
    }

    public void setProductionStructure(UnitInPool productionStructure) {
        this.productionStructure = productionStructure;
    }

    @Override
    public PurchaseResult build() {
        if (productionStructure != null && !productionStructure.isAlive()) {
            productionStructure = null;
        }
        if (canAfford()) {
            if (productionStructure == null) {
                selectProductionStructure();
            }
            if (productionStructure != null &&
                    !productionStructure.unit().getActive().orElse(true) &&
                    productionStructure.unit().getBuildProgress() == 1 &&
                    (!UnitUtils.requiresTechLab(unitType) || isAddOnComplete())) {
                ActionHelper.unitCommand(productionStructure.unit(), Bot.OBS.getUnitTypeData(false).get(unitType).getAbility().get(), false);
                Cost.updateBank(cost);
                return PurchaseResult.SUCCESS;
            }
        }
        //if production structure exists
        if (!Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == UnitUtils.getRequiredStructureType(unitType)).isEmpty()) {
            Cost.updateBank(cost);
        }
        return PurchaseResult.WAITING;
    }

    private boolean isAddOnComplete() {
        return productionStructure.unit().getAddOnTag().isPresent();
    }

    private void selectProductionStructure() {
        Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == UnitUtils.getRequiredStructureType(unitType)).stream()
                .filter(u -> !UnitUtils.requiresTechLab(unitType) || UnitUtils.getAddOn(u.unit()).isPresent())
                //.filter(u -> !PurchaseUnit.contains(u)) //commented out as it's been replaced to handle timing out PurchaseUnits in purchase queue
                .min(Comparator.comparing(u -> UnitUtils.secondsUntilAvailable(u.unit())))
                .ifPresent(structure -> productionStructure = structure);
    }

    @Override
    public boolean canAfford() {
        return GameCache.mineralBank >= cost.minerals && GameCache.gasBank >= cost.gas;
    }

    @Override
    public void setCost() {
        cost = Cost.getUnitCost(unitType);
    }

    @Override
    public String getType() {
        return unitType.toString();
    }

    // ***************************************
    // ********** STATIC METHODS *************
    // ***************************************

    public static boolean contains(UnitInPool structure) {
        return KetrocBot.purchaseQueue.stream()
                .anyMatch(purchase -> purchase instanceof PurchaseUnit &&
                        ((PurchaseUnit) purchase).getProductionStructure() != null &&
                        ((PurchaseUnit) purchase).getProductionStructure().getTag().equals(structure.getTag()));
    }
}
