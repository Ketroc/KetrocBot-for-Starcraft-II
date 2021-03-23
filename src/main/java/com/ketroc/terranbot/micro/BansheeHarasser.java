package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.*;

import java.util.Comparator;
import java.util.List;

public class BansheeHarasser {
    public static final float RETREAT_HEALTH = 50;
    public static final float BANSHEE_MOVEMENT_SIZE = (Bot.isRealTime) ? 4f : 2.5f;
    public UnitInPool banshee;
    private List<Point2d> baseList;
    private boolean isDodgeClockwise;
    private int baseIndex = 1;
    public boolean retreatForRepairs;
    private long prevDirectionChangeFrame;

    public BansheeHarasser(UnitInPool banshee, boolean isBaseTravelClockwise) {
        this.banshee = banshee;
        baseList = (isBaseTravelClockwise) ? LocationConstants.clockBasePositions : LocationConstants.counterClockBasePositions;
        baseList = baseList.subList(1, baseList.size());
        this.isDodgeClockwise = isBaseTravelClockwise;
    }

    public boolean isDodgeClockwise() {
        return isDodgeClockwise;
    }

    public void toggleDodgeClockwise() {
        isDodgeClockwise = !isDodgeClockwise;
        prevDirectionChangeFrame = Time.nowFrames();
    }

    //3sec delay between direction changes (so it doesn't get stuck wiggling against the edge)
    public boolean changedDirectionRecently() {
        return prevDirectionChangeFrame + 75 > Time.nowFrames();
    }

    private void nextBase() {
        baseIndex = (baseIndex + 1) % baseList.size();
    }

    private Point2d getThisBase() {
        return baseList.get(baseIndex);
    }



    public void bansheeMicro() {
        //send home when health is low
        if (!retreatForRepairs && isLowHealth()) {
            retreatForRepairs = true;
        }

        //if should cloak
        if (shouldCloak()) {
            ActionHelper.unitCommand(banshee.unit(), Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            return;
        }

        //if can attack, find target
        if (UnitUtils.isWeaponAvailable(banshee.unit())) {
            Target target = selectHarassTarget();
            //attack when safe, or when there's a good value target and not headed home
            if (target.unit != null) {
                if (isSafe() || (!retreatForRepairs && target.hp <= 24)){
                    ActionHelper.unitCommand(banshee.unit(), Abilities.ATTACK, target.unit.unit(), false);
                    return;
                }
            }
            //if at basePos without workers in vision, then move on to next base
            else if (UnitUtils.getDistance(banshee.unit(), getThisBase()) < 2 &&
                    UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType).stream()
                            .noneMatch(enemyWorker -> UnitUtils.getDistance(banshee.unit(), enemyWorker) < 10)) {
                nextBase();
            }
        }

        //find a path
        Point2d headedTo = getTargetLocation();
        giveMovementCommand(headedTo);
    }

    private Point2d getTargetLocation() {
        //go home if low hp
        if (retreatForRepairs) {
            return LocationConstants.REPAIR_BAY;
        }

        //flee from closest cyclone, if locked on
        if (banshee.unit().getBuffs().contains(Buffs.LOCK_ON)) {
            Unit nearestCyclone = UnitUtils.getClosestEnemyOfType(Units.TERRAN_CYCLONE, banshee.unit().getPosition().toPoint2d());
            if (nearestCyclone != null) {
                return Position.towards(banshee.unit().getPosition().toPoint2d(), nearestCyclone.getPosition().toPoint2d(), -4);
            }
        }

        //go towards nearest enemy worker
        Unit closestWorker = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType).stream()
                .min(Comparator.comparing(worker -> UnitUtils.getDistance(banshee.unit(), worker)))
                .orElse(null);
        if (closestWorker != null && UnitUtils.getDistance(banshee.unit(), closestWorker) < 10) {
            return closestWorker.getPosition().toPoint2d();
        }

        //go towards next base
        return getThisBase();
    }

    private void giveMovementCommand(Point2d targetPos) {
        Point2d safePos = getSafePos(targetPos);
        ActionHelper.unitCommand(banshee.unit(), Abilities.MOVE, safePos, false);
        DebugHelper.drawBox(safePos, Color.GREEN, 0.22f);
        DebugHelper.drawBox(safePos, Color.GREEN, 0.20f);
        DebugHelper.drawBox(safePos, Color.GREEN, 0.18f);
    }

    private Point2d getSafePos(Point2d targetPos) {
        return getSafePos(targetPos, BANSHEE_MOVEMENT_SIZE);
    }

    private Point2d getSafePos(Point2d targetPos, float rangeCheck) {
        Point2d towardsTarget = Position.towards(banshee.unit().getPosition().toPoint2d(), targetPos, rangeCheck);
        Point2d safestPos = null;
        int safestThreatValue = Integer.MAX_VALUE;
        boolean canCloak = canCloak();
        for (int i=0; i<360; i+=20) {
            int angle = (isDodgeClockwise) ? i : (i * -1);
            Point2d detourPos = Position.rotate(towardsTarget, banshee.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null) {
                continue;
            }
            int threatValue = (canCloak && !InfluenceMaps.getValue(InfluenceMaps.pointDetected, detourPos))
                    ? 0
                    : InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, detourPos);
            if (rangeCheck > 7 && threatValue < safestThreatValue) { //save least dangerous position in case no safe position is found
                safestThreatValue = threatValue;
                safestPos = detourPos;
            }
            if (isSafe(detourPos)) {
                if (i > 200 && changedDirectionRecently()) {
                    int q=0;
                }
                if (isLeavingWorkers(detourPos) || (i > 200 && !changedDirectionRecently())) { //Position.atEdgeOfMap(detourPos) ||
                    toggleDodgeClockwise();
                }
                //add 20degrees more angle as buffer, to account for chasing units
                i += 20;
                angle = (isDodgeClockwise) ? i : (i * -1);
                detourPos = Position.rotate(towardsTarget, banshee.unit().getPosition().toPoint2d(), angle);
                return detourPos;
            }
        }
        if (safestPos == null) {
            return getSafePos(targetPos, rangeCheck+2);
        }
        else {
            return safestPos;
        }
    }

    private boolean isLeavingWorkers(Point2d targetPos) {
        return isWorkerInRange(banshee.unit().getPosition().toPoint2d()) && !isWorkerInRange(targetPos);
    }

    private boolean isWorkerInRange(Point2d pos) {
        return !Bot.OBS.getUnits(Alliance.ENEMY, enemy -> enemy.unit().getType() == UnitUtils.enemyWorkerType &&
                UnitUtils.getDistance(enemy.unit(), pos) < 6).isEmpty();
    }


    public boolean canCloak() {
        float energyToCloak = (banshee.unit().getHealth().get() > 24) ? 50 : 27;
        return Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
                banshee.unit().getEnergy().orElse(0f) > energyToCloak;

    }

    private boolean isCloaked() {
        return banshee.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) == CloakState.CLOAKED_ALLIED;
    }

    //is safe if position is free from threat, or undetected with cloak available
    private boolean isSafe() {
        return isSafe(banshee.unit().getPosition().toPoint2d());
    }

    private boolean isLowHealth() {
        return banshee.unit().getHealth().orElse(1f) < RETREAT_HEALTH;
    }

    private boolean isSafe(Point2d p) {
        //avoid high damage areas even if cloaked
        float threatValue = InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, p);
        if (threatValue >= 50) {
            return false;
        }

        //don't risk being in threat range while cloaked, if health is low
        boolean safe = threatValue <= 2;
        if (retreatForRepairs) {
            return safe;
        }

        //safe if no threat or undetected with cloak
        boolean cloakAvailable = canCloak() || (isCloaked() && banshee.unit().getEnergy().orElse(0f) > 5);
        return safe || (cloakAvailable && !isDetected(p));
    }

    //is safe if position is free from threat, or undetected with cloak available
    private boolean shouldCloak() {
        Point2d bansheePos = banshee.unit().getPosition().toPoint2d();
        if (!isCloaked() && canCloak() && !isDetected(bansheePos)) {
            //health:threat threshold or cyclone locked on
            return  InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, bansheePos) > banshee.unit().getHealth().get()/30 ||
                    banshee.unit().getBuffs().contains(Buffs.LOCK_ON);
        }
        return false;
    }

    private boolean isDetected(Point2d p) {
        return InfluenceMaps.getValue(InfluenceMaps.pointDetected, p);
    }

    //selects target based on cost:health ratio
    public Target selectHarassTarget() {
        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY,
                enemy -> !enemy.unit().getFlying().orElse(true) &&
                        UnitUtils.getDistance(enemy.unit(), banshee.unit()) <= 5.9 &&
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()));
        Target bestTarget = new Target(null, Float.MIN_VALUE, Float.MAX_VALUE); //best target will be lowest hp unit without barrier
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
            float enemyValue = enemyCost/enemyHP;
            if (enemyValue > bestTarget.value && !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                bestTarget.update(enemy, enemyValue, enemyHP);
            }
        }
        return bestTarget;
    }

}
