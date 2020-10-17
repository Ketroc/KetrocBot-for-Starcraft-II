package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

public class IgnoredScvDefender extends Ignored {
    public UnitInPool target;

    public IgnoredScvDefender(Tag unitTag, UnitInPool target) {
        super(unitTag);
        this.target = target;
    }

    @Override
    public boolean doReleaseUnit() {
        if (!target.isAlive() ||
                target.getLastSeenGameLoop() != Bot.OBS.getGameLoop() ||
                UnitUtils.getDistance(target.unit(), LocationConstants.baseLocations.get(0)) >= 40) {
            Bot.ACTION.unitCommand(unitTag, Abilities.STOP, false);
            return true;
        }
        return false;
    }

}
