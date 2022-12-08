package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.UnitUtils;

public class Banshee extends BasicUnitMicro {

    public boolean retreatForRepairs;

    public Banshee(Unit unit, MicroPriority priority) {
        super(unit, priority);
    }

    public Banshee(Unit unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    public Banshee(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }


    @Override
    protected boolean isSafe(Point2d p) {
        boolean cloakAvailable = canCloak() || (isCloaked() && unit.unit().getEnergy().orElse(0f) > 5);
        int threatValue = InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, p);

        if (threatValue > 0 && !cloakAvailable) {
            return false;
        }

        if (unit.unit().getBuffs().contains(Buffs.LOCK_ON)) {
            return false;
        }

        //avoid high damage areas even if cloaked
        if (threatValue >= 50) {
            return false;
        }

        //don't risk being in threat range while cloaked, if health is low
        boolean safe = threatValue <= 2;
        if (retreatForRepairs) {
            return safe;
        }

        //safe if no threat or undetected with cloak
        return safe || (cloakAvailable && !isDetected(p));
    }

    protected boolean isDetected(Point2d p) {
        return InfluenceMaps.getValue(InfluenceMaps.pointDetected, p) || UnitUtils.hasDecloakBuff(unit.unit());
    }

    public boolean canCloak() {
        float energyToCloak = (unit.unit().getHealth().get() > 24) ? 50 : 27;
        return UnitUtils.canCast(unit.unit()) &&
                Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
                unit.unit().getEnergy().orElse(0f) > energyToCloak;
    }

    protected boolean isCloaked() {
        return unit.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.NOT_CLOAKED;
    }

    //is safe if position is free from threat, or undetected with cloak available
    protected boolean shouldCloak() {
        Point2d bansheePos = unit.unit().getPosition().toPoint2d();
        if (!isCloaked() && canCloak() && !isDetected(bansheePos)) {
            //health:threat threshold or cyclone locked on
            return  InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, bansheePos) > unit.unit().getHealth().get()/30 ||
                    unit.unit().getBuffs().contains(Buffs.LOCK_ON);
        }
        return false;
    }
}
