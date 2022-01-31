package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.UnitUtils;

public class ScvMiner extends Scv {

    public UnitInPool targetNode;

    public ScvMiner(UnitInPool scv, UnitInPool targetNode) {
        super(scv, targetNode.unit().getPosition().toPoint2d(), MicroPriority.DPS);
        this.targetNode = targetNode;
    }

    public ScvMiner(Unit scv, UnitInPool targetNode) {
        super(scv, targetNode.unit().getPosition().toPoint2d(), MicroPriority.DPS);
        this.targetNode = targetNode;
    }

    @Override
    public void onStep() {
        //DebugHelper.draw3dBox(unit.unit().getPosition().toPoint2d(), Color.GREEN, 0.5f);

        //avoid splash / lib zones
        if (!isSafe()) {
            detour();
            return;
        }

        //mine node
        if (!UnitUtils.isCarryingResources(unit.unit())) {
            if (!targetNode.getTag().equals(UnitUtils.getTargetUnitTag(unit.unit()))) {
                ActionHelper.unitCommand(unit.unit(), Abilities.SMART, targetNode.unit(), false);
            }
        }
        else if (UnitUtils.getOrder(unit.unit()) != Abilities.HARVEST_RETURN) {
            ActionHelper.unitCommand(unit.unit(), Abilities.HARVEST_RETURN, false);
        }
    }

    @Override
    public void onArrival() {

    }

    @Override
    public boolean isSafe() { //TODO: handle when to avoid damage
        return true;
        //return InfluenceMaps.getValue(InfluenceMaps.pointPersistentDamageToGround, unit.unit().getPosition().toPoint2d());
    }
}
