package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UnitAttribute;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.models.Cost;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.Optional;

public class MedivacScvHealer extends BasicUnitMicro {

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

        targetUip = GameCache.allMyUnitsSet.stream()
                .filter(u -> u.unit().getHealth().orElse(0f) < u.unit().getHealthMax().orElse(0f) &&
                        Bot.OBS.getUnitTypeData(false).get(u.unit().getType()).getAttributes().contains(UnitAttribute.BIOLOGICAL))
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), unit.unit())))
                .orElse(null);
        targetPos = targetUip != null ? targetUip.unit().getPosition().toPoint2d() : LocationConstants.REPAIR_BAY;
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
        //boost out of danger
        if (canBoost() && InfluenceMaps.getValue(InfluenceMaps.pointDamageToAirValue, unit.unit().getPosition().toPoint2d()) >=
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
}
