package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.*;

import java.util.List;

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

        randomFrameDelayToggle();

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
            return true; //isUnsiegeWaitTimeComplete(); changed cuz when retreating, just go immediately (don't stagger)
        }

//        //don't unsiege if this is the last tank sieged nearby
//        List<UnitInPool> nearbyTanks = UnitUtils.getUnitsNearbyOfType(
//                Alliance.SELF,
//                UnitUtils.SIEGE_TANK_TYPE,
//                unit.unit().getPosition().toPoint2d(),
//                6
//        );
//        boolean unsiegedTankNearby = nearbyTanks.stream()
//                .anyMatch(u -> u.unit().getType() == Units.TERRAN_SIEGE_TANK);
//        boolean siegedTankNearby = nearbyTanks.stream()
//                .anyMatch(u -> u.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED);
//
//        if (unsiegedTankNearby && !siegedTankNearby) {
//            return false;
//        }

        //don't unsiege if this is the closest tank to attackPos and enemy is nearby
        if (ArmyManager.attackGroundPos != null &&
                UnitUtils.getDistance(unit.unit(), ArmyManager.attackGroundPos) < 20 &&
                UnitUtils.getDistance(unit.unit(), PosConstants.enemyRampPos) > 5 &&
                UnitMicroList.getUnitSubList(TankOffense.class).size() > 1) {
            Unit leadSiegedTank = UnitUtils.getClosestUnitOfType(Alliance.SELF, Units.TERRAN_SIEGE_TANK_SIEGED, ArmyManager.attackGroundPos);
            if (leadSiegedTank.getTag().equals(unit.getTag())) {
                return false;
            }
        }

        return super.doUnsiege();
    }
}
