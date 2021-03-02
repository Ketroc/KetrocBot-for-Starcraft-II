package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.DebugHelper;
import com.ketroc.terranbot.utils.UnitUtils;

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
