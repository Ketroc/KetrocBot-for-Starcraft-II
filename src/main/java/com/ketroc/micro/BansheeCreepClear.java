package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.Comparator;

public class BansheeCreepClear extends Banshee {
    protected float prevAngle;
    private int tumorCount;

    public BansheeCreepClear(Unit banshee) {
        super(banshee, MicroPriority.SURVIVAL);
        doDetourAroundEnemy = true;
        prevAngle = Position.getAngle(unit.unit().getPosition().toPoint2d(), PosConstants.enemyMainBaseMidPos);
    }

    public void incrementTumorCount() {
        tumorCount++;
    }

    @Override
    protected void setTargetPos() {
        //if near a valid tumor then tumorPos
        if (isCreepTumorInRange()) {
            Point2d tumorPos = UnitUtils.getClosestUnitOfType(
                    Alliance.ENEMY,
                    UnitUtils.CREEP_TUMOR_TYPES,
                    unit.unit().getPosition().toPoint2d()
            ).getPosition().toPoint2d();
            targetPos = Position.towards(tumorPos, unit.unit(), 5.5f);
            return;
        }

        //if away from raven, then ravenPos
        Point2d ravenPos = UnitMicroList.getUnitSubList(RavenCreepClear.class).stream()
                .findFirst()
                .map(raven -> raven.unit.unit().getPosition().toPoint2d())
                .filter(pos -> UnitUtils.getDistance(unit.unit(), pos) > 5)
                .orElse(null);
        if (ravenPos != null) {
            targetPos = ravenPos;
            return;
        }

        //otherwise enemy main
        targetPos = PosConstants.enemyMainBaseMidPos;
    }

    @Override
    public void onArrival() {

    }

    @Override
    public UnitInPool selectTarget() {
        //priority on creep tumors
        UnitInPool targetUnit =  UnitUtils.getEnemyTargetsInRange(unit.unit(), target -> UnitUtils.CREEP_TUMOR_TYPES.contains(target.unit().getType()))
                .stream()
                .min(Comparator.comparing(tumour -> tumour.unit().getHealth().orElse(500f)))
                .orElse(null);

        //otherwise, pick a target in range
        if (targetUnit == null) {
            targetUnit = super.selectTarget();
        }
        return targetUnit;
    }

    @Override
    public void onStep() {
        if (!isAlive()) {
            onDeath();
            tumorReport();
            return;
        }

        if (UnitUtils.getHealthPercentage(unit.unit()) <= Strategy.RETREAT_HEALTH) {
            removeMe = true;
            tumorReport();
            return;
        }

        setTargetPos();

        //done if unit is immobile
        if (!UnitUtils.canMove(unit.unit())) {
            return;
        }

        if (shouldCloak()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            return;
        }

        //flee from closest cyclone, if locked on
        if (hasLockOnBuff()) {
            Unit nearestCyclone = UnitUtils.getClosestEnemyOfType(Units.TERRAN_CYCLONE, unit.unit().getPosition().toPoint2d());
            if (nearestCyclone != null) {
                targetPos = Position.towards(unit.unit().getPosition().toPoint2d(), nearestCyclone.getPosition().toPoint2d(), -4);
                return;
            }
        }

        //attack if available and safe
        if (attackIfAvailable() && isSafe()) {
            return;
        }

        //movement
        Point2d movePos = getMovementPos(4f);
        DebugHelper.drawLine(
                unit.unit().getPosition().toPoint2d(),
                Position.towards(unit.unit().getPosition().toPoint2d(), movePos, 4),
                Color.BLUE
        );
        prevAngle = Position.getAngle(unit.unit().getPosition().toPoint2d(), movePos);
        ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, movePos, false);
    }

    // safe and stay near edge of creep
    protected boolean isGoodNextPos(Point2d pos) {
        return isSafe(pos) && (!Bot.OBS.hasCreep(pos) || isCreepTumorInRange());
    }

    private boolean isCreepTumorInRange() {
        Point2d bansheePos = unit.unit().getPosition().toPoint2d();
        Unit closestTumor = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, UnitUtils.CREEP_TUMOR_TYPES, bansheePos);
        if (closestTumor == null) {
            return false;
        }
        return UnitUtils.getDistance(closestTumor, unit.unit()) < 11.5f &&
                !Bot.OBS.hasCreep(Position.towards(closestTumor.getPosition().toPoint2d(), bansheePos, 11.5f)) &&
                isSafe(Position.towards(closestTumor.getPosition().toPoint2d(), bansheePos, 6));
    }

    //tries to go around the threat
    protected Point2d getMovementPos(float rangeCheck) {
        Point2d towardsTarget = Position.towards(unit.unit(), targetPos, rangeCheck + unit.unit().getRadius());
        DebugHelper.drawLine(unit.unit().getPosition().toPoint2d(),towardsTarget,Color.RED);
        float angleToTarget = Position.getAngle(unit.unit().getPosition().toPoint2d(), targetPos);
        float angleDiff = Position.getDirectionalAngleDifference(prevAngle, angleToTarget, !isDodgeClockwise);
        Point2d startPos = Position.getDestinationByAngle(unit.unit().getPosition().toPoint2d(),prevAngle,rangeCheck + unit.unit().getRadius());
        DebugHelper.drawLine(unit.unit().getPosition().toPoint2d(),Position.towards(unit.unit().getPosition().toPoint2d(), startPos, 4),Color.WHITE);
        if (angleDiff > 25) {
            towardsTarget = Position.getDestinationByAngle(
                    unit.unit().getPosition().toPoint2d(),
                    prevAngle + (isDodgeClockwise ? -25 : 25),
                    rangeCheck + unit.unit().getRadius()
            );
        }
        DebugHelper.drawLine(unit.unit().getPosition().toPoint2d(),Position.towards(unit.unit().getPosition().toPoint2d(), towardsTarget, 4),Color.YELLOW);
        int j=1;
        for (int i=0; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? i : -i;
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null) {
                continue;
            }
            if (isGoodNextPos(detourPos)) {
                Color color = Color.GREEN;
                if (!changedDirectionRecently() && Position.isNearMapBorder(unit.unit().getPosition().toPoint2d(), 8)) {
                    toggleDodgeClockwise();
                    color = Color.BLUE;
                }
                DebugHelper.drawBox(detourPos, color, 0.2f);
                DebugHelper.drawBox(detourPos, color, 0.1f);
                return detourPos;
            }
            DebugHelper.drawText(j++ +"", detourPos, Color.RED);
        }
        if (rangeCheck > 18) {
            return ArmyManager.retreatPos;
        }
        return getMovementPos(rangeCheck + 2.5f);
    }

    public void tumorReport() {
        Print.print("BansheeCreepClear tumors killed: " + tumorCount);
        Chat.chat("Creep Clearing Squad Removed - tumors killed: " + tumorCount);
    }
}
