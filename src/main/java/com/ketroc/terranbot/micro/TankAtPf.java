package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.DebugHelper;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.function.Predicate;

public class TankAtPf extends Tank {

    public TankAtPf(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos);
    }

    @Override
    public void onStep() {
        DebugHelper.boxUnit(unit.unit());

        //no tank
        if (unit == null || !unit.isAlive()) {
            super.onStep();
            return;
        }

        //unsiege
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
            unsiegeMicro();
            return;
        }

        //siege up
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK) {
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
