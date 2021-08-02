package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.managers.ArmyManager;

//TODO: complete (this is currently just a copy of TankOffense
public class LibOffense extends Liberator {

    public LibOffense(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, null);
    }

    @Override
    public void onStep() {
        //DebugHelper.boxUnit(unit.unit());
        targetPos = ArmyManager.attackGroundPos;

        //no liberator
        if (!isAlive()) {
            onDeath();
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
