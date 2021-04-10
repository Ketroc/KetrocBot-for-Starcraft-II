package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.UnitAttribute;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.CycloneKillTracker;
import com.ketroc.terranbot.utils.*;

import java.util.*;
import java.util.stream.Collectors;

//TODO: if target dies before it gets a LOCK_ON buff, then don't go on cooldown
public class Cyclone extends BasicUnitMicro {
    //list of unit types to never lock-on to TODO: check if KD8Charge unit ever exists
    public static final Set<Units> NEVER_LOCK_TYPES = new HashSet<>(Set.of(
            Units.ZERG_LARVA, Units.ZERG_EGG, Units.ZERG_BROODLING, Units.TERRAN_MULE,
            Units.PROTOSS_INTERCEPTOR, Units.PROTOSS_DISRUPTOR_PHASED, Units.TERRAN_KD8CHARGE,
            Units.ZERG_PARASITIC_BOMB_DUMMY));
    //list of units to soft-lock-on (keep reassessing)
    public static final Set<Units> SOFT_LOCK_TYPES = new HashSet<>(Set.of(
            Units.ZERG_LOCUS_TMP, Units.ZERG_LOCUS_TMP_FLYING, Units.TERRAN_MARINE,
            Units.ZERG_ZERGLING, Units.ZERG_CHANGELING_MARINE, Units.ZERG_CHANGELING_MARINE_SHIELD,
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

        //no new actions if currently locking on
        if (UnitUtils.getOrder(unit.unit()) == Abilities.EFFECT_LOCK_ON) {
            return;
        }

        //done if unit is immobile
        if (!UnitUtils.canMove(unit.unit())) {
            return;
        }

        //find new target for lock
        if (lockTarget == null && !isLockOnCooldown()) {
            if (setLockTarget()) {
                return;
            }
        }

        //detour if unsafe
        if (!isSafe()) {
            detour();
            return;
        }

        //continue moving to target
        if (!isMovingToTargetPos()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
        }

        //super.onStep();
    }

    private void updateTargetPos() {
        //locked on
        if (lockTarget != null) {
            targetPos = lockTarget.unit().getPosition().toPoint2d();
        }
        //lock is on cooldown
        else if (isLockOnCooldown()) {
            targetPos = LocationConstants.insideMainWall;
        }
        //attacking
        else {
            targetPos = ArmyManager.attackAirPos;
        }
    }

    private boolean setLockTarget() {
        UnitInPool closestHardLockTarget = GameCache.allVisibleEnemiesList.stream()
                .filter(enemy -> !NEVER_LOCK_TYPES.contains(enemy.unit().getType()) &&
                        !SOFT_LOCK_TYPES.contains(enemy.unit().getType()) &&
                        UnitUtils.getDistance(enemy.unit(), unit.unit()) - enemy.unit().getRadius() <= 10 &&
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        targetAcceptingMoreLocks(enemy))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), unit.unit())))
                .orElse(null);
        if (closestHardLockTarget != null) {
            lockOn(closestHardLockTarget);
            return true;
        }

        UnitInPool closestSoftLockTarget = GameCache.allVisibleEnemiesList.stream()
                .filter(enemy -> SOFT_LOCK_TYPES.contains(enemy.unit().getType()) &&
                        UnitUtils.getDistance(enemy.unit(), unit.unit()) - enemy.unit().getRadius() <= 10 &&
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        targetAcceptingMoreLocks(enemy))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), unit.unit())))
                .orElse(null);
        if (closestSoftLockTarget != null) {
            if (UnitUtils.getDistance(closestSoftLockTarget.unit(), unit.unit()) < 7.1) {
                lockOn(closestSoftLockTarget);
            }
            else {
                ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, closestSoftLockTarget.unit(), false);
            }
            return true;
        }

        return false;
    }

    //nothing locked on yet, or armored attack unit with > 90hp, or armored structure with > 250hp
    private boolean targetAcceptingMoreLocks(UnitInPool enemy) {
        return !Cyclone.containsTarget(enemy.getTag()) ||
                (UnitUtils.getAttributes(enemy.unit()).contains(UnitAttribute.ARMORED) &&
                        (UnitUtils.getTotalHealth(enemy.unit()) > 250 ||
                                UnitUtils.canAttack(enemy.unit().getType()) &&
                                UnitUtils.getTotalHealth(enemy.unit()) > 90));

    }

    private void lockOn(UnitInPool lockTarget) {
        this.lockTarget = lockTarget;
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_LOCK_ON, lockTarget.unit(), false);
    }

    private boolean isLockOnCooldown() {
        return COOLDOWN_DURATION >= Time.nowFrames() - cooldownStartFrame;
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
                    Cost killCost = Cost.getUnitCost(lockTarget.unit().getType());
                    killTracker.totalKillValue.add(killCost);
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
                .anyMatch(cyclone -> cyclone.lockTarget != null &&
                        cyclone.lockTarget.getTag().equals(targetTag));
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
}
