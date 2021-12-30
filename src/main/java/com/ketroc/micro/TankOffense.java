package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.*;

public class TankOffense extends Tank {

    public TankOffense(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos);
    }

    @Override
    public void onStep() {
        //no tank
        if (!isAlive()) {
            onDeath();
            return;
        }

        //ignore when morphing
        if (isMorphing()) {
            return;
        }

        //DebugHelper.boxUnit(unit.unit());
        updateTargetPos();

        //tank vs tank special case
        Unit enemyTankToSiege = getEnemyTankToSiege();
        if (enemyTankToSiege != null) {
            if (UnitUtils.getDistance(unit.unit(), enemyTankToSiege) - enemyTankToSiege.getRadius() * 2 > 12.95f) {
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
            else if (ArmyManager.prevScanFrame + 24 < Time.nowFrames() &&
                    UnitUtils.isSnapshot(enemyTankToSiege) &&
                    UnitUtils.numScansAvailable() > 0) {
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
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK && isSafe() && !isRetreating()) {
            if (doSiegeUp()) {
                siege();
                return;
            }
        }

        //normal micro
        super.onStep();
    }

    @Override
    public void onArrival() {
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 1.3f) {
            //keep one siege tanked at position while waiting for enemies to arrive
            //NOTE: probably only 1 tank will get within 1 range of targetPos
            siege();
        }
        else if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }
    }

    @Override
    protected boolean doUnsiege() {
        //unsiege immediately if retreating
        if (!ArmyManager.doOffense &&
                UnitUtils.getDistance(unit.unit(), ArmyManager.attackGroundPos) > 15 && //attackGroundPos is home pos or enemy units near my bases
                getEnemyTargetsInRange(11).isEmpty()) {
            return isUnsiegeWaitTimeComplete();
        }

        return super.doUnsiege();
    }
}
