package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;

public class ScvAttacker extends BasicUnitMicro {

    public ScvAttacker(Unit unit) {
        super(unit, ArmyManager.attackGroundPos, MicroPriority.DPS);
    }

    public ScvAttacker(UnitInPool unit) {
        super(unit, ArmyManager.attackGroundPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        setTargetPos();
        super.onStep();
    }

    @Override
    public void onArrival() {

    }
}
