package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.utils.ActionHelper;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.UnitUtils;

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
                Bot.ACTION.toggleAutocast(unit.getTag(), Abilities.EFFECT_REPAIR_SCV);
                removeMe = true;
            }
            else {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, targetPos, false);
            }
        }
        else if (order != Abilities.ATTACK && order != Abilities.EFFECT_REPAIR) {
            removeMe = true;
            Bot.ACTION.toggleAutocast(unit.getTag(), Abilities.EFFECT_REPAIR_SCV);
        }
    }
}
