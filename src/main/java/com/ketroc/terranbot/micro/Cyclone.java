package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.List;

public class Cyclone extends BasicUnitMicro {
    private UnitInPool lockTarget;

    public Cyclone(Unit unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    public Cyclone(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    public Cyclone(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
    }

    public void onStep() {

    }

    @Override
    public void onArrival() {

    }

    public boolean isLockedOn() {
        return unit.unit().getOrders().stream()
                .anyMatch(unitOrder -> unitOrder.getAbility() == Abilities.EFFECT_LOCK_ON);
    }
}
