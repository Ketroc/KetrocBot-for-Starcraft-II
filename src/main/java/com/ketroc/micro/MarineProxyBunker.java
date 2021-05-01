package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.Position;

public class MarineProxyBunker extends Marine {
    public MarineProxyBunker(Unit unit, Point2d bunkerPos) {
        super(unit, Position.towards(bunkerPos, LocationConstants.proxyBarracksPos, 2f), MicroPriority.GET_TO_DESTINATION);
    }

    public MarineProxyBunker(UnitInPool unit, Point2d bunkerPos) {
        super(unit, Position.towards(bunkerPos, LocationConstants.proxyBarracksPos, 2f), MicroPriority.GET_TO_DESTINATION);
    }
}
