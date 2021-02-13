package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.DebugHelper;
import com.ketroc.terranbot.utils.Position;

public class LibToPosition extends Liberator {

    public LibToPosition(UnitInPool unit, Point2d targetPos, Point2d plannedLibZonePos) {
        super(unit, targetPos, plannedLibZonePos);
    }

    //TODO: handle other planned lib zone positions that don't care about where the liberator is positioned
    public LibToPosition(UnitInPool unit, Point2d plannedLibZonePos) {
        super(unit, plannedLibZonePos, plannedLibZonePos);
    }

    @Override
    public void onStep() {
        DebugHelper.boxUnit(unit.unit());

        //no lib
        if (unit == null || !isAlive()) {
            super.onStep();
            return;
        }

        //if currently changing modes
        if (isMorphing()) {
            return;
        }

        //unsiege
        if (unit.unit().getType() == Units.TERRAN_LIBERATOR_AG) {
            if (unsiegeMicro()) {
                return;
            }
        }

        //siege up
        if (unit.unit().getType() == Units.TERRAN_LIBERATOR && isSafe()) {
            if (siegeUpMicro()) {
                return;
            }
        }

        //normal micro
        super.onStep();
    }

    @Override
    public void onArrival() {

    }

}
