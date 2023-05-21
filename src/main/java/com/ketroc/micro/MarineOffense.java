package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

import java.util.function.Predicate;

public class MarineOffense extends Marine {
    private Unit closestEnemyThreat;
    private boolean doStutterForward;
    private boolean inStaticDefenseRange;

    public MarineOffense(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public MarineOffense(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        inStaticDefenseRange = false;
        Predicate<UnitInPool> marineAllInFilter = enemy -> Strategy.gamePlan == GamePlan.MARINE_RUSH &&
                ArmyManager.doOffense &&
                !UnitUtils.DONT_CHASE_TYPES.contains(enemy.unit().getType());
        closestEnemyThreat = getClosestEnemyThreatToGround(marineAllInFilter);
        if (isStaticDefenseThreat(closestEnemyThreat)) {
            inStaticDefenseRange = UnitUtils.getDistance(unit.unit(), closestEnemyThreat) < UnitUtils.getAttackRange(closestEnemyThreat, Weapon.TargetType.GROUND) + unit.unit().getRadius() + 1;
            closestEnemyThreat = getClosestEnemyThreatToGround(enemy -> !isStaticDefenseThreat(enemy.unit()));
        }
        doStutterForward = doStutterForward(unit.unit(), closestEnemyThreat);
        setTargetPos();
        super.onStep();
    }

    @Override
    public boolean isSafe(Point2d pos) {
        return !inStaticDefenseRange && doStutterForward && !isInSplashDamage(pos);
    }

    @Override
    protected void setTargetPos() {
        // attack any threats within 15 range (army, workers, pylons -- excluding reapers/hellions/oracles/adepts)
        if (closestEnemyThreat != null) {
            targetPos = closestEnemyThreat.getPosition().toPoint2d();
        }
        // search and destroy when all known enemy units/bases are gone
        else if (ArmyManager.attackEitherPos == null) {
            setFinishHimTarget();
        }
        // kill any known enemy when enemy bases are gone
        else if (PosConstants.nextEnemyBase == null) {
            targetPos = ArmyManager.attackEitherPos;
        }
        // prefer heading towards next base, but double back for enemy army if 3x closer
        else {
            Point2d nextEnemyBasePos = PosConstants.nextEnemyBase.getResourceMidPoint();
            float distToNextBase = UnitUtils.getDistance(unit.unit(), nextEnemyBasePos);
            float distToAttackPos = UnitUtils.getDistance(unit.unit(), ArmyManager.attackEitherPos);
            targetPos = (distToNextBase < distToAttackPos * 3) ? nextEnemyBasePos : ArmyManager.attackEitherPos;
        }
    }
}
