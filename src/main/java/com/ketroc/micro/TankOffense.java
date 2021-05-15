package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.DebugHelper;
import com.ketroc.utils.UnitUtils;

public class TankOffense extends Tank {

    public TankOffense(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos);
    }

    @Override
    public void onStep() {
        DebugHelper.boxUnit(unit.unit());
        updateTargetPos();

        //no tank
        if (unit == null || !unit.isAlive()) {
            super.onStep();
            return;
        }

        //tank vs tank special case
        Unit enemyTankToSiege = getEnemyTankToSiege();
        if (enemyTankToSiege != null) {
            if (UnitUtils.getDistance(unit.unit(), enemyTankToSiege) - enemyTankToSiege.getRadius()*2 > 12.9f) {
                if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                    ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_UNSIEGE,false);
                }
                else {
                    ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, enemyTankToSiege, false);
                }
            }
            else {
                ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE,false);
            }
            return;
        }

        //unsiege
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
            if (doUnsiege()) {
                return;
            }
        }

        //siege up
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK && isSafe()) {
            if (doSiegeUp()) {
                return;
            }
        }

        //normal micro
        super.onStep();
    }

    @Override
    public void onArrival() {
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 0.5) {
            //keep one siege tanked at position while waiting for enemies to arrive
            //NOTE: probably only 1 tank will get within 1 range of targetPos
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
        }
        else if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }
    }
}
