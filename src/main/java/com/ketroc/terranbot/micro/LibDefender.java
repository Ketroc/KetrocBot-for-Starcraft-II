package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.Position;

public class LibDefender extends BasicMover {
    Point2d ccPos;

    public LibDefender(UnitInPool unit, Point2d targetPos, Point2d ccPos) {
        super(unit, targetPos);
        this.ccPos = ccPos;
    }

    @Override
    public void onCompletion() {
        super.onCompletion();
        Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, Position.towards(targetPos, ccPos, -2), false)
                .unitCommand(unit.unit(), Abilities.MORPH_LIBERATOR_AG_MODE, Position.towards(targetPos, ccPos, 5), true);
    }
}
