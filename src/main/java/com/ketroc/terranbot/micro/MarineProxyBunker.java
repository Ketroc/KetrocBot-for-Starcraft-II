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

public class MarineProxyBunker extends Marine {
    public MarineProxyBunker(Unit unit, Point2d bunkerPos) {
        super(unit, Position.towards(bunkerPos, LocationConstants.proxyBarracksPos, 2f), MicroPriority.GET_TO_DESTINATION);
    }

    public MarineProxyBunker(UnitInPool unit, Point2d bunkerPos) {
        super(unit, Position.towards(bunkerPos, LocationConstants.proxyBarracksPos, 2f), MicroPriority.GET_TO_DESTINATION);
    }
}
