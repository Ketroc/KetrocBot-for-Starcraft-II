package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;

public class RepairBayScv extends BasicUnitMicro {
    public RepairBayScv(UnitInPool scv) {
        super(scv, LocationConstants.REPAIR_BAY, true);
    }

    @Override
    public void onArrival() {
        Abilities order = UnitUtils.getOrder(unit.unit());
        if (order == Abilities.MOVE || order == Abilities.HARVEST_GATHER || order == Abilities.HARVEST_RETURN) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.ATTACK, targetPos, false);
        }
        else if (order != Abilities.ATTACK && order != Abilities.EFFECT_REPAIR) {
            removeMe = true;
            Bot.ACTION.toggleAutocast(unit.getTag(), Abilities.EFFECT_REPAIR_SCV);
        }
    }
}
