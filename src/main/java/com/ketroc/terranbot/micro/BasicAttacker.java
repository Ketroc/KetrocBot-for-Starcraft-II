package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.InfluenceMaps;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class BasicAttacker {
    public UnitInPool attacker;
    public Point2d targetPos;
    public boolean isGround;
    public boolean canAttackAir;
    public boolean canAttackGround;
    public float groundAttackRange;
    public float airAttackRange;
    public float movementSpeed;
    public float weaponCooldown = Float.MAX_VALUE; //default for units without weapons

    public BasicAttacker(UnitInPool attacker, Point2d targetPos) {
        this.attacker = attacker;
        this.targetPos = targetPos;
        this.isGround = !attacker.unit().getFlying().orElse(false);
        setWeaponInfo();
        this.movementSpeed = Bot.OBS.getUnitTypeData(false).get(attacker.unit().getType()).getMovementSpeed().orElse(0f);
    }

    public void onStep() {
        Unit target = findTarget();
        float targetDistance = UnitUtils.getDistance(attacker.unit(), target);
        float attackRange = (target.getFlying().orElse(false)) ? airAttackRange : groundAttackRange;
        if (doMoveInToEngage(targetDistance - attackRange)) {
            if (!isTargettingUnit(target)) {
                Bot.ACTION.unitCommand(attacker.unit(), Abilities.ATTACK, target, false);
            }
        }
        else {
            //TODO: stutterstep forwards/backwards
            //float targetRange = UnitUtils.getAttackRange()
        }
    }

    private boolean isTargettingUnit(Unit target) {
        return !attacker.unit().getOrders().isEmpty() &&
                target.getTag().equals(attacker.unit().getOrders().get(0).getTargetedUnitTag().orElse(null));
    }

    private Unit findTarget() {
        //find all targets in range
        List<UnitInPool> validTargets = getValidTargetsInRange();

        //pick best target
        return validTargets.stream()
                .max(Comparator.comparing(enemy -> getTargetValue()))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    private float getTargetValue() {

        return 0;
    }

    private List<UnitInPool> getValidTargetsInRange() {
        Predicate<UnitInPool> enemyTargetPredicate = enemy -> {
            boolean isEnemyGround = !enemy.unit().getFlying().orElse(false);
            float range = ((isEnemyGround) ? groundAttackRange : airAttackRange) + 1.5f;
            return ((isEnemyGround && canAttackGround) || (!isEnemyGround && canAttackAir)) &&
                    UnitUtils.getDistance(attacker.unit(), targetPos) < range;
        };
        return Bot.OBS.getUnits(Alliance.ENEMY, enemyTargetPredicate);
    }

    private boolean isOffCooldown() {
        return UnitUtils.isWeaponAvailable(attacker.unit());
    }

    private boolean doMoveInToEngage(float distanceBuffer) {
        return attacker.unit().getWeaponCooldown().orElse(1f) <= getCooldownToAttack(distanceBuffer);
    }

    //returns weapon cooldown value where that the unit has to move a certain distance before weapon becomes ready
    private float getCooldownToAttack(float distanceBuffer) {
        if (distanceBuffer <= 0) {
            return 0;
        }
        float timeRequired = distanceBuffer/movementSpeed;
        return timeRequired / weaponCooldown;
    }

    private void setWeaponInfo() {
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(attacker.unit().getType()).getWeapons();
        for (Weapon weapon : weapons) {
            switch (weapon.getTargetType()) {
                case AIR:
                    canAttackAir = true;
                    airAttackRange = weapon.getRange();
                    weaponCooldown = weapon.getSpeed();
                    break;
                case GROUND:
                    canAttackGround = true;
                    groundAttackRange = weapon.getRange();
                    weaponCooldown = weapon.getSpeed();
                    break;
                case ANY:
                    canAttackAir = true;
                    canAttackGround = true;
                    airAttackRange = weapon.getRange();
                    groundAttackRange = weapon.getRange();
                    weaponCooldown = weapon.getSpeed();
                    break;
            }
        }
    }

    private boolean isSafe() {
        Point2d curPos = attacker.unit().getPosition().toPoint2d();
        int[][] threatMap = (isGround) ? InfluenceMaps.pointThreatToGround : InfluenceMaps.pointThreatToAir;
        int threat = InfluenceMaps.getValue(threatMap, curPos);
        return threat == 0;
    }
}
