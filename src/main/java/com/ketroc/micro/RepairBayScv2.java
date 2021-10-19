package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.Set;

//new way of assigning repair bay to any base
public class RepairBayScv2 extends BasicUnitMicro {
    public Point2d repairBayPos;

    public RepairBayScv2(UnitInPool scv, Point2d repairBayPos) {
        super(scv, repairBayPos, MicroPriority.SURVIVAL);
        this.repairBayPos = repairBayPos;
        Base.releaseScv(scv.unit());
    }

    @Override
    public void onStep() {
        Set<Unit> repairTargets = UnitUtils.getRepairBayTargets(repairBayPos);
        if (repairTargets.isEmpty()) {
            removeMe = true;
            UnitUtils.returnAndStopScv(unit);
        }
        else if (UnitUtils.getOrder(unit.unit()) != Abilities.EFFECT_REPAIR) {
            repairTargets.stream()
                    .min(Comparator.comparing(repairTarget -> UnitUtils.getDistance(unit.unit(), repairTarget)))
                    .ifPresent(repairTarget -> ActionHelper.unitCommand(
                            unit.unit(), Abilities.EFFECT_REPAIR_SCV, repairTarget, false));
        }
    }

    @Override
    public void onArrival() {  }
}
