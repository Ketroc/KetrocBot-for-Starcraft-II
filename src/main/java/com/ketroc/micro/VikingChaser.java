package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.UnitUtils;

public class VikingChaser extends BasicUnitMicro {
    public UnitInPool target;

    public VikingChaser(Unit unit, UnitInPool target) {
        super(unit, target.unit().getPosition().toPoint2d(), MicroPriority.CHASER);
        this.target = target;
    }

    public VikingChaser(UnitInPool unit, UnitInPool target) {
        super(unit, target.unit().getPosition().toPoint2d(), MicroPriority.CHASER);
        this.target = target;
    }

    @Override
    public void onStep() {
        targetPos = UnitUtils.getPosLeadingUnit(unit.unit(), target.unit());
        super.onStep();
    }

    @Override
    protected boolean attack() {
        if (!super.attackTarget(target.unit())) { //attack targetUnit
            return super.attack(); //otherwise attack best target in range
        }
        return true;
    }
}
