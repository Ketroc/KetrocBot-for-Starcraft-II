package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

//old way of assigning repair bay to main base
public class ScvRepairBayMain extends Scv {
    public ScvRepairBayMain(UnitInPool scv) {
        super(scv, PosConstants.REPAIR_BAY, MicroPriority.SURVIVAL);
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
