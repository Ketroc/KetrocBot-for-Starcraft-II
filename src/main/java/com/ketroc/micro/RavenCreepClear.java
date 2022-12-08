package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.UnitUtils;

public class RavenCreepClear extends Raven {
    public RavenCreepClear(Unit raven) {
        super(raven, raven.getPosition().toPoint2d(), MicroPriority.SURVIVAL);
    }
    UnitInPool banshee;

    @Override
    protected void setTargetPos() {
        targetPos = banshee.unit().getPosition().toPoint2d();
    }

    @Override
    public void onStep() {
        //set the banshee
        banshee = UnitMicroList.getUnitSubList(BansheeCreepClear.class).stream()
                .findFirst()
                .map(banshee -> banshee.unit)
                .orElse(null);

        //remove itself if banshee doesn't exist
        if (banshee == null || !banshee.isAlive()) {
            removeMe = true;
            return;
        }

        if (UnitUtils.getHealthPercentage(unit.unit()) <= Strategy.RETREAT_HEALTH + 10) {
            removeMe = true;
            return;
        }

        setTargetPos(); //keep behind banshee
        super.onStep(); //keep raven moving and safe
    }

    @Override
    protected Point2d findDetourPos() {
        return findDetourPos(4f);
    }

    @Override
    public void onArrival() {

    }
}
