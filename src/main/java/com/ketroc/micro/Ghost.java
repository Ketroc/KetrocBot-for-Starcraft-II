package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;
import com.ketroc.utils.*;

import java.util.Set;

//TODO: cloak to set up snipe/emp??
public class Ghost extends BasicUnitMicro {
    public static final Set<Units> SNIPE_EXCLUDE_UNITS = Set.of(
            Units.ZERG_ZERGLING, Units.ZERG_ZERGLING_BURROWED, Units.ZERG_DRONE,
            Units.ZERG_DRONE_BURROWED, Units.ZERG_CHANGELING_MARINE, Units.ZERG_CHANGELING_MARINE_SHIELD,
            Units.ZERG_BROODLING, Units.ZERG_LOCUS_TMP, Units.ZERG_LOCUS_TMP_FLYING, Units.ZERG_EGG,
            Units.ZERG_LARVA, Units.ZERG_OVERLORD_COCOON, Units.ZERG_TRANSPORT_OVERLORD_COCOON,
            Units.TERRAN_MARINE, Units.TERRAN_SCV
    );
    public static final Set<Units> TANKING_UNITS = Set.of(
            Units.TERRAN_HELLION_TANK, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_AUTO_TURRET,
            Units.TERRAN_BUNKER
    );
    public static final int EMP_RANGE = 10;
    public static long prevEmpFrame;
    public static long prevSnipeFrame;

    public Ghost(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
    }

    public Ghost(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
    }

    public static int totalGhostEnergy() {
        return (int)UnitMicroList.getUnitSubList(Ghost.class).stream()
                .mapToDouble(ghost -> ghost.unit.unit().getEnergy().orElse(0f))
                .sum();
    }

    @Override
    public void onStep() {
        super.onStep();
    }

    protected boolean isCasting() {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(action -> action.ability == Abilities.EFFECT_EMP ||
                        action.ability == Abilities.EFFECT_GHOST_SNIPE ||
                        action.ability == Abilities.EFFECT_NUKE_CALL_DOWN);
    }

    protected void cloak() {
        ActionHelper.unitCommand(unit.unit(), Abilities.BEHAVIOR_CLOAK_ON_GHOST, false);
    }

    protected void snipe(Unit target) {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_GHOST_SNIPE, target,false);
        Ghost.prevSnipeFrame = Time.nowFrames();
    }

    protected void emp(Point2d pos) {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_EMP, pos,false);
        Ghost.prevEmpFrame = Time.nowFrames();
    }

    protected void nuke(Point2d pos) {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_NUKE_CALL_DOWN, pos,false);
        Chat.tag("MY_NUKE");
    }

    protected boolean canSnipe() {
        //don't snipe if taking damage from storm, fungal, etc
        if (unit.unit().getBuffs().contains(Buffs.FUNGAL_GROWTH) || isInSplashDamage()) {
            return false;
        }

        //don't snipe if enemy is close
        Unit closestEnemyThreat = UnitUtils.getClosestEnemyThreat(unit.unit());
        boolean isEnemyThreatClose = closestEnemyThreat != null && UnitUtils.getDistance(unit.unit(), closestEnemyThreat) < 7;
        if (isEnemyThreatClose && (!isCloaked() || isDetected())) {
            return false;
        }

        //one snipe per frame as hack to handle multiple snipes of same target
        return Ghost.prevSnipeFrame + Launcher.STEP_SIZE < Time.nowFrames() && canCast(50);
    }

    protected boolean canCloak() {
        return Bot.OBS.getUpgrades().contains(Upgrades.PERSONAL_CLOAKING) && canCast(30);
    }

    protected boolean canEmp() { //TODO: handle incoming EMPs in mapping instead of this hardcoded delay
        return Ghost.prevEmpFrame + 24 <= Time.nowFrames() &&
                canCast(75);
    }

    protected boolean canNuke() {
        return MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_NUKE_CALL_DOWN);
    }

    private boolean canCast(int energyReq) {
        return unit.unit().getEnergy().orElse(0f) >= energyReq + (isCloaked() ? 1 : 0)  &&
                UnitUtils.canCast(unit.unit());
    }

    protected boolean isCloaked() {
        return unit.unit().getBuffs().contains(Buffs.GHOST_CLOAK) ||
                ActionIssued.getCurOrder(unit.unit()).stream()
                .anyMatch(action -> action.ability == Abilities.BEHAVIOR_CLOAK_ON_GHOST);
    }

    protected boolean isDetected() {
        return UnitUtils.isDetected(unit.unit());
    }

    public static int numSnipesInProgress(Unit target) {
        return (int)UnitMicroList.getUnitSubList(Ghost.class).stream()
                .filter(ghost -> ActionIssued.getCurOrder(ghost.unit).stream()
                        .anyMatch(order -> order.ability == Abilities.EFFECT_GHOST_SNIPE &&
                                target.getTag().equals(order.targetTag)))
                .count();
    }
}
