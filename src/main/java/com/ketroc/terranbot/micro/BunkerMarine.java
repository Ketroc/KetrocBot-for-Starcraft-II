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

public class BunkerMarine extends BasicUnitMicro {
    public BunkerMarine(Unit unit, Point2d bunkerPos) {
        super(unit, Position.towards(bunkerPos, LocationConstants.proxyBarracksPos, 2f), MicroPriority.GET_TO_DESTINATION);
    }

    public BunkerMarine(UnitInPool unit, Point2d bunkerPos) {
        super(unit, Position.towards(bunkerPos, LocationConstants.proxyBarracksPos, 2f), MicroPriority.GET_TO_DESTINATION);
    }

    @Override
    public void onArrival() {
        Unit bunker = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, targetPos, 3f)
                .stream()
                .findFirst()
                .map(UnitInPool::unit)
                .orElse(null);
        if (bunker != null && bunker.getBuildProgress() == 1f) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.SMART, bunker, false);
            removeMe = true;
        }
    }
}
