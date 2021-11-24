package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UnitAttribute;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Cost;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MedivacScvHealer extends BasicUnitMicro {
    private static Set<Units> FRIENDLY_AA_TYPES = Set.of(Units.TERRAN_CYCLONE, Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_MISSILE_TURRET);
    private UnitInPool targetUip;

    public MedivacScvHealer(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
        doDetourAroundEnemy = true;
    }

    public MedivacScvHealer(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
        doDetourAroundEnemy = true;
    }

    @Override
    protected void updateTargetPos() {
        //getting repaired
        if (!Cost.isGasBroke() && !Cost.isMineralBroke() && shouldRepair()) {
            targetUip = null;
            targetPos = LocationConstants.REPAIR_BAY;
            return;
        }

        Set<Tag> otherMedivacTargets = getOtherMedivacTargets();
        targetUip = GameCache.allMyUnitsSet.stream()
                .filter(u -> u.unit().getHealth().orElse(0f) < u.unit().getHealthMax().orElse(0f) &&
                        Bot.OBS.getUnitTypeData(false).get(u.unit().getType()).getAttributes().contains(UnitAttribute.BIOLOGICAL) &&
                        !otherMedivacTargets.contains(u.getTag()))
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), unit.unit())))
                .orElse(null);
        if (targetUip != null) {
            targetPos = targetUip.unit().getPosition().toPoint2d();
        } else if (ArmyManager.attackGroundPos == null) {
            targetPos = LocationConstants.REPAIR_BAY;
        } else {
            Point2d forwardBioPos = GameCache.allMyUnitsSet.stream()
                    .filter(u -> Bot.OBS.getUnitTypeData(false).get(u.unit().getType()).getAttributes().contains(UnitAttribute.BIOLOGICAL))
                    .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), ArmyManager.attackGroundPos)))
                    .map(u -> u.unit().getPosition().toPoint2d())
                    .orElse(LocationConstants.REPAIR_BAY);
            if (forwardBioPos.distance(targetPos) > 3) {
                targetPos = forwardBioPos;
            }
        }
    }

    private boolean shouldRepair() {
        float healthPercentage = UnitUtils.getHealthPercentage(unit.unit());
        return healthPercentage < 20 ||
                (healthPercentage < 100 && UnitUtils.getDistance(unit.unit(), LocationConstants.REPAIR_BAY) < 3);
    }

    @Override
    public boolean[][] getThreatMap() {
        return InfluenceMaps.pointThreatToAirPlusBuffer;
    }

    @Override
    public void onStep() {
        if (!isAlive()) {
            onDeath();
            return;
        }

        updateTargetPos();
        if (isSafe()) {
            if (UnitUtils.getOrder(unit.unit()) == Abilities.EFFECT_HEAL) {
                return;
            }
            if (targetUip != null) {
                if (canHeal() && isInPosToHeal()) { //max range is 4 for healing
                    ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_HEAL, targetUip.unit(), false);
                }
                else { //travel to target
                    if (!isInPosToHeal() && !isMovingToTargetPos()) {
                        ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
                    }
                    if (canBoost() && UnitUtils.getDistance(unit.unit(), targetPos) > 15) {
                        castBoost();
                    }
                }
                return;
            }
        }

        //flee to closest aa if phoenix is on me
        Unit closestEnemyThreat = UnitUtils.getClosestEnemyThreat(unit.unit());
        if (closestEnemyThreat != null &&
                closestEnemyThreat.getType() == Units.PROTOSS_PHOENIX &&
                UnitUtils.getDistance(closestEnemyThreat, unit.unit()) < 5) {
            if (canBoost()) {
                castBoost();
            }
            Unit myClosestAA = UnitUtils.getClosestUnitOfType(Alliance.SELF, FRIENDLY_AA_TYPES, unit.unit().getPosition().toPoint2d());
            if (myClosestAA != null) {
                targetPos = myClosestAA.getPosition().toPoint2d();
                if (!isMovingToTargetPos()) {
                    ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
                }
                return;
            }
        }

        //boost out of danger
        else if (canBoost() && InfluenceMaps.getValue(InfluenceMaps.pointDamageToAirValue, unit.unit().getPosition().toPoint2d()) * 2 >=
                unit.unit().getHealth().orElse(0f)) {
            castBoost();
        }

        super.onStep();
    }

    @Override
    protected boolean isMovingToTargetPos() {
        Optional<ActionIssued> order = ActionIssued.getCurOrder(unit);
        return order.isPresent() &&
                order.get().targetPos != null &&
                order.get().targetPos.distance(targetPos) < 3.5f;
    }

    protected boolean isInPosToHeal() {
        return UnitUtils.getRange(unit.unit(), targetUip.unit()) < 4;
    }

    @Override
    public void onArrival() { }

    private void castBoost() {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_MEDIVAC_IGNITE_AFTERBURNERS, false);
    }

    private boolean canBoost() {
        return MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_MEDIVAC_IGNITE_AFTERBURNERS);
    }

    private boolean canHeal() {
        return unit.unit().getEnergy().orElse(0f) >= 5 && UnitUtils.canCast(unit.unit());
    }

    public Set<Tag> getOtherMedivacTargets() {
        return UnitMicroList.getUnitSubList(MedivacScvHealer.class).stream()
                .filter(medivac -> !medivac.equals(this))
                .filter(medivac -> medivac.targetUip != null)
                .map(medivac -> medivac.targetUip.getTag())
                .collect(Collectors.toSet());
    }

    public static boolean needAnother() {
        return numInjuredScvs() >= 6 && isAllMedivacsDry();
    }

    private static long numInjuredScvs() {
        return GameCache.allMyUnitsSet.stream()
                .filter(u -> u.unit().getType() == Units.TERRAN_SCV && u.unit().getHealth().orElse(0f) < 45f)
                .count();
    }

    private static boolean isAllMedivacsDry() {
        return UnitUtils.numInProductionOfType(Units.TERRAN_MEDIVAC) == 0 &&
                GameCache.allMyUnitsSet.stream().noneMatch(u -> u.unit().getType() == Units.TERRAN_MEDIVAC &&
                        u.unit().getEnergy().orElse(0f) > 10);
    }
}
