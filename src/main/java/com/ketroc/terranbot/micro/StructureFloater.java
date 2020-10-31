package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;

public class StructureFloater extends BasicUnitMicro {

    public StructureFloater(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, true);
    }

    @Override
    public void onCompletion() {
        super.onCompletion();
        Bot.ACTION.unitCommand(unit.unit(), Abilities.LAND, targetPos, false);
    }
}
