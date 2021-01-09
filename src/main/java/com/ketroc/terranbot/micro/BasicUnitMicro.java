package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.utils.*;
import com.ketroc.terranbot.bots.Bot;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class BasicUnitMicro {
    public UnitInPool unit;
    public Point2d targetPos;
    public boolean prioritizeLiving;
    public boolean isGround;
    public boolean canAttackAir;
    public boolean canAttackGround;
    public float groundAttackRange;
    public float airAttackRange;
    public float movementSpeed;
    public boolean isDodgeClockwise;
    private long prevDirectionChangeFrame;
    public boolean removeMe;

    public BasicUnitMicro(Unit unit, Point2d targetPos, boolean prioritizeLiving) {
        this(Bot.OBS.getUnit(unit.getTag()), targetPos, prioritizeLiving);
    }

    public BasicUnitMicro(UnitInPool unit, Point2d targetPos, boolean prioritizeLiving) {
        this.unit = unit;
        this.targetPos = targetPos;
        this.prioritizeLiving = prioritizeLiving;
        this.isGround = !unit.unit().getFlying().orElse(false);
        setWeaponInfo();
        this.movementSpeed = UnitUtils.getUnitSpeed(unit.unit().getType());
    }

    private boolean[][] getThreatMap() {
        if (this instanceof StructureFloater) {
            return InfluenceMaps.pointThreatToAirPlusBuffer;
        }
        if (!prioritizeLiving) {
            switch ((Units) unit.unit().getType()) {
                case TERRAN_BANSHEE:
                    return InfluenceMaps.pointInBansheeRange;
                case TERRAN_VIKING_FIGHTER:
                    return InfluenceMaps.pointInVikingRange;
                case TERRAN_MARINE:
                    return InfluenceMaps.pointInMarineRange;
            }
        }
        if (isGround) {
            return InfluenceMaps.pointThreatToGround;
        }
        return InfluenceMaps.pointThreatToAir;
    }

    public void onStep() {
        if (unit == null || !unit.isAlive()) {
            onDeath();
            return;
        }
        DebugHelper.draw3dBox(unit.unit().getPosition().toPoint2d(), Color.GREEN, 0.5f);

        //attack if available
        if (UnitUtils.isWeaponAvailable(unit.unit())) {
            UnitInPool attackTarget = selectTarget();
            //attack if there's a target
            if (attackTarget != null) {
                if (!isTargettingUnit(attackTarget.unit())) {
                    Bot.ACTION.unitCommand(unit.unit(), Abilities.ATTACK, attackTarget.unit(), false);
                }
                return;
            }
        }

        //done if unit is immobile
        if (!UnitUtils.canMove(unit.unit())) {
            return;
        }

        //detour if needed
        if (!isSafe()) {
            Point2d detourPos = findDetourPos();
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, detourPos, false);
        }
        //finishing step on arrival
        else if (UnitUtils.getDistance(unit.unit(), targetPos) < 2.5f) {
            onArrival();
        }
        //continue moving to target
        else if (!isMovingToTargetPos()) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }
    }

    public void onArrival() {
        removeMe = true;
    }

    public void onDeath() {
        removeMe = true;
        return;
    }

    private boolean isMovingToTargetPos() {
        return !unit.unit().getOrders().isEmpty() &&
                unit.unit().getOrders().get(0).getTargetedWorldSpacePosition().isPresent() &&
                unit.unit().getOrders().get(0).getTargetedWorldSpacePosition().get().toPoint2d().distance(targetPos) < 1;

    }

    private boolean isTargettingUnit(Unit target) {
        return !unit.unit().getOrders().isEmpty() &&
                target.getTag().equals(unit.unit().getOrders().get(0).getTargetedUnitTag().orElse(null));
    }

    //selects target based on cost:health ratio
    public UnitInPool selectTarget() {
        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                ((isAir(enemy) && canAttackAir) || (!isAir(enemy) && canAttackGround)) &&
                UnitUtils.getDistance(enemy.unit(), unit.unit()) <= (isAir(enemy) ? airAttackRange : groundAttackRange) &&
                !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()));
        Target bestTarget = new Target(null, Float.MAX_VALUE, Float.MAX_VALUE); //best target will be lowest hp unit without barrier
        for (UnitInPool enemy : enemiesInRange) {
            float enemyHP = enemy.unit().getHealth().orElse(0f) + enemy.unit().getShield().orElse(0f);
            UnitTypeData enemyData = Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType());
            float enemyCost;
            if (enemy.unit().getType() == UnitUtils.enemyWorkerType) { //inflate value of workers as they impact income
                enemyCost = 75;
            }
            else {
                enemyCost = enemyData.getMineralCost().orElse(1) + (enemyData.getVespeneCost().orElse(1) * 1.2f); //value gas more than minerals
            }
            float enemyValue = enemyHP/enemyCost;
            if (enemyValue < bestTarget.value && !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                bestTarget.update(enemy, enemyValue, enemyHP);
            }
        }
        return bestTarget.unit;
    }

    private boolean isAir(UnitInPool enemy) {
        return enemy.unit().getFlying().orElse(true);
    }

    private List<UnitInPool> getValidTargetsInRange() {
        Predicate<UnitInPool> enemyTargetPredicate = enemy -> {
            boolean isEnemyGround = !enemy.unit().getFlying().orElse(false);
            float range = ((isEnemyGround) ? groundAttackRange : airAttackRange) + 1.5f;
            return ((isEnemyGround && canAttackGround) || (!isEnemyGround && canAttackAir)) &&
                    UnitUtils.getDistance(unit.unit(), targetPos) < range;
        };
        return Bot.OBS.getUnits(Alliance.ENEMY, enemyTargetPredicate);
    }

    private void setWeaponInfo() {
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.unit().getType()).getWeapons();
        for (Weapon weapon : weapons) {
            switch (weapon.getTargetType()) {
                case AIR:
                    canAttackAir = true;
                    airAttackRange = weapon.getRange() + unit.unit().getRadius();
                    break;
                case GROUND:
                    canAttackGround = true;
                    groundAttackRange = weapon.getRange() + unit.unit().getRadius();
                    break;
                case ANY:
                    canAttackAir = true;
                    canAttackGround = true;
                    airAttackRange = weapon.getRange() + unit.unit().getRadius();
                    groundAttackRange = weapon.getRange() + unit.unit().getRadius();
                    break;
            }
        }
    }

    protected boolean isSafe() {
        return isSafe(unit.unit().getPosition().toPoint2d());
    }

    private boolean isSafe(Point2d pos) {
        return !InfluenceMaps.getValue(getThreatMap(), pos);
    }

    private Point2d findDetourPos() {
        return findDetourPos(2f);
    }

    private Point2d findDetourPos(float rangeCheck) {
        Point2d towardsTarget = Position.towards(unit.unit().getPosition().toPoint2d(), targetPos, rangeCheck);
        for (int i=0; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? i : (i * -1);
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            if (isSafe(detourPos)) {
                if (i > 200 && !changedDirectionRecently()) { //Position.atEdgeOfMap(detourPos) ||
                    toggleDodgeClockwise();
                }
                //add 15degrees more angle as buffer, to account for chasing units
                i += 15;
                angle = (isDodgeClockwise) ? i : (i * -1);
                detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle);
                return detourPos;
            }
        }
        if (rangeCheck > 20) {
            return ArmyManager.retreatPos;
        }
        return findDetourPos(rangeCheck+2);
    }

    private boolean isPathable(Point2d detourPos) {
        return !isGround || Bot.OBS.isPathable(detourPos);
    }

    public void toggleDodgeClockwise() {
        isDodgeClockwise = !isDodgeClockwise;
        prevDirectionChangeFrame = Time.nowFrames();
    }

    //3sec delay between direction changes (so it doesn't get stuck wiggling against the edge)
    public boolean changedDirectionRecently() {
        return prevDirectionChangeFrame + 75 > Time.nowFrames();
    }
}
