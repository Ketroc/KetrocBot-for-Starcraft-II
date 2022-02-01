package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.CycloneKillTracker;
import com.ketroc.utils.*;

import java.util.*;

public class Cyclone extends BasicUnitMicro {
    //list of unit types to never lock-on to TODO: check if KD8Charge unit ever exists
    public static final Set<Units> NEVER_LOCK_TYPES = new HashSet<>(Set.of(
            Units.ZERG_LARVA, Units.ZERG_EGG, Units.ZERG_BROODLING, Units.ZERG_PARASITIC_BOMB_DUMMY,
            Units.ZERG_CREEP_TUMOR, Units.ZERG_CREEP_TUMOR_BURROWED, Units.ZERG_CREEP_TUMOR_QUEEN,
            Units.ZERG_CHANGELING, Units.ZERG_CHANGELING_MARINE, Units.ZERG_CHANGELING_MARINE_SHIELD,
            Units.PROTOSS_INTERCEPTOR, Units.PROTOSS_ADEPT_PHASE_SHIFT, Units.PROTOSS_DISRUPTOR_PHASED,
            Units.TERRAN_KD8CHARGE, Units.TERRAN_MULE));
    //list of units to soft-lock-on (keep reassessing)
    public static final Set<Units> SOFT_LOCK_TYPES = new HashSet<>(Set.of(
            Units.ZERG_LOCUS_TMP, Units.ZERG_LOCUS_TMP_FLYING, Units.TERRAN_MARINE,
            Units.ZERG_ZERGLING, Units.TERRAN_SCV, Units.ZERG_DRONE, Units.ZERG_DRONE_BURROWED,
            Units.PROTOSS_PROBE));

    public static final Set<Units> AUTOATTACK_WHEN_UNSAFE = new HashSet<>(Set.of(
            Units.ZERG_ZERGLING, Units.TERRAN_REAPER, Units.TERRAN_HELLION,
            Units.ZERG_MUTALISK, Units.PROTOSS_PHOENIX, //Units.PROTOSS_INTERCEPTOR,
            Units.TERRAN_SCV, Units.ZERG_DRONE, Units.ZERG_DRONE_BURROWED, Units.PROTOSS_PROBE));

    public static Map<Tag, CycloneKillTracker> cycloneKillTracker = new HashMap<>();
    private UnitInPool lockTarget;
    private long cooldownStartFrame;
    private static final long COOLDOWN_DURATION = 100;

    public Cyclone(Unit unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
        cycloneKillTracker.put(unit.getTag(), new CycloneKillTracker(Time.nowFrames()));
    }

    public Cyclone(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
        cycloneKillTracker.put(unit.getTag(), new CycloneKillTracker(Time.nowFrames()));
    }

    public Cyclone(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
        cycloneKillTracker.put(unit.getTag(), new CycloneKillTracker(Time.nowFrames()));
    }

    public Cyclone(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
    }

    @Override
    public void onStep() {
        removeLockTarget();
        updateTargetPos();
        visualizeLock();
        visualizeCooldown();

        if (!isAlive()) {
            onDeath();
            return;
        }

        //if currently trying to lock on
        if (lockTarget != null && UnitUtils.getOrder(unit.unit()) == Abilities.EFFECT_LOCK_ON) {
            if (!isSafeToAttemptLock(lockTarget)) {
                lockTarget = null;
            }
            else {
                return;
            }
        }

        //if cyclone is immobile
        if (!UnitUtils.canMove(unit.unit())) {
            return;
        }

        //find new target for lock
        if (lockTarget == null && !isLockOnCooldown()) {
            if (setLockTarget()) {
                return;
            }
        }

        //use basic attack
        if (lockTarget == null && UnitUtils.isWeaponAvailable(unit.unit())) {
            if (UnitUtils.isAttacking(unit.unit())) { //allow attack animation to complete
                return;
            }
            Optional<Unit> autoAttackTarget = getAutoAttackTarget();
            if (autoAttackTarget.isPresent()) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, autoAttackTarget.get(), false);
                return;
            }
        }

        //detour if unsafe
        if (!isSafe() && !stayInDamageRange()) {
            Point2d towardsRetreatPos = Position.towards(unit.unit(), ArmyManager.retreatPos, 2);
            if (isSafe(towardsRetreatPos)) { //first try going straight back
                ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, ArmyManager.retreatPos, false);
            }
            else {
                detour();
            }
            return;
        }

        //continue moving to target
        if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }

        //super.onStep();
    }

    private boolean stayInDamageRange() {
        //always false unless at edge of lock range
        if (lockTarget == null || UnitUtils.getRange(lockTarget.unit(), unit.unit()) < 13.5f) {
            return false;
        }

        //be suicidal vs tempests
        if (lockTarget.unit().getType() == Units.PROTOSS_TEMPEST) {
            return true;
        }

        //maintain lock if target near dead
        return targetNearDeath();
    }

    private boolean targetNearDeath() {
        if (lockTarget == null) {
            return false;
        }
        int hpThreshold = (UnitUtils.hasUpgrade(Upgrades.CYCLONE_LOCK_ON_DAMAGE_UPGRADE) &&
                UnitUtils.getAttributes(lockTarget.unit()).contains(UnitAttribute.ARMORED)) ? 80 : 40;
        return lockTarget.unit().getHealth().orElse(9999f) < hpThreshold;
    }

    //choose targets to auto-attack
    private Optional<Unit> getAutoAttackTarget() {
        //if unsafe, attack fast units like zerglings/workers/hellions/reapers/etc, or 1shot kills
        List<UnitInPool> enemiesInRange = UnitUtils.getEnemyTargetsInRange(unit.unit());
        if (!isSafe()) {
            return enemiesInRange.stream()
                    .filter(enemyInRange -> AUTOATTACK_WHEN_UNSAFE.contains(enemyInRange.unit().getType()) ||
                            UnitUtils.canOneShotEnemy(unit.unit(), enemyInRange.unit()))
                    .min(Comparator.comparing(enemyInRange -> enemyInRange.unit().getHealth().orElse(9999f)))
                    .map(UnitInPool::unit);
        }
        //if safe, attack any unit within range TODO: include destructible neutral units??
        else {
            return enemiesInRange.stream()
                    .filter(enemyInRange -> !UnitUtils.IGNORED_TARGETS.contains(enemyInRange.unit().getType()))
                    .min(Comparator.comparing(enemyInRange -> enemyInRange.unit().getHealth().orElse(9999f)))
                    .map(UnitInPool::unit);
        }
    }

    private boolean isSafeToAttemptLock(UnitInPool lockTarget) {
        if (Switches.isDivingTempests ||
                lockTarget.unit().getType() == Units.PROTOSS_TEMPEST ||
                UnitUtils.THOR_TYPE.contains(lockTarget.unit().getType()) ||
                UnitUtils.getDistance(lockTarget.unit(), unit.unit()) +
                        lockTarget.unit().getRadius() + unit.unit().getRadius() <= 7) {
            return true;
        }
        Point2d posForLock = getPosForLock(lockTarget.unit());
        int dmgAtLockPos = InfluenceMaps.getValue(InfluenceMaps.pointDamageToGroundValue, posForLock);
        return dmgAtLockPos < 40;
    }

    protected void updateTargetPos() {
        //locked on
        if (lockTarget != null) {
            targetPos = lockTarget.unit().getPosition().toPoint2d();
            return;
        }

        //go to a repair bay
        Optional<Point2d> closestRepairBay = getClosestRepairBay();
        if (closestRepairBay.isPresent() && (requiresRepairs(60) || underRepair(closestRepairBay.get()))) {
            targetPos = closestRepairBay.get();
            return;
        }

        //lock is on cooldown
        if (isLockOnCooldown()) {
            targetPos = LocationConstants.insideMainWall;
            return;
        }

        //lock is off cooldown
        super.updateTargetPos();
    }

    private boolean setLockTarget() {
        UnitInPool target = null;
        if (Switches.enemyHasTempests) {
            target = GameCache.allVisibleEnemiesList.stream()
                    .filter(enemy -> enemy.unit().getType() == Units.PROTOSS_TEMPEST &&
                            !UnitUtils.isSnapshot(enemy.unit()) &&
                            UnitUtils.getDistance(enemy.unit(), unit.unit()) - enemy.unit().getRadius() <= 15)
                    .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), unit.unit())))
                    .orElse(null);
        }
        if (target == null) {
            target = GameCache.allVisibleEnemiesList.stream()
                    .filter(enemy -> !NEVER_LOCK_TYPES.contains(enemy.unit().getType()) &&
                            !SOFT_LOCK_TYPES.contains(enemy.unit().getType()) &&
                            enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                            !UnitUtils.isSnapshot(enemy.unit()) &&
                            (!UnitUtils.isStructure(enemy.unit().getType()) || UnitUtils.canAttack(enemy.unit().getType())) && //units or attacking structures
                            UnitUtils.getDistance(enemy.unit(), unit.unit()) - enemy.unit().getRadius() <=
                                    getRangeToCheck((Units) enemy.unit().getType()) &&
                            targetAcceptingMoreLocks(enemy) &&
                            isSafeToAttemptLock(enemy))
                    .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), unit.unit())))
                    .orElse(null);
        }
        if (target == null) {
            target = GameCache.allVisibleEnemiesList.stream()
                    .filter(enemy -> !NEVER_LOCK_TYPES.contains(enemy.unit().getType()) &&
                            enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                            !UnitUtils.isSnapshot(enemy.unit()) &&
                            UnitUtils.getDistance(enemy.unit(), unit.unit()) - enemy.unit().getRadius() <=
                                    getRangeToCheck((Units) enemy.unit().getType()) &&
                            targetAcceptingMoreLocks(enemy) &&
                            isSafeToAttemptLock(enemy))
                    .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), unit.unit())))
                    .orElse(null);
        }
        if (target == null) {
            return false;
        }

        //give lock command
        if (!SOFT_LOCK_TYPES.contains(target.unit().getType()) ||
                UnitUtils.getDistance(target.unit(), unit.unit()) < 7.1) {
            lockOn(target);
        }
        else { //move towards soft target but reassess every frame
            ActionHelper.unitCommand(unit.unit(),
                    Abilities.MOVE,
                    Position.towards(target.unit().getPosition().toPoint2d(), unit.unit().getPosition().toPoint2d(), 6f),
                    false);
        }
        return true;
    }

    private float getRangeToCheck(Units type) {
        return (Switches.isDivingTempests || type == Units.PROTOSS_TEMPEST) ? 15 : 10;
    }

    //nothing locked on yet, or armored attack unit with > 100hp, or armored structure with > 300hp
    private boolean targetAcceptingMoreLocks(UnitInPool enemy) {
        if (enemy.unit().getType() == Units.PROTOSS_TEMPEST) { //max locks for tempests
            return true;
        }
        int numLocks = Cyclone.numLocks(enemy.getTag());
        return numLocks == 0 || (
                UnitUtils.getAttributes(enemy.unit()).contains(UnitAttribute.ARMORED) && (
                        UnitUtils.getTotalHealth(enemy.unit())/numLocks > 900 || ( //1:pylon/extractor, 2:gateway/barracks/cc, 3:nexus/lair/hive
                                UnitUtils.canAttack(enemy.unit().getType()) &&
                                UnitUtils.getTotalHealth(enemy.unit())/numLocks > 160 //1:stalker/ravager, 2:tempest/tank, 3:BC/thor
                        )
                )
        );
    }

    private void lockOn(UnitInPool lockTarget) {
        this.lockTarget = lockTarget;
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_LOCK_ON, lockTarget.unit(), false);
    }

    private boolean isLockOnCooldown() {
        return !MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_LOCK_ON);
    }

    private void visualizeCooldown() {
        long cooldownInSeconds = COOLDOWN_DURATION - (Time.nowFrames() - cooldownStartFrame);
        if (cooldownInSeconds >= 0) {
            DebugHelper.drawText(cooldownInSeconds + "",
                    unit.unit().getPosition().toPoint2d(),
                    Color.BLUE);
        }
    }

    private void visualizeLock() {
        if (lockTarget != null) {
            DebugHelper.drawLine(unit.unit().getPosition(),
                    lockTarget.unit().getPosition(),
                    Color.YELLOW);
        }
    }

    private void removeLockTarget() {
        if (lockTarget != null) {
            if (!isLockedOn() && UnitUtils.getOrder(unit.unit()) != Abilities.EFFECT_LOCK_ON) {
                //add to tracked kills
                if (!lockTarget.isAlive()) {
                    CycloneKillTracker killTracker = cycloneKillTracker.get(unit.getTag());
                    killTracker.killCount++;
                    killTracker.totalKillValue.add(lockTarget.unit().getType(), 1);
                }

                lockTarget = null;
                cooldownStartFrame = Time.nowFrames();
            }
        }
    }

    @Override
    public void onArrival() {

    }

    public boolean isLockedOn() {
        return lockTarget != null &&
                lockTarget.isAlive() &&
                lockTarget.unit().getBuffs().contains(Buffs.LOCK_ON) &&
                lockTarget.getLastSeenGameLoop() == Time.nowFrames() &&
                lockTarget.unit().getDisplayType() == DisplayType.VISIBLE &&
                UnitUtils.getDistance(unit.unit(), lockTarget.unit()) -
                        unit.unit().getRadius() - lockTarget.unit().getRadius() <= 15;
    }

    //TODO: move this???
    public static boolean containsTarget(Tag targetTag) {
        return UnitMicroList.getUnitSubList(Cyclone.class).stream()
                .anyMatch(cyclone -> cyclone.lockTarget != null && cyclone.lockTarget.getTag().equals(targetTag));
    }

    //TODO: move this???
    public static int numLocks(Tag targetTag) {
        return (int)UnitMicroList.getUnitSubList(Cyclone.class).stream()
                .filter(cyclone -> cyclone.lockTarget != null && cyclone.lockTarget.getTag().equals(targetTag))
                .count();
    }

    //TODO: move this???
    public static void cycloneKillReport() {
        System.out.println("Cyclone Kill Report");
        System.out.println("===================");
        cycloneKillTracker.values().stream()
                .sorted(Comparator.comparing(cyclone -> cyclone.createdFrame))
                .forEach(cyclone -> {
                    System.out.println(
                            "created on: " + cyclone.createdFrame +
                            "\t kills: " + cyclone.killCount +
                            "\t minerals: " + cyclone.totalKillValue.minerals +
                            "\t gas: " + cyclone.totalKillValue.gas +
                            "\t supply: " + cyclone.totalKillValue.supply);
                });
        int cyclonesDied = cycloneKillTracker.size() -
                Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_CYCLONE).size();
        int totalKillsMinerals = cycloneKillTracker.values().stream()
                .mapToInt(cyclone -> cyclone.totalKillValue.minerals)
                .sum();
        int totalKillsGas = cycloneKillTracker.values().stream()
                .mapToInt(cyclone -> cyclone.totalKillValue.gas)
                .sum();
        int numCyclones = cycloneKillTracker.size();
        int cycloneDeathCostMinerals = 150 * cyclonesDied;
        int cycloneDeathCostGas = 100 * cyclonesDied;
        System.out.println("Total cyclones produced: " + numCyclones);
        System.out.println("Total cyclones died: " + cyclonesDied);
        System.out.println("Cyclones death cost: " + cycloneDeathCostMinerals +  "/" + cycloneDeathCostGas);
        System.out.println("Cyclones kills value: " + totalKillsMinerals +  "/" + totalKillsGas);

    }

    //position for front of cyclone where lock will be established
    private Point2d getPosForLock(Unit targetUnit) {
        return Position.towards(targetUnit.getPosition().toPoint2d(), //tank pos
                unit.unit().getPosition().toPoint2d(), //cyclone pos
                8.5f + targetUnit.getRadius()); //7 lock range + 1.5 buffer + cyclone radius
    }
}
