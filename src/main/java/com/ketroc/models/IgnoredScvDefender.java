package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.bots.Bot;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

public class IgnoredScvDefender extends Ignored {
    public UnitInPool target;

    public IgnoredScvDefender(Tag unitTag, UnitInPool target) {
        super(unitTag);
        this.target = target;
    }

    @Override
    public boolean doReleaseUnit() {
        if (!target.isAlive() ||
                target.getLastSeenGameLoop() != Time.nowFrames() ||
                UnitUtils.getDistance(target.unit(), LocationConstants.baseLocations.get(0)) >= 40) {
            UnitInPool scv = Bot.OBS.getUnit(unitTag);
            if (scv != null) {
                UnitUtils.returnAndStopScv(scv);
            }
            return true;
        }
        return false;
    }

}
