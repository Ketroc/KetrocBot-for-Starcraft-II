package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.UnitUtils;

public class ScvRepairer extends Scv {
    UnitInPool repairTarget;

    public ScvRepairer(Unit unit, UnitInPool repairTarget) {
        super(unit, repairTarget.unit().getPosition().toPoint2d(), MicroPriority.GET_TO_DESTINATION);
        this.repairTarget = repairTarget;
    }

    public ScvRepairer(UnitInPool unit, UnitInPool repairTarget) {
        super(unit, repairTarget.unit().getPosition().toPoint2d(), MicroPriority.GET_TO_DESTINATION);
        this.repairTarget = repairTarget;
    }

    @Override
    public void onStep() {
        if (!isAlive() || !repairTarget.isAlive() || isTargetFullHealth() || Bot.OBS.getMinerals() < 5) {
            ActionHelper.unitCommand(unit.unit(), Abilities.STOP, false);
            removeMe = true;
            return;
        }

        if (!isTargettingUnit(repairTarget.unit())) {
            ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_REPAIR, repairTarget.unit(), false);
            return;
        }
    }

    protected boolean isTargetFullHealth() {
        return repairTarget.unit().getHealth().orElse(0f) + 0.1f >= repairTarget.unit().getHealthMax().orElse(9999f);
    }

    public static int getPendingRepairCost() {
        return UnitMicroList.getUnitSubList(ScvRepairer.class).stream()
                .mapToInt(scv -> (int)Math.ceil(UnitUtils.getHealthPercentage(scv.repairTarget.unit()) * 11.25))
                .sum();
    }
}
