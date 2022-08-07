package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
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

    public boolean[][] getThreatMap() {
        if (this instanceof StructureFloater) {
            return InfluenceMaps.pointThreatToAirPlusBuffer;
        }
        if (priority != MicroPriority.SURVIVAL && priority != MicroPriority.CHASER) {
            switch ((Units) unit.unit().getType()) {
                case TERRAN_BANSHEE:
                    return InfluenceMaps.point6RangevsGround;
                case TERRAN_VIKING_FIGHTER:
                    return InfluenceMaps.enemyInVikingRange;
                case TERRAN_MARINE:
                    return InfluenceMaps.pointIn5RangeVsBoth;
                case TERRAN_HELLION:
                    return InfluenceMaps.pointIn5RangeVsGround;
                case TERRAN_GHOST:
                    return InfluenceMaps.pointIn7RangeVsBoth;
                case TERRAN_MARAUDER:
                    return InfluenceMaps.point6RangevsGround;
                case TERRAN_HELLION_TANK:
                    return InfluenceMaps.point2RangevsGround;
            }
        }
        if (priority == MicroPriority.SURVIVAL && unit.unit().getType() == Units.TERRAN_SCV) {
            return InfluenceMaps.pointThreatToGroundPlusBuffer;
        }
        return isGround ? InfluenceMaps.pointThreatToGround : InfluenceMaps.pointThreatToAir;
    }

    public void onStep() {
        if (!isAlive()) {
            onDeath();
            return;
        }

        if (isMorphing()) {
            return;
        }

        //attack if available
        if (attackIfAvailable()) {
            return;
        }

        //done if unit is immobile
        if (!UnitUtils.canMove(unit.unit())) {
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

        //detour if needed
        if (!isSafe() && !neverDetour()) {
            detour();
            return;
        }

        //finishing step on arrival
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 2.5f) {
            onArrival();
            return;
        }

        //continue moving to target
        if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
            return;
        }
    }

    //only dodge splash damage for short-range DPS and all GET_TO_DESTINATION units
    private boolean neverDetour() {
        return (priority == MicroPriority.GET_TO_DESTINATION ||
                        (priority == MicroPriority.DPS && canAttackGround && groundAttackRange - unit.unit().getRadius() < 2)) &&
                !isInSplashDamage();
    }

    protected void setTargetPos() {
        if (canAttackAir && canAttackGround) {
            targetPos = ArmyManager.attackEitherPos;
        }
        else if (canAttackAir) {
            targetPos = ArmyManager.attackAirPos;
        }
        else if (ArmyManager.attackGroundPos != null) {
            targetPos = ArmyManager.attackGroundPos;
        }
        else {
            setFinishHimTarget();
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
            if (unit.unit().getType() == Units.TERRAN_THOR_AP) {
                Chat.chat("Thor Targetting: " + attackTarget.unit().getType());
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
                order.get().targetPos.distance(targetPos) < 0.5f;
    }

    protected boolean isAttackingTarget(Tag targetTag) {
        Optional<ActionIssued> order = ActionIssued.getCurOrder(unit);
        return order.isPresent() &&
                order.get().ability == Abilities.ATTACK &&
                targetTag.equals(order.get().targetTag);

    }

    protected boolean isTargettingUnit(Unit target) {
        return target != null && ActionIssued.getCurOrder(unit).stream()
                .anyMatch(order -> target.getTag().equals(order.targetTag));
    }

    protected boolean hasLockOnBuff() {
        return unit.unit().getBuffs().contains(Buffs.LOCK_ON);
    }

    //selects target based on cost:health ratio
    public UnitInPool selectTarget() {
        UnitInPool selectedTarget = selectTarget(false);
        if (selectedTarget == null) {
            selectedTarget = selectTarget(true);
        }
        return selectedTarget;
    }

    //prefers targets it can do bonus damage to
    public UnitInPool selectTarget(boolean includeIgnoredTargets) {
        List<UnitInPool> enemiesInRange = getValidTargetList(includeIgnoredTargets);
        float bestTargetValue = 0;
        UnitInPool bestTargetUip = null;
        for (UnitInPool enemy : enemiesInRange) {
            //ignore barriered immortals
            if (enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                continue;
            }

            //tanks target other tanks first
            if (UnitUtils.SIEGE_TANK_TYPE.contains(enemy.unit().getType()) &&
                    (UnitUtils.SIEGE_TANK_TYPE.contains(unit.unit().getType()) ||
                            unit.unit().getType() == Units.TERRAN_MARAUDER)) {
                return enemy;
            }

            float numShotsToKill = Math.max(
                    1,
                    (enemy.unit().getHealth().orElse(0f) + enemy.unit().getShield().orElse(0f)) /
                            UnitUtils.getDamage(unit.unit(), enemy.unit())
            );
            float enemyCost = (enemy.unit().getType() == UnitUtils.enemyWorkerType) ?
                    75 : //inflate value of workers as they impact income
                    Math.max(1, UnitUtils.getCost(enemy.unit()).getValue()); //value gas more than minerals
            float enemyValue = enemyCost/numShotsToKill;
            if (enemyValue > bestTargetValue) {
                bestTargetValue = enemyValue;
                bestTargetUip = enemy;
            }
        }
        return bestTargetUip;
    }

    private List<UnitInPool> getValidTargetList(boolean includeIgnoredTargets) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                ((isAir(enemy) && canAttackAir) || (!isAir(enemy) && canAttackGround)) && //can attack ground/air
                UnitUtils.getDistance(enemy.unit(), unit.unit()) <=
                        (isAir(enemy) ? airAttackRange : groundAttackRange) + enemy.unit().getRadius() && //in attack range
                !UnitUtils.UNTARGETTABLES.contains(enemy.unit().getType()) && //not a dummy unit
                (includeIgnoredTargets || !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType())) && //handle lesser targets
                !UnitUtils.isSnapshot(enemy.unit()) && //not in fog / unspotted high ground
                enemy.unit().getDisplayType() == DisplayType.VISIBLE); //not cloaked/burrowed
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
        if (UnitUtils.WIDOW_MINE_TYPE.contains(unit.unit().getType())) {
            canAttackAir = true;
            canAttackGround = true;
            airAttackRange = groundAttackRange = 5 + unit.unit().getRadius() + 0.25f;
            return;
        }

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

    public boolean isSafe() {
        return isSafe(unit.unit().getPosition().toPoint2d());
    }

    protected boolean isSafe(Point2d pos) {
        return !InfluenceMaps.getValue(getThreatMap(), pos) && !isInSplashDamage(pos);
    }

    protected Point2d findDetourPos() {
        return doDetourAroundEnemy ? findDetourPos2(2f) : findDetourPos(2f);
    }

    //tries to go around the threat
    protected Point2d findDetourPos2(float rangeCheck) {
        Point2d towardsTarget = Position.towards(unit.unit(), targetPos, rangeCheck + unit.unit().getRadius());
        for (int i=0; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? i : -i;
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            if (isSafe(detourPos)) {
                if (i > 200 && !changedDirectionRecently()) {
                    toggleDodgeClockwise();
                }
                //add 1degree more angle as buffer, to account for chasing units
                i += 1;
                angle = (isDodgeClockwise) ? i : -i;
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
        //first try going straight back home
        Point2d towardsRetreatPos = Position.towards(unit.unit(), ArmyManager.retreatPos, 2);
        if (isSafe(towardsRetreatPos)) {
            return ArmyManager.retreatPos;
        }

        Point2d towardsTarget = Position.towards(unit.unit(), targetPos, rangeCheck);
        for (int i=180; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? i : -i;
            Point2d detourPos = Position.rotate(towardsTarget, unit.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos == null || !isPathable(detourPos)) {
                continue;
            }
            if (isSafe(detourPos)) {
                return detourPos;
            }
        }
        for (int i=180; i<360; i+=15) {
            int angle = (isDodgeClockwise) ? -i : i;
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
            } else if (canAttackGround) {
                targetPos = ArmyManager.attackGroundPos;
            } else {
                targetPos = ArmyManager.attackAirPos;
            }
        //find last structures with random reachable positions
        } else if (targetPos == null || UnitUtils.getDistance(unit.unit(), targetPos) < 3) { //switch positions when it arrives
            do {
                targetPos = Bot.OBS.getGameInfo().findRandomLocation();
            } while (isGround && !Bot.OBS.isPathable(targetPos));
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


    protected Optional<Point2d> getClosestRepairBay() {
        return getClosestRepairBay(unit.unit().getPosition().toPoint2d());
    }

    protected Optional<Point2d> getClosestRepairBay(Point2d unitPos) {
        return GameCache.baseList.stream()
                .filter(base -> base.isMyBase() &&
                        base.isComplete() &&
                        !base.isMyMainBase() &&
                        base.numMineralScvs() + base.numScvRepairers() >= 4 &&
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

    //excludes reapers, hellions, adepts, oracles, when on offense, excludes structures
    //priority on overcharged batteries, then units that shoot ground, then everything else
    protected Unit getClosestEnemyThreatToGround() {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        !enemy.unit().getHallucination().orElse(false) &&
                                !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                                (!ArmyManager.doOffense || !UnitUtils.DONT_CHASE_TYPES.contains(enemy.unit().getType())) &&
                                enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                                (!UnitUtils.isSnapshot(enemy.unit()) || enemy.unit().getType() == Units.PROTOSS_PYLON) &&
                                (UnitUtils.canAttackGround(enemy.unit()) || enemy.unit().getType() == Units.PROTOSS_PYLON) &&
                                UnitUtils.getDistance(unit.unit(), enemy.unit()) < 15)
                .stream()
                .min(Comparator.comparing(enemy ->
                        UnitUtils.getDistance(unit.unit(), enemy.unit()) +
                        (!enemy.unit().getBuffs().contains(Buffs.BATTERY_OVERCHARGE) ? 1000 : 0) +
                        (!UnitUtils.canAttackGround(enemy.unit()) ? 100 : 0)
                ))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    protected boolean doStutterForward(Unit closestEnemyThreat) {
        if (closestEnemyThreat == null) {
            return true;
        }

        boolean isEnemyRetreating = UnitUtils.isEnemyRetreating(closestEnemyThreat, unit.unit().getPosition().toPoint2d());
        double myAttackRange = UnitUtils.getAttackRange(unit.unit(), closestEnemyThreat);
        double enemyAttackRange = (closestEnemyThreat.getType() == Units.ZERG_BANELING || closestEnemyThreat.getType() == Units.ZERG_BANELING_BURROWED) ?
                4 :
                UnitUtils.getAttackRange(closestEnemyThreat, unit.unit());


        //doStutterForward if enemy outranges me (or is weaponless)
        return enemyAttackRange == 0 ||
                isEnemyRetreating ||
                myAttackRange < enemyAttackRange ||
                myAttackRange + closestEnemyThreat.getRadius() < UnitUtils.getDistance(unit.unit(), closestEnemyThreat);
    }

    protected boolean isFlying() {
        return unit.unit().getFlying().orElse(false);
    }

    protected boolean isInSplashDamage() {
        return isInSplashDamage(unit.unit().getPosition().toPoint2d());
    }

    protected boolean isInSplashDamage(Point2d pos) {
        return InfluenceMaps.getValue(
                (isFlying() ? InfluenceMaps.pointPersistentDamageToAir : InfluenceMaps.pointPersistentDamageToGround),
                pos
        );
    }

    protected void move(Unit targetUnit) {
        ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetUnit.getPosition().toPoint2d(), false);
    }

    protected void move(Point2d targetPos) {
        ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
    }
}
