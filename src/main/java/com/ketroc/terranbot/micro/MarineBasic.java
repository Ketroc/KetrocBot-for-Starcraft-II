package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.strategies.defenses.CannonRushDefense;

public class MarineBasic extends Marine {
    public MarineBasic(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public MarineBasic(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        priority = (CannonRushDefense.cannonRushStep == 2) ? MicroPriority.SURVIVAL : MicroPriority.DPS;
        //TODO: move target selection here??
        super.onStep();
    }
}
