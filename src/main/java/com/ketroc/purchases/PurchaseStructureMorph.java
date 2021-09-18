package com.ketroc.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.models.Base;
import com.ketroc.models.Cost;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.Print;
import com.ketroc.utils.UnitUtils;

import java.util.Set;

public class PurchaseStructureMorph implements Purchase {
    private static final float CANCEL_THRESHOLD = 0.4f;

    private Abilities morphOrAddOn;
    private UnitInPool structure;
    private Cost cost;

    // ============= CONSTRUCTORS =============
    public PurchaseStructureMorph(Abilities morphOrAddOn, Unit structure) {
        this(morphOrAddOn, Bot.OBS.getUnit(structure.getTag()));
    }

    public PurchaseStructureMorph(Abilities morphOrAddOn, UnitInPool structure) {
        this.morphOrAddOn = morphOrAddOn;
        this.structure = structure;
        setCost();
        Print.print("Added to queue: " + this.morphOrAddOn);
    }

    public static void remove(Tag structureTag) {
        KetrocBot.purchaseQueue.removeIf(p ->
                p instanceof PurchaseStructureMorph &&
                ((PurchaseStructureMorph) p).structure.getTag().equals(structureTag));
    }


    // ========== GETTERS AND SETTERS ============

    public Ability getMorphOrAddOn() {
        return morphOrAddOn;
    }

    public void setMorphOrAddOn(Abilities morphOrAddOn) {
        this.morphOrAddOn = morphOrAddOn;
    }

    public UnitInPool getStructure() {
        return structure;
    }

    public void setStructure(UnitInPool structure) {
        this.structure = structure;
    }

    @Override
    public Cost getCost() {
        return cost;
    }

    // =========== METHODS ============

    public PurchaseResult build() {
        if (structure == null || !structure.isAlive()) {
            return PurchaseResult.CANCEL;
        }

        if (structureAlreadyMorphing()) {
            return PurchaseResult.CANCEL;
        }

        //if tech structure required
        if (isTechRequired(morphOrAddOn)) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        //if production under 40% and can afford if unit production is cancelled TODO: doesn't account for which cc
        if (shouldCancelPreviousOrder()) {
            Print.print("cancelled unit");
            ActionHelper.unitCommand(structure.unit(), Abilities.CANCEL_LAST, false);
            GameCache.mineralBank += 50;
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        //if can't afford it
        if (!canAfford()) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        //if structure not producing unit and can afford morph TODO: this is hardcoded to scv production (not valid for cancelling factory production etc)
        if (!structure.unit().getActive().orElse(true)) {
            Print.print("start building " + this.morphOrAddOn.toString());
            Print.print("sending action " + this.morphOrAddOn);
            ActionHelper.unitCommand(structure.unit(), this.morphOrAddOn, false);
            Cost.updateBank(cost);
            if (morphOrAddOn == Abilities.MORPH_PLANETARY_FORTRESS) {
                Base.setBaseMorphTime(structure.unit());
            }
            return PurchaseResult.SUCCESS;
        }

        //if canafford but structure is busy
        Cost.updateBank(cost);
        return PurchaseResult.WAITING;
    }

    private boolean structureAlreadyMorphing() {
        return ActionIssued.getCurOrder(structure).stream()
                .anyMatch(unitOrder -> unitOrder.ability == morphOrAddOn);
    }

    private boolean shouldCancelPreviousOrder() {
        if (this.structure.unit().getOrders().isEmpty() ||
                this.structure.unit().getOrders().get(0).getAbility() != Abilities.TRAIN_SCV) {
            return false;
        }

        int minerals = GameCache.mineralBank;
        int gas = GameCache.gasBank;
        UnitOrder order = this.structure.unit().getOrders().get(0);
        UnitTypeData producingUnitData = Bot.OBS.getUnitTypeData(false).get(Bot.abilityToUnitType.get(order.getAbility()));
        if (minerals + producingUnitData.getMineralCost().get() >= cost.minerals &&
                gas + producingUnitData.getVespeneCost().get() >= cost.gas &&
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
}
