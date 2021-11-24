package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.ActionHelper;

public class Raven extends BasicUnitMicro {
    public Raven(Unit unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    public Raven(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    protected void castMatrix(Unit target) {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_INTERFERENCE_MATRIX, target, false);
    }

    protected boolean canMatrix() {
        return unit.unit().getEnergy().orElse(0f) >= 75;
    }
}
