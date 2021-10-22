package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

//old way of assigning repairbay to main base
public class RepairBayScv extends BasicUnitMicro {
    public RepairBayScv(UnitInPool scv) {
        super(scv, LocationConstants.REPAIR_BAY, MicroPriority.SURVIVAL);
        Base.releaseScv(scv.unit());
        Bot.ACTION.toggleAutocast(unit.getTag(), Abilities.EFFECT_REPAIR_SCV);
    }

    @Override
    public void onArrival() { //TODO: handle scvs that run out of repair bay to repair a unit (get rid of auto-repair?)
        Abilities order = UnitUtils.getOrder(unit.unit());
        if (order == Abilities.MOVE || order == Abilities.HARVEST_GATHER || order == Abilities.HARVEST_RETURN) {
            if (ArmyManager.getNumRepairBayUnits() == 0) {
                endScvRepair();
            }
            else {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, targetPos, false);
            }
        }
        else if (order != Abilities.ATTACK && order != Abilities.EFFECT_REPAIR) {
            endScvRepair();
        }
    }

    public void endScvRepair() {
        Bot.ACTION.toggleAutocast(unit.getTag(), Abilities.EFFECT_REPAIR_SCV);
        UnitUtils.returnAndStopScv(unit);
        removeMe = true;
    }
}
