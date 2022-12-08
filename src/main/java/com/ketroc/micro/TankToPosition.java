package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;
//TODO: can this be removed or something??? It looks very similar to TankOffense
public class TankToPosition extends Tank {

    public TankToPosition(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    @Override
    public void onStep() {
        //no tank
        if (!isAlive()) {
            onDeath();
            return;
        }

        if (isMorphing()) {
            return;
        }

        randomFrameDelayToggle();

        //tank vs tank special case
        Unit enemyTankToSiege = getEnemyTankToSiege();
        if (enemyTankToSiege != null) {
            if (UnitUtils.getDistance(unit.unit(), enemyTankToSiege) - enemyTankToSiege.getRadius() * 2 > 12.9f) {
                if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                    unsiege();
                }
                else {
                    ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, enemyTankToSiege.getPosition().toPoint2d(), false);
                }
            }
            else if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK) {
                siege();
            }
            else if (UnitUtils.canScan() && UnitUtils.isSnapshot(enemyTankToSiege)) {
                scanEnemyTank(enemyTankToSiege);
            }
            return;
        }

        //unsiege
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
            if (doUnsiege()) {
                unsiege();
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
        if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }
    }

    @Override
    protected boolean doSiegeUp() {
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 0.1f) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
            return true;
        }
        return super.doSiegeUp();
    }


    @Override
    protected boolean doUnsiege() {
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 1) {
            return false;
        }
        return super.doUnsiege();
    }
}
