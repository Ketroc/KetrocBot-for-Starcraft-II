package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.InfluenceMaps;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;
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
    public int[][] threatMap;
    public boolean isDodgeClockwise;
    private long prevDirectionChangeFrame;
    public boolean removeMe;

    public BasicUnitMicro(UnitInPool unit, Point2d targetPos, boolean prioritizeLiving) {
        this.unit = unit;
        this.targetPos = targetPos;
        this.prioritizeLiving = prioritizeLiving;
        this.isGround = !unit.unit().getFlying().orElse(false);
        setWeaponInfo();
        this.movementSpeed = Bot.OBS.getUnitTypeData(false).get(unit.unit().getType()).getMovementSpeed().orElse(0f);
    }

    public void onStep() {
        isGround = !unit.unit().getFlying().orElse(false);
        if (unit == null || !unit.isAlive()) {
            onDeath();
            return;
        }

        threatMap = (isGround) ? InfluenceMaps.pointThreatToGround : InfluenceMaps.pointThreatToAir;

        //attack if available
        if (isOffCooldown()) {
            UnitInPool attackTarget = selectTarget();
            //attack if there's a target
            if (attackTarget != null) {
                if (!isTargettingUnit(attackTarget.unit())) {
                    Bot.ACTION.unitCommand(unit.unit(), Abilities.ATTACK, attackTarget.unit(), false);
                }
                return;
            }
        }

        //detour if needed
        if (!isSafe()) {
            Point2d detourPos = findDetourPos();
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, detourPos, false);
        }
        //finishing step on arrival
        else if (UnitUtils.getDistance(unit.unit(), targetPos) < 0.3) {
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

    private boolean isOffCooldown() {
        return unit.unit().getWeaponCooldown().orElse(1f) == 0;
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

    private boolean isSafe() {
        return isSafe(unit.unit().getPosition().toPoint2d());
    }

    private boolean isSafe(Point2d pos) {
        return InfluenceMaps.getValue(threatMap, pos) == 0;
    }

    private Point2d findDetourPos() {
        return findDetourPos(3.5f);
    }

    private Point2d findDetourPos(float rangeCheck) {
        Point2d towardsTarget = Position.towards(unit.unit().getPosition().toPoint2d(), targetPos, rangeCheck);
        Point2d safestPos = null;
        int safestThreatValue = Integer.MAX_VALUE;
        for (int i=0; i<360; i+=20) {
            int angle = (isDodgeClockwise) ? i : (i * -1);
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            int threatValue = InfluenceMaps.getValue(threatMap, detourPos);
            if (rangeCheck > 7 && threatValue < safestThreatValue) { //save least dangerous position in case no safe position is found
                safestThreatValue = threatValue;
                safestPos = detourPos;
            }
            if (isSafe(detourPos)) {
                if (i > 200 && !changedDirectionRecently()) { //Position.atEdgeOfMap(detourPos) ||
                    toggleDodgeClockwise();
                }
                //add 20degrees more angle as buffer, to account for chasing units
                i += 20;
                angle = (isDodgeClockwise) ? i : (i * -1);
                detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle);
                return detourPos;
            }
        }
        if (safestPos == null) {
            return findDetourPos(rangeCheck+2);
        }
        else {
            return safestPos;
        }
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
