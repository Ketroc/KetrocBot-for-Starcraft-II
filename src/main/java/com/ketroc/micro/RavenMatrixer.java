package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.UnitUtils;

//TODO: remove ravens when nuke is cancelled (doesn't belong here as this role is more generic than just nukes
public class RavenMatrixer extends Raven {
    protected boolean isMatrixCast;
    protected UnitInPool matrixTarget;

    public RavenMatrixer(Unit unit, UnitInPool matrixTarget) {
        super(unit, matrixTarget.unit().getPosition().toPoint2d(), MicroPriority.SURVIVAL);
        this.matrixTarget = matrixTarget;
    }

    public RavenMatrixer(UnitInPool unit, UnitInPool matrixTarget) {
        super(unit, matrixTarget.unit().getPosition().toPoint2d(), MicroPriority.SURVIVAL);
        this.matrixTarget = matrixTarget;
    }

    @Override
    protected void setTargetPos() {
        targetPos = matrixTarget.unit().getPosition().toPoint2d();
    }

    @Override
    public void onStep() {
        setTargetPos();

        //remove if raven dies, target dies, or raven gets matrixed/fungaled
        if (!isAlive() || !matrixTarget.isAlive() || !UnitUtils.canCast(unit.unit())) {
            removeMe = true;
            return;
        }

        //remove after matrix is cast
        if (isMatrixCast && !isMatrixCastInProgress()) {
            removeMe = true;
            return;
        }

        //prior to give matrix command
        if (!isMatrixCast) {
            //move in range in a straight fearless line
            if (UnitUtils.getDistance(unit.unit(), matrixTarget.unit()) >= getRange()) {
                if (!isMovingToTargetPos()) {
                    ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
                }
                return;
            }

            //cast matrix if energy available
            if (canMatrix() && !isMatrixCastInProgress()) { //within 9 range implied
                castMatrix(matrixTarget.unit());
                isMatrixCast = true;
                return;
            }

            //in range without enough energy = standard survival micro
            if (UnitUtils.getDistance(unit.unit(), matrixTarget.unit()) < getRange()) {
                super.onStep();
            }
        }
    }

    @Override
    public void onArrival() { }

    //how close to be, where matrix can still be cast at immediately at 75+ energy
    private float getRange() {
        float energyTime = timeUntilMatrixEnergy(unit.unit());
        return castDistance(unit.unit(), matrixTarget.unit()) +
                energyTime * UnitUtils.getUnitSpeed(unit.unit().getType());
    }

    private boolean isMatrixCastInProgress() {
        return ActionIssued.getCurOrder(unit).stream().anyMatch(action ->
                action.ability == Abilities.EFFECT_INTERFERENCE_MATRIX &&
                        matrixTarget.getTag().equals(action.targetTag));
    }

    private static float castDistance(Unit raven, Unit target) {
        return 9 + raven.getRadius() + target.getRadius();
    }

    //in seconds
    public static float timeUntilMatrixEnergy(Unit raven) {
        float energyNeeded = Math.max(0, 75 - raven.getEnergy().orElse(0f));
        return energyNeeded / 0.7875f;
    }

    //based on energy and distance, how early can matrix be cast on the target (in seconds)
    public static float minTimeToMatrix(Unit raven, Unit target) {
        float travelTime = (UnitUtils.getDistance(raven, target) - castDistance(raven, target)) /
                UnitUtils.getUnitSpeed(raven.getType());
        float energyTime = timeUntilMatrixEnergy(raven);
        return Math.max(travelTime, energyTime);
    }
}
