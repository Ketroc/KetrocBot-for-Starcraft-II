package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class WidowMine extends BasicUnitMicro {
    public static final int COOLDOWN = 650; //cooldown in frames
    public static final int REPAIR_AT = 50;
    public static int burrowFrames = 56 + Launcher.STEP_SIZE;
    public static int fireFrames = 22 + Launcher.STEP_SIZE;
    public long lastAttackFrame;
    public long lastManualTargetFrame;
    public UnitInPool lastManualTarget;
    public Base offenseTargetBase;

    public WidowMine(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
        offenseTargetBase = LocationConstants.nextEnemyBase;
    }

    public WidowMine(UnitInPool unit, Point2d targetPos) {
        this(unit.unit(), targetPos);
    }

    protected void updateCooldown() {
        if (isBurrowed() &&
                !isOnCooldown() &&
                !MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_WIDOWMINE_ATTACK)) {
            lastAttackFrame = Time.nowFrames();
        }
    }

    @Override
    protected void updateTargetPos() {
        if (unit.unit().getHealth().orElse(0f) < REPAIR_AT ||
                getCooldownRemaining() > 250 ||
                isUnderRepair()) {
            if (UnitUtils.getHealthPercentage(unit.unit()) >= 99) {
                targetPos = ArmyManager.retreatPos;
                return;
            }
            targetPos = getClosestRepairBay().orElse(ArmyManager.retreatPos);
            return;
        }
        if (ArmyManager.doOffense) {
            if (offenseTargetBase == null) {
                offenseTargetBase = UnitUtils.getNextEnemyBase();
            }
            if (offenseTargetBase != null) {
                targetPos = offenseTargetBase.getResourceMidPoint();
                return;
            }
        }
        super.updateTargetPos();
    }

    @Override
    public void onStep() {
        if (!isAlive()) {
            onDeath();
            return;
        }

        if (isBusy()) {
            return;
        }

        updateTargetPos();
        DebugHelper.drawBox(targetPos, Color.GREEN, 0.5f);
        DebugHelper.boxUnit(unit.unit());
        Bot.DEBUG.sendDebug();
        updateCooldown();

        //search for final structures
        if (targetPos == null) {
            finalStructureSearch();
            return;
        }

        if (!isBurrowed()) {
            if (!isSafe()) {
                if (!isDetected()) {
                    burrow();
                    return;
                }
                if (unit.unit().getBuffs().contains(Buffs.LOCK_ON)) {
                    retreatFromLockOn();
                    return;
                }
                detour();
                return;
            }
            //unburrowed and safe
            if (isAtTargetPosition() || (!isOnCooldown() && !getTargets().isEmpty())) {
                burrow();
                return;
            }
            moveToTargetPos();
            return;
        }

        //burrowed
        //========
        UnitInPool bestTarget = null; // no target when on cooldown
        if (!isOnCooldown()) { // TODO what if 1sec from off cooldown?
            bestTarget = getBestTarget();
        }

        // can't shoot && (it's unsafe || it wants to move forward)
        if (bestTarget == null &&
                ((!isSafe() && isDetected()) || (isSafePlusBuffer() && !isAtTargetPosition()))) {
            unburrow();
            return;
        }
        if (bestTarget != null && !isOnCooldown()) { //time to attack
            attack(bestTarget);
            return;
        }
        if (isAtEnemyBasePos() && getTargets().isEmpty()) { //arrival at enemy base with no targets left
            offenseTargetBase = UnitUtils.getNextEnemyBase(offenseTargetBase.getCcPos());
        }
        //does nothing when burrowed at targetPos, or burrowed hiding on cooldown for safety
    }

    private boolean isAtTargetPosition() {
        return UnitUtils.getDistance(unit.unit(), targetPos) < (isBurrowed() ? 4 : 2.5);
    }

    private boolean isAtEnemyBasePos() {
        return UnitUtils.getDistance(unit.unit(), offenseTargetBase.getResourceMidPoint()) <= 3.05;
    }

    private void retreatFromLockOn() {
        Point2d nearestCyclonePos = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_CYCLONE).stream()
                .filter(cyclone -> UnitUtils.getDistance(unit.unit(), cyclone.unit()) <= 16.5)
                .min(Comparator.comparing(cyclone -> UnitUtils.getDistance(unit.unit(), cyclone.unit())))
                .map(u -> u.unit().getPosition().toPoint2d())
                .orElse(null);
        if (nearestCyclonePos != null) {
            ActionHelper.unitCommand(
                    unit.unit(),
                    Abilities.MOVE,
                    Position.towards(unit.unit(), nearestCyclonePos, -3),
                    false);
        }
        else {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, ArmyManager.retreatPos, false);
        }
    }

    private void finalStructureSearch() {
        if (isBurrowed()) {
            unburrow();
        }
        else if (ActionIssued.getCurOrder(unit).isEmpty()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, UnitUtils.getRandomPathablePos(), false);
        }
        return;
    }

    @Override
    public void onArrival() {
        if (!isBurrowed()) {
            burrow();
        }
    }

    @Override
    protected boolean isSafe() {
        return this.isSafe(unit.unit().getPosition().toPoint2d());
    }

    @Override
    protected boolean isSafe(Point2d p) { //TODO: splash effects like storm, ravager bile
        if (unit.unit().getBuffs().contains(Buffs.LOCK_ON)) {
            return false;
        }

        return !InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, p);
    }

    protected boolean isSafePlusBuffer() {
        return this.isSafePlusBuffer(unit.unit().getPosition().toPoint2d());
    }

    protected boolean isSafePlusBuffer(Point2d p) { //TODO: splash effects like storm, ravager bile
        if (unit.unit().getBuffs().contains(Buffs.LOCK_ON)) {
            return false;
        }

        return !InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundPlusBuffer, p);
    }

    public boolean isOnCooldown() {
        return Time.nowFrames() < lastAttackFrame + COOLDOWN;
    }

    public int getCooldownRemaining() {
        return (int)Math.max(0, COOLDOWN - (Time.nowFrames() - lastAttackFrame));
    }

    public boolean isCloaked() {
        return isBurrowed() && (hasPermaCloak() || isAttackAvailable());
    }

    private boolean isAttackAvailable() {
        return MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_WIDOWMINE_ATTACK);
    }

    public boolean isDetected() {
        if (UnitUtils.hasDecloakBuff(unit.unit()) || (isBurrowed() && unit.unit().getBuffs().contains(Buffs.LOCK_ON))) {
            return true;
        }
        if (!hasPermaCloak() && getCooldownRemaining() > burrowFrames) { //if burrowing doesn't grant cloak
            return true;
        }
        return InfluenceMaps.getValue(InfluenceMaps.pointDetected, unit.unit().getPosition().toPoint2d());
    }

    protected boolean isUnderRepair() {
        Optional<Point2d> repairBayPos = getClosestRepairBay();
        if (repairBayPos.isEmpty()) {
            return false;
        }
        return UnitUtils.getHealthPercentage(unit.unit()) < 100 &&
                UnitUtils.getDistance(unit.unit(), repairBayPos.get()) <= 4;
    }

    protected boolean isBurrowed() {
        return unit.unit().getType() == Units.TERRAN_WIDOWMINE_BURROWED;
    }

    protected boolean isBusy() {
        return ActionIssued.getCurOrder(unit).stream().anyMatch(action -> action.ability.toString().startsWith("BURROW")) ||
                isAttacking();
    }

    protected void burrow() {
        ActionHelper.unitCommand(unit.unit(), Abilities.BURROW_DOWN_WIDOWMINE, false);
    }

    protected void unburrow() {
        ActionHelper.unitCommand(unit.unit(), Abilities.BURROW_UP_WIDOWMINE, false);
    }

    protected void moveToTargetPos() {
        ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
    }

    protected void attack(UnitInPool target) {
        //TODO: there is a lot of missed attacks and double targetting, so turned off to allow automatic targetting in game
//        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_WIDOWMINE_ATTACK, target.unit(), false);
//        lastManualTargetFrame = Time.nowFrames();
//        lastManualTarget = target;
    }

    protected boolean isAttacking() {
        return isManuallyTargetted() &&
                ActionIssued.getCurOrder(unit).stream().anyMatch(action -> action.ability == Abilities.EFFECT_WIDOWMINE_ATTACK);
    }

    protected boolean isManuallyTargetted() {
        if (lastManualTargetFrame + 24 > Time.nowFrames()) {
            if (lastManualTarget.isAlive() && isWithinAttackRange(lastManualTarget)) {
                return true;
            }
            lastManualTargetFrame = 0;
        }
        return false;
    }

    private boolean isWithinAttackRange(UnitInPool target) {
        return UnitUtils.getDistance(target.unit(), unit.unit()) -
                target.unit().getRadius() - unit.unit().getRadius() < 5;
    }

    private boolean shouldChangeTargets(UnitInPool target) {
        Optional<ActionIssued> curOrder = ActionIssued.getCurOrder(unit);
        return curOrder.isEmpty() ||
                (curOrder.stream().noneMatch(action -> action.targetTag.equals(target.getTag())) &&
                        unit.unit().getHealth().orElse(0f) > 25f &&
                        (isSafe() || !isDetected()));
    }

    protected Tag getCurrentTarget() {
        return ActionIssued.getCurOrder(unit)
                .map(actionIssued -> actionIssued.targetTag)
                .orElse(null);
    }

    protected List<UnitInPool> getTargets() {
        return UnitUtils.getEnemyTargetsInRange(
                unit.unit(),
                target -> !UnitUtils.isStructure(target.unit().getType()) &&
                        (!WidowMine.isTargetted(target.getTag()) ||
                        !canBeOneShot(target))
        );
    }

    //TODO: choose center target for best splash?  choose target closest to 125hp+35shields for best 1shot?  looks at target values?
    protected UnitInPool getBestTarget() {
        List<UnitInPool> targets = getTargets();
        if (targets.isEmpty()) {
            return null;
        }
        Optional<UnitInPool> oneShotTarget = targets.stream()
                .filter(target -> canBeOneShot(target))
                .max(Comparator.comparing(target -> {
                    UnitTypeData targetData = Bot.OBS.getUnitTypeData(false).get(target.unit().getType());
                    return (targetData.getMineralCost().orElse(0) + 1.5 * targetData.getVespeneCost().orElse(0));
                }));
        if (oneShotTarget.isPresent()) {
            return oneShotTarget.get();
        }
        Point2d midPointOfTargets = Position.midPointUnitsMedian(UnitUtils.toUnitList(targets));
        return targets.stream()
                .min(Comparator.comparing(target -> UnitUtils.getDistance(target.unit(), midPointOfTargets)))
                .get();
    }

    public static float getShieldDamage(UnitInPool target) {
        return Math.max(35, target.unit().getShield().orElse(0f));
    }

    public static boolean canBeOneShot(UnitInPool target) {
        return UnitUtils.getTotalHealth(target.unit()) < 125f + getShieldDamage(target) &&
                UnitUtils.getTotalHealth(target.unit()) > 90f + getShieldDamage(target);
    }

    public static boolean hasFastBurrow() {
        return Bot.OBS.getUpgrades().contains(Upgrades.DRILL_CLAWS);
    }

    public static boolean hasPermaCloak() {
        return !UnitUtils.getMyUnitsOfType(Units.TERRAN_ARMORY).isEmpty();
    }

    public static boolean isTargetted(Tag targetTag) {
        return UnitMicroList.getUnitSubList(WidowMine.class).stream()
                .anyMatch(mine -> mine.isManuallyTargetted() &&
                        targetTag.equals(mine.lastManualTarget.getTag()));
    }
}
