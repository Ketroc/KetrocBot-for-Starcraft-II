package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;

public class TankDefender extends BasicUnitMicro {

    public TankDefender(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, true);
    }

    @Override
    public void onCompletion() {
        Bot.ACTION.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
    }
}
