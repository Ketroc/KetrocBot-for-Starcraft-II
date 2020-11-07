package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

public class PurchaseStructureMorph implements Purchase {
    private static final float CANCEL_THRESHOLD = 0.4f;

    private Ability morphOrAddOn;
    private UnitInPool structure;
    private UnitTypeData structureData;
    private UnitTypeData morphData;
    private Cost cost;

    // ============= CONSTRUCTORS =============
    public PurchaseStructureMorph(Ability morphOrAddOn, Unit structure) {
        this(morphOrAddOn, Bot.OBS.getUnit(structure.getTag()));
    }

    public PurchaseStructureMorph(Ability morphOrAddOn, UnitInPool structure) {
        this.morphOrAddOn = morphOrAddOn;
        this.structure = structure;
        structureData = Bot.OBS.getUnitTypeData(false).get(structure.unit().getType());
        morphData = Bot.OBS.getUnitTypeData(false).get(Bot.abilityToUnitType.get(morphOrAddOn));
        setCost();
        System.out.println("Added to queue: " + this.morphOrAddOn);
    }


    // ========== GETTERS AND SETTERS ============

    public Ability getMorphOrAddOn() {
        return morphOrAddOn;
    }

    public void setMorphOrAddOn(Ability morphOrAddOn) {
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
        if (!structure.isAlive()) {
            return PurchaseResult.CANCEL;
        }
        //if production under 40% and can afford if cancelled TODO: doesn't account for which cc
        if (shouldCancelPreviousOrder()) {
            System.out.println("cancelled unit");
            Bot.ACTION.unitCommand(structure.unit(), Abilities.CANCEL_LAST, false);
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
        if (structure.unit().getOrders().isEmpty()) {
            System.out.println("start building " + this.morphOrAddOn.toString());
            System.out.println("sending action " + this.morphOrAddOn + " at: " + Time.nowClock());
            Bot.ACTION.unitCommand(structure.unit(), this.morphOrAddOn, false);
            Cost.updateBank(cost);
            return PurchaseResult.SUCCESS;
        }

        //if canafford but structure is busy
        Cost.updateBank(cost);
        return PurchaseResult.WAITING;
    }

    private boolean shouldCancelPreviousOrder() {
        if (UnitUtils.getOrder(structure.unit()) == Abilities.TRAIN_SCV) {
            int minerals = GameCache.mineralBank;
            int gas = GameCache.gasBank;

            UnitOrder order = this.structure.unit().getOrders().get(0);
            UnitTypeData producingUnitData = Bot.OBS.getUnitTypeData(false).get(Bot.abilityToUnitType.get(order.getAbility()));
            if (minerals + producingUnitData.getMineralCost().get() >= cost.minerals &&
                    gas + producingUnitData.getVespeneCost().get() >= cost.gas &&
                    order.getProgress().get() < CANCEL_THRESHOLD) {
                return true;
            }
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
}
