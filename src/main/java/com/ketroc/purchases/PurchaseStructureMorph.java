package com.ketroc.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.launchers.Launcher;
import com.ketroc.models.Base;
import com.ketroc.models.Cost;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.Set;

public class PurchaseStructureMorph implements Purchase {
    private static final float CANCEL_THRESHOLD = 0.4f;

    private Abilities morphOrAddOn;
    private UnitInPool productionStructure;
    private Cost cost;

    // ============= CONSTRUCTORS =============
    public PurchaseStructureMorph(Abilities morphOrAddOn, Unit productionStructure) {
        this(morphOrAddOn, Bot.OBS.getUnit(productionStructure.getTag()));
    }

    public PurchaseStructureMorph(Abilities morphOrAddOn, UnitInPool productionStructure) {
        this.morphOrAddOn = morphOrAddOn;
        this.productionStructure = productionStructure;
        setCost();
        Print.print("Added to queue: " + this.morphOrAddOn);
    }

    public PurchaseStructureMorph(Abilities morphOrAddOn) {
        this.morphOrAddOn = morphOrAddOn;
        setCost();
    }

    public static void remove(Tag structureTag) {
        KetrocBot.purchaseQueue.removeIf(p ->
                p instanceof PurchaseStructureMorph &&
                ((PurchaseStructureMorph) p).productionStructure != null &&
                ((PurchaseStructureMorph) p).productionStructure.getTag().equals(structureTag));
    }

    // ========== GETTERS AND SETTERS ============

    public Ability getMorphOrAddOn() {
        return morphOrAddOn;
    }

    public void setMorphOrAddOn(Abilities morphOrAddOn) {
        this.morphOrAddOn = morphOrAddOn;
    }

    public UnitInPool getProductionStructure() {
        return productionStructure;
    }

    public void setProductionStructure(UnitInPool productionStructure) {
        this.productionStructure = productionStructure;
    }

    @Override
    public Cost getCost() {
        return cost;
    }

    // =========== METHODS ============

    public PurchaseResult build() {
        //TODO: handle null productionStructure up here then remove all the null checks below

        //cancel purchase if structure died or morph already in progress
        if (productionStructure != null &&
                (!productionStructure.isAlive() || structureAlreadyMorphing())) {
            return PurchaseResult.CANCEL;
        }

        //if tech structure required
        if (isTechRequired(morphOrAddOn)) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        //if structure already has an add-on
        if (productionStructure != null && productionStructure.unit().getAddOnTag().isPresent()) {
            return PurchaseResult.CANCEL;
        }

        //if add-on position is blocked
        if (isBlocked()) {
            return PurchaseResult.WAITING;
        }

        //if production under 40% and can afford if unit production is cancelled TODO: doesn't account for which cc
        if (shouldCancelPreviousOrder()) {
            Print.print("cancelled unit");
            ActionHelper.unitCommand(productionStructure.unit(), Abilities.CANCEL_LAST, false);
            GameCache.mineralBank += 50;
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        //if can't afford it
        if (!canAfford()) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        //select production structure
        if (productionStructure == null) {
            selectProductionStructure();
            if (productionStructure == null) {
                return PurchaseResult.CANCEL;
            }
        }

        //wait for structure to be available and decide whether to set funds aside
        if (productionStructure != null &&
                (productionStructure.unit().getBuildProgress() < 1f || UnitUtils.getOrder(productionStructure.unit()) != null)) {
            if (UnitUtils.framesUntilAvailable(productionStructure.unit()) < 100) {
                Cost.updateBank(cost);
            }
            return PurchaseResult.WAITING;
        }

        Print.print("start building " + this.morphOrAddOn.toString());
        Print.print("sending action " + this.morphOrAddOn);
        ActionHelper.unitCommand(productionStructure.unit(), this.morphOrAddOn, false);
        Cost.updateBank(cost);
        if (morphOrAddOn == Abilities.MORPH_PLANETARY_FORTRESS) {
            Base.setBaseMorphTime(productionStructure.unit());
        }
        return PurchaseResult.SUCCESS;
    }

    private boolean structureAlreadyMorphing() {
        return ActionIssued.getCurOrder(productionStructure).stream()
                .anyMatch(unitOrder -> unitOrder.ability == morphOrAddOn);
    }

    private boolean shouldCancelPreviousOrder() {
//        if (Launcher.isRealTime) { //FIXME: this is a hack to hopefully negate the constant cancel scv + build scv loop that happens when trying to morph OC/PF
//            return false;
//        }
        if (productionStructure == null || productionStructure.unit().getOrders().isEmpty()) {
            return false;
        }
        UnitOrder order = productionStructure.unit().getOrders().get(0);
        if (order.getAbility() != Abilities.TRAIN_SCV) {
            return false;
        }

        UnitTypeData producingUnitData = Bot.OBS.getUnitTypeData(false).get(Bot.abilityToUnitType.get(order.getAbility()));
        if (GameCache.mineralBank + producingUnitData.getMineralCost().get() >= cost.minerals &&
                GameCache.gasBank + producingUnitData.getVespeneCost().get() >= cost.gas &&
                order.getProgress().get() < CANCEL_THRESHOLD) {
            return true;
        }
        return false;
    }

    @Override
    public void setCost() {
        cost = Cost.getUnitCost(Bot.abilityToUnitType.get(morphOrAddOn));
    }

    @Override
    public boolean canAfford() {
        return GameCache.mineralBank >= cost.minerals && GameCache.gasBank >= cost.gas;
    }

    @Override
    public String getType() {
        return morphOrAddOn.toString();
    }

    private void selectProductionStructure() {
        Units unitType = Bot.abilityToUnitType.get(morphOrAddOn);
        Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == UnitUtils.getRequiredStructureType(unitType)).stream()
                .filter(u -> (!unitType.toString().contains("_REACTOR") && !unitType.toString().contains("_TECHLAB")) ||
                        UnitUtils.getAddOn(u.unit()).isEmpty())
                .min(Comparator.comparing(u -> UnitUtils.secondsUntilAvailable(u.unit())))
                .ifPresent(structure -> productionStructure = structure);
    }

    public boolean isBlocked() {
        if (productionStructure == null || morphOrAddOn.toString().contains("MORPH")) {
            return false;
        }
        return !Bot.QUERY.placement(Abilities.BUILD_SUPPLY_DEPOT, UnitUtils.getAddonPos(productionStructure.unit()));
    }


    // ***************************************
    // ********** STATIC METHODS *************
    // ***************************************

    public static boolean isTechRequired(Abilities morphType) {
        Units techStructureNeeded = null;
        switch (morphType) {
            case MORPH_ORBITAL_COMMAND:
                techStructureNeeded = Units.TERRAN_BARRACKS;
                break;
            case MORPH_PLANETARY_FORTRESS:
                techStructureNeeded = Units.TERRAN_ENGINEERING_BAY;
                break;
        }
        if (techStructureNeeded == null) {
            return false;
        }
        Set<Units> techStructureUnitsSet = UnitUtils.getUnitTypeSet(techStructureNeeded);
        if (UnitUtils.numMyUnits(techStructureUnitsSet, false) == 0) {
            if (!Purchase.isStructureQueued(techStructureNeeded) &&
                    UnitUtils.numMyUnits(techStructureUnitsSet, true) == 0) {
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(techStructureNeeded));
            }
            return true;
        }
        return false;
    }

    public static boolean contains(Unit structure) {
        return KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseStructureMorph)
                .map(p -> ((PurchaseStructureMorph)p).getProductionStructure())
                .anyMatch(s -> s != null && s.getTag().equals(structure.getTag()));
    }

}
