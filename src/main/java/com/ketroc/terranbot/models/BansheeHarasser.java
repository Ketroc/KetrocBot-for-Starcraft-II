package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.List;

public class BansheeHarasser {
    public UnitInPool banshee;
    private List<Point2d> baseList;
    private boolean isDodgeClockwise;
    private int baseIndex = 1;

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
    }

    private void nextBase() {
        baseIndex = (baseIndex + 1) % baseList.size();
    }

    private Point2d getThisBase() {
        return baseList.get(baseIndex);
    }

    public void bansheeMicro() {
        //if can attack, find target
        if (banshee.unit().getWeaponCooldown().orElse(1f) == 0) {
            UnitInPool target = selectHarassTarget();
            if (target != null) {
                Bot.ACTION.unitCommand(banshee.unit(), Abilities.ATTACK, target.unit(), false);
                return;
            }
            //if at basePos with no targets, then move on to next base
            else if (UnitUtils.getDistance(banshee.unit(), getThisBase()) < 1) {
                nextBase();
            }
        }

        //if should cloak
        if (shouldCloak()) {
            Bot.ACTION.unitCommand(banshee.unit(), Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            return;
        }

        //find a path
        Point2d towardsBase = Position.towards(banshee.unit().getPosition().toPoint2d(), getThisBase(), 3);
        giveMovementCommand(towardsBase);
    }

    private void giveMovementCommand(Point2d targetPos) {
        Point2d safePos = getSafePos(targetPos);
        if (safePos != null) {
            Bot.ACTION.unitCommand(banshee.unit(), Abilities.MOVE, safePos, false);
        }
        else {
            Bot.ACTION.unitCommand(banshee.unit(), Abilities.MOVE, LocationConstants.REPAIR_BAY, false);
        }
    }

    private Point2d getSafePos(Point2d targetPos) {
        for (int i=0; i<360; i+=10) {
            int angle = (isDodgeClockwise) ? i : (i * -1);
            targetPos = Position.rotate(targetPos, banshee.unit().getPosition().toPoint2d(), angle);
            if (isSafe(targetPos)) {
                if (Position.atEdgeOfMap(targetPos) || i > 240 || isLeavingWorkers(targetPos)) {
                    toggleDodgeClockwise();
                }
                //add 20degrees more angle as buffer, to account for chasing units
                i += 20;
                angle = (isDodgeClockwise) ? i : (i * -1);
                return Position.rotate(targetPos, banshee.unit().getPosition().toPoint2d(), angle);
            }
        }
        return null;
    }

    private boolean isLeavingWorkers(Point2d targetPos) {
        return isWorkerInRange(banshee.unit().getPosition().toPoint2d()) && !isWorkerInRange(targetPos);
    }

    private boolean isWorkerInRange(Point2d pos) {
        return !Bot.OBS.getUnits(Alliance.ENEMY, enemy -> enemy.unit().getType() == UnitUtils.enemyWorkerType &&
                UnitUtils.getDistance(enemy.unit(), pos) < 6).isEmpty();
    }


    public boolean canCloak() {
        return banshee.unit().getEnergy().orElse(0f) > 50 && GameCache.upgradesCompleted.contains(Upgrades.BANSHEE_CLOAK);
    }

    private boolean isCloaked() {
        return banshee.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) == CloakState.CLOAKED_ALLIED;
    }

    //is safe if position is free from threat, or undetected with cloak available
    private boolean isSafe(Point2d p) {
        boolean safe = GameCache.pointThreatToAir[(int) p.getX()][(int) p.getY()] <= 2;
        boolean cloakAvailable = canCloak() || (isCloaked() && banshee.unit().getEnergy().orElse(0f) > 3);
        return safe || (cloakAvailable && !isDetected(p));
    }

    //is safe if position is free from threat, or undetected with cloak available
    private boolean shouldCloak() {
        if (!isCloaked() && canCloak()) {
            Point2d p = Position.nearestWholePoint(banshee.unit().getPosition().toPoint2d());
            return !isDetected(p) && GameCache.pointThreatToAir[(int) p.getX()][(int) p.getY()] > 0;
        }
        return false;
    }

    private boolean isDetected(Point2d p) {
        return GameCache.pointDetected[(int)p.getX()][(int)p.getY()];
    }

    //selects lowest hp target
    public UnitInPool selectHarassTarget() {
        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY,
                enemy -> !enemy.unit().getFlying().orElse(true) &&
                        UnitUtils.getDistance(enemy.unit(), banshee.unit()) <= 6 &&
                        !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()));
        UnitInPool bestTarget = null; //best target will be lowest hp unit without barrier
        float bestTargetHP = Float.MAX_VALUE;
        for (UnitInPool enemy : enemiesInRange) {
            float enemyHP = enemy.unit().getHealth().orElse(0f) + enemy.unit().getShield().orElse(0f);
            if (enemyHP < bestTargetHP && !enemy.unit().getBuffs().contains(Buffs.PROTECTIVE_BARRIER)) {
                bestTargetHP = enemyHP;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }
}
