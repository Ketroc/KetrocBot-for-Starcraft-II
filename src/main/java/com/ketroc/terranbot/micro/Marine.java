package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;

import java.util.List;

public class Marine extends BasicUnitMicro {
    public Marine(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public Marine(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onArrival() {

    }

    public static void setTargetPos(Point2d newTargetPos) {
        List<Marine> marineList = UnitMicroList.getUnitSubList(Marine.class);
        marineList.forEach(marine -> marine.targetPos = newTargetPos);
    }

}
