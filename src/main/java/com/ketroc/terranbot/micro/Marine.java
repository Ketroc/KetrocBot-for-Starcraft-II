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

    public static void setTargetPos(Point2d newTargetPos) {
        List<Marine> marineList = UnitMicroList.getUnitSubList(Marine.class);
        marineList.forEach(marine -> marine.targetPos = newTargetPos);
    }

}
