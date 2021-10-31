package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.models.Ignored;
import com.ketroc.models.IgnoredUnit;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class BasicUnitMicro {
    public UnitInPool unit;
    public Point2d targetPos;
    public MicroPriority priority;
    public boolean isGround;
    public boolean canAttackAir;
    public boolean canAttackGround;
    public float groundAttackRange;
    public float airAttackRange;
    public float movementSpeed;
    public boolean isDodgeClockwise;
    private long prevDirectionChangeFrame;
    public boolean removeMe;
    public boolean doDetourAroundEnemy;

    public BasicUnitMicro(Unit unit, Point2d targetPos, MicroPriority priority) {
        this(Bot.OBS.getUnit(unit.getTag()), targetPos, priority);
    }

    public BasicUnitMicro(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        this.unit = unit;
        this.targetPos = targetPos;
        this.priority = priority;
        this.isGround = !unit.unit().getFlying().orElse(false);
        setWeaponInfo();
        this.movementSpeed = UnitUtils.getUnitSpeed(unit.unit().getType());
    }

    protected boolean[][] getThreatMap() {
        if (this instanceof StructureFloater) {
            return InfluenceMaps.pointThreatToAirPlusBuffer;
        }
        if (priority != MicroPriority.SURVIVAL && priority != MicroPriority.CHASER) {
            switch ((Units) unit.unit().getType()) {
                case TERRAN_BANSHEE:
                    return InfluenceMaps.pointInBansheeRange;
                case TERRAN_VIKING_FIGHTER:
                    return InfluenceMaps.enemyInVikingRange;
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
        if (!isAlive()) {
            onDeath();
            return;
        }

        if (isMorphing()) {
            return;
        }

        //DebugHelper.draw3dBox(unit.unit().getPosition().toPoint2d(), Color.GREEN, 0.5f);

        //attack if available
        if (attackIfAvailable()) {
            return;
        }

        //done if unit is immobile
        if (!UnitUtils.canMove(unit.unit())) {
            return;
        }

        //detour if needed
        if (!isSafe() && !neverDetour()) {
            detour();
        }
        //finishing step on arrival
        else if (UnitUtils.getDistance(unit.unit(), targetPos) < 2.5f) {
            onArrival();
        }
        //continue moving to target
        else if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }
    }

    private boolean neverDetour() {
        return priority == MicroPriority.GET_TO_DESTINATION ||
                (priority == MicroPriority.DPS && canAttackGround && groundAttackRange - unit.unit().getRadius() < 2);
    }

    protected void setTargetPos() {
        if (canAttackAir && canAttackGround) {
            targetPos = ArmyManager.attackEitherPos;
        }
        else if (canAttackAir) {
            targetPos = ArmyManager.attackAirPos;
        }
        else {
            targetPos = ArmyManager.attackGroundPos;
        }
    }

    protected void detour() {
        Point2d detourPos = findDetourPos();
        ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, detourPos, false);
    }

    protected boolean attackIfAvailable() {
        if (UnitUtils.isWeaponAvailable(unit.unit())) {
            return attack();
        }
        return false;
    }

    protected boolean attack() {
        UnitInPool attackTarget = selectTarget();
        //attack if there's a target
        if (attackTarget != null) {
            if (!isTargettingUnit(attackTarget.unit())) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, attackTarget.unit(), false);
            }
            return true;
        }
        return false;
    }

    public void onArrival() {
        removeMe = true;
    }

    public void onDeath() {
        removeMe = true;
    }

    protected boolean isMovingToTargetPos() {
        Optional<ActionIssued> order = ActionIssued.getCurOrder(unit);
        return order.isPresent() &&
                order.get().targetPos != null &&
                order.get().targetPos.distance(targetPos) < 0.5 ;

    }

    protected boolean isAttackingTarget(Tag targetTag) {
        Optional<ActionIssued> order = ActionIssued.getCurOrder(unit);
        return order.isPresent() &&
                order.get().ability == Abilities.ATTACK &&
                targetTag.equals(order.get().targetTag);

    }

    protected boolean isTargettingUnit(Unit target) {
        Optional<ActionIssued> order = ActionIssued.getCurOrder(unit);
        return order.isPresent() &&
                target.getTag().equals(order.get().targetTag);
    }

    //selects target based on cost:health ratio
    public UnitInPool selectTarget() {
        UnitInPool selectedTarget = selectTarget(false);
        if (selectedTarget == null) {
            selectedTarget = selectTarget(true);
        }
        return selectedTarget;
    }
    public UnitInPool selectTarget(boolean includeIgnoredTargets) {
        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                ((isAir(enemy) && canAttackAir) || (!isAir(enemy) && canAttackGround)) &&
                UnitUtils.getDistance(enemy.unit(), unit.unit()) <=
                        (isAir(enemy) ? airAttackRange : groundAttackRange) + enemy.unit().getRadius() &&
                !UnitUtils.UNTARGETTABLES.contains(enemy.unit().getType()) &&
                (includeIgnoredTargets || !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType())) &&
                enemy.unit().getDisplayType() == DisplayType.VISIBLE);
        Target bestTarget = new Target(null, Float.MIN_VALUE, Float.MAX_VALUE); //best target will be lowest hp unit without barrier
        for (UnitInPool enemy : enemiesInRange) {
            float enemyHP = enemy.unit().getHealth().orElse(0f) + enemy.unit().getShield().orElse(0f);
            UnitTypeData enemyData = Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType());
            float enemyCost;
            if (UnitUtils.SIEGE_TANK_TYPE.contains(enemy.unit().getType()) &&
                    UnitUtils.SIEGE_TANK_TYPE.contains(unit.unit().getType())) { //focus on winning siege tank war first TODO: move to my siege tanks only
                return enemy;
            }
            else if (enemy.unit().getType() == UnitUtils.enemyWorkerType) { //inflate value of workers as they impact income
                enemyCost = 75;
            }
            else {
                enemyCost = Math.max(1,
                        enemyData.getMineralCost().orElse(1) + (enemyData.getVespeneCost().orElse(1) * 1.2f)); //value gas more than minerals
            }
            float enemyValue = enemyCost/enemyHP;
            if (enemyValue > bestTarget.value && !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                bestTarget.update(enemy, enemyValue, enemyHP);
            }
        }
        return bestTarget.unit;
    }

    protected boolean isAir(UnitInPool enemy) {
        return enemy.unit().getFlying().orElse(true);
    }

    protected List<UnitInPool> getValidTargetsInRange() {
        Predicate<UnitInPool> enemyTargetPredicate = enemy -> {
            boolean isEnemyGround = !enemy.unit().getFlying().orElse(false);
            float range = ((isEnemyGround) ? groundAttackRange : airAttackRange);
            return ((isEnemyGround && canAttackGround) || (!isEnemyGround && canAttackAir)) &&
                    UnitUtils.getDistance(unit.unit(), targetPos) < range;
        };
        return Bot.OBS.getUnits(Alliance.ENEMY, enemyTargetPredicate);
    }

    protected void setWeaponInfo() {
        Set<Weapon> weapons = Bot.OBS.getUnitTypeData(false).get(unit.unit().getType()).getWeapons();
        for (Weapon weapon : weapons) {
            switch (weapon.getTargetType()) {
                case AIR:
                    canAttackAir = true;
                    airAttackRange = weapon.getRange() + unit.unit().getRadius() + 0.25f;
                    break;
                case GROUND:
                    canAttackGround = true;
                    groundAttackRange = weapon.getRange() + unit.unit().getRadius() + 0.25f;
                    break;
                case ANY:
                    canAttackAir = true;
                    canAttackGround = true;
                    airAttackRange = weapon.getRange() + unit.unit().getRadius() + 0.25f;
                    groundAttackRange = weapon.getRange() + unit.unit().getRadius() + 0.25f;
                    break;
            }
        }
    }

    protected boolean isSafe() {
        return isSafe(unit.unit().getPosition().toPoint2d());
    }

    protected boolean isSafe(Point2d pos) {
        return !InfluenceMaps.getValue(getThreatMap(), pos);
    }

    protected Point2d findDetourPos() {
        return doDetourAroundEnemy ? findDetourPos2(2f) : findDetourPos(2f);
    }

    //tries to go around the threat
    protected Point2d findDetourPos2(float rangeCheck) {
        Point2d towardsTarget = Position.towards(unit.unit(), targetPos, rangeCheck + unit.unit().getRadius());
        for (int i=0; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? i : (i * -1);
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            if (isSafe(detourPos)) {
                if (i > 200 && !changedDirectionRecently()) {
                    toggleDodgeClockwise();
                }
                //add 5degrees more angle as buffer, to account for chasing units
                i += 5;
                angle = (isDodgeClockwise) ? i : (i * -1);
                detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle);
                return Position.towards(detourPos, unit.unit(), unit.unit().getRadius());
            }
        }
        if (rangeCheck > 18) {
            return ArmyManager.retreatPos;
        }
        return findDetourPos(rangeCheck+1.5f);
    }

    //TODO: start of another pathing technique that attempts to maintain the same direction by starting with Facing Angle
    protected Point2d findDetourPos3(float rangeCheck) {
        float facingAngle = Position.getFacingAngle(unit.unit());
        float retreatAngle = Position.getAngle(unit.unit().getPosition().toPoint2d(), ArmyManager.retreatPos);
        float sign = (Position.getSignedAngleDifference(facingAngle, retreatAngle) >= 0) ? 1 : -1;
        return null;
    }

    //retreats as straight back as possible from the threat
    protected Point2d findDetourPos(float rangeCheck) {
        Point2d towardsTarget = Position.towards(unit.unit(), targetPos, rangeCheck);
        for (int i=180; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? i : (i * -1);
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            if (isSafe(detourPos)) {
                return detourPos;
            }
        }
        for (int i=180; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? (i * -1) : i;
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            if (isSafe(detourPos) && routeIsPathable(detourPos)) {
                if (!changedDirectionRecently()) {
                    toggleDodgeClockwise();
                }
                return detourPos;
            }
        }
        if (rangeCheck > 20) {
            return ArmyManager.retreatPos;
        }
        return findDetourPos(rangeCheck+2);
    }

    //TODO: replace with a pathfind with numSteps limited by distance ratio
    private boolean routeIsPathable(Point2d detourPos) {
        if (unit.unit().getFlying().orElse(true)) {
            return true;
        }
        float terrainDifference = Math.abs(Bot.OBS.terrainHeight(detourPos) - Bot.OBS.terrainHeight(unit.unit().getPosition().toPoint2d()));
        if (terrainDifference > 1) { //only check when change terrain level
            float distanceToDetourPos = UnitUtils.getDistance(unit.unit(), detourPos);
            return Bot.OBS.isPathable(Position.towards(unit.unit(), detourPos, distanceToDetourPos * 0.2f)) &&
                    Bot.OBS.isPathable(Position.towards(unit.unit(), detourPos, distanceToDetourPos * 0.4f)) &&
                    Bot.OBS.isPathable(Position.towards(unit.unit(), detourPos, distanceToDetourPos * 0.6f)) &&
                    Bot.OBS.isPathable(Position.towards(unit.unit(), detourPos, distanceToDetourPos * 0.8f));
        }
        return true;
    }

    protected boolean isPathable(Point2d detourPos) {
        return !isGround || Bot.OBS.isPathable(detourPos);
    }

    public void toggleDodgeClockwise() {
        isDodgeClockwise = !isDodgeClockwise;
        prevDirectionChangeFrame = Time.nowFrames();
    }

    //3sec delay between direction changes (so it doesn't get stuck wiggling against the edge)
    public boolean changedDirectionRecently() {
        return prevDirectionChangeFrame + 175 > Time.nowFrames();
    }

    public void replaceUnit(UnitInPool newUnit) {
        Ignored.remove(unit.getTag());
        Base.releaseScv(newUnit.unit());
        unit = newUnit;
        Ignored.add(new IgnoredUnit(newUnit.getTag()));
    }

    protected boolean isEnemyFacingMe(Unit enemy) {
        float facing = (float)Math.toDegrees(enemy.getFacing());
        float attackAngle = Position.getAngle(enemy.getPosition().toPoint2d(), unit.unit().getPosition().toPoint2d());
        float angleDiff = Position.getAngleDifference(facing, attackAngle);
        return angleDiff <= 90;
    }

    public boolean isAlive() {
        return unit != null && unit.isAlive();
    }

    protected boolean attackTarget(Unit target) {
        float attackRange = target.getFlying().orElse(false) ? airAttackRange : groundAttackRange;
        if (target.getDisplayType() == DisplayType.VISIBLE && UnitUtils.getDistance(unit.unit(), target) < attackRange) {
            if (!isAttackingTarget(unit.getTag())) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, target, false);
            }
            return true;
        }
        return false;
    }

    protected void updateTargetPos() {
        if (ArmyManager.attackGroundPos != null) {
            if (canAttackAir && canAttackGround) {
                targetPos = ArmyManager.attackEitherPos;
            }
            else if (canAttackAir) {
                targetPos = ArmyManager.attackAirPos;
            }
            else {
                targetPos = ArmyManager.attackGroundPos;
            }
        }
        else { //find last structures with random reachable positions
            if (UnitUtils.getDistance(unit.unit(), targetPos) < 3) { //switch positions when it arrives
                do {
                    targetPos = Bot.OBS.getGameInfo().findRandomLocation();
                } while (isGround && !Bot.OBS.isPathable(targetPos));
            }
        }
    }

    protected boolean isMorphing() {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(action -> action.ability.toString().contains("MORPH"));
    }

    //consider retreating if !doOffense and not near a friendly base
    protected boolean isRetreating() {
        return !ArmyManager.doOffense && GameCache.baseList.stream()
                        .noneMatch(base -> base.isMyBase() && UnitUtils.getDistance(unit.unit(), base.getCcPos()) < 15);
    }

    //hunt for last structures during "Finish Him"
    protected void setFinishHimTarget() {
        ActionIssued.getCurOrder(unit)
                .filter(actionIssued -> actionIssued.targetPos != null)
                .ifPresentOrElse(actionIssued -> targetPos = actionIssued.targetPos,
                        () -> targetPos = Bot.OBS.getGameInfo().findRandomLocation());
    }


    protected Optional<Point2d> getClosestRepairBay(Point2d unitPos) {
        return GameCache.baseList.stream()
                .filter(base -> base.isMyBase() &&
                        base.isComplete() &&
                        !base.isMyMainBase() &&
                        base.getNumMineralScvs() >= 4 &&
                        !base.isUnderAttack())
                .map(Base::inFrontPos)
                .min(Comparator.comparing(pos -> pos.distance(unitPos)));
    }

    protected boolean underRepair(Point2d repairBayPos) {
        return UnitUtils.getHealthPercentage(unit.unit()) < 100 &&
                UnitUtils.getDistance(unit.unit(), repairBayPos) <= 3;
    }

    protected boolean requiresRepairs(int healthToRepairAt) {
        return unit.unit().getHealth().orElse(120f) <= healthToRepairAt;
    }
}
