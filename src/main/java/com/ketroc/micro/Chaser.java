package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;

public class Chaser extends BasicUnitMicro {
    UnitInPool targetUnit;

    public Chaser(Unit chaser, UnitInPool targetUnit) {
        super(chaser, targetUnit.unit().getPosition().toPoint2d(), MicroPriority.CHASER);
        this.targetUnit = targetUnit;
    }

    public Chaser(UnitInPool chaserUip, UnitInPool targetUnit) {
        super(chaserUip, targetUnit.unit().getPosition().toPoint2d(), MicroPriority.CHASER);
        this.targetUnit = targetUnit;
    }

    @Override
    public void onStep() {
        super.onStep();
    }

    @Override
    protected void updateTargetPos() {
        targetPos = targetUnit.unit().getPosition().toPoint2d();
    }

    @Override
    public void onArrival() { }
}
