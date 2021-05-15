package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.strategies.defenses.CannonRushDefense;

public class MarineBasic extends Marine {
    public MarineBasic(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public MarineBasic(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        updateMicroPriority();
        //TODO: move target selection here??
        super.onStep();
    }

    private void updateMicroPriority() {
        if (CannonRushDefense.cannonRushStep == 2) {
            priority = MicroPriority.SURVIVAL;
            return;
        }
        priority = MicroPriority.DPS;
    }
}
