package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.DebugHelper;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;
//TODO: can this be removed or something??? It looks very similar to TankOffense
public class TankToPosition extends Tank {

    public TankToPosition(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    @Override
    public void onStep() {
        if (isMorphing()) {
            return;
        }

        DebugHelper.boxUnit(unit.unit());

        //no tank
        if (unit == null || !unit.isAlive()) {
            super.onStep();
            return;
        }

        //tank vs tank special case
        Unit enemyTankToSiege = getEnemyTankToSiege();
        if (enemyTankToSiege != null) {
            if (UnitUtils.getDistance(unit.unit(), enemyTankToSiege) > 12.9f + enemyTankToSiege.getRadius()*2) {
                if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                    unsiege();
                }
                else {
                    ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, enemyTankToSiege, false);
                }
            }
            else {
                siege();
            }
            return;
        }

        //unsiege
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
            UnitInPool enemyTank = getClosestEnemySiegedTankInRange();
            if (enemyTank != null &&
                    ArmyManager.prevScanFrame + 24 < Time.nowFrames() &&
                    UnitUtils.isInFogOfWar(enemyTank) &&
                    UnitUtils.numScansAvailable() > 0) {
                scanEnemyTank(enemyTank);
            }
            else if (doUnsiege()) {
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
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 1 || !getEnemiesInRange(13).isEmpty()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
            return true;
        }
        return false;
    }
}
