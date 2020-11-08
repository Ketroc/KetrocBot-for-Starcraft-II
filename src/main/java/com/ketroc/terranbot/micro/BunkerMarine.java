package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;

public class BunkerMarine extends BasicUnitMicro {
    UnitInPool bunker;

    public BunkerMarine(UnitInPool unit, UnitInPool bunker) {
        super(unit, Position.towards(bunker.unit().getPosition().toPoint2d(), LocationConstants.proxyBarracksPos, 2f), false);
        this.bunker = bunker;
    }

    @Override
    public void onArrival() {
        if (bunker.unit().getBuildProgress() == 1f) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.SMART, bunker.unit(), false);
            removeMe = true;
        }
    }
}
