package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.DebugHelper;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/*
    This scv object will attack a target until it dies or flees the main&nat area
    Scv is replaced with a fresh scv when it's hp drops below 10
 */
public class ScvRepairer extends Scv {

    public UnitInPool targetUnit;

    public ScvRepairer(UnitInPool scv) {
        super(scv, ArmyManager.attackGroundPos, MicroPriority.DPS);
        Base.releaseScv(scv.unit());
    }

    public ScvRepairer(Unit scv) {
        this(Bot.OBS.getUnit(scv.getTag()));
    }

    @Override
    public void onStep() {
        //no scv or low hp scv
        if (!isAlive() || unit.unit().getHealth().orElse(0f) < 15) {
            onDeath();
            return;
        }

        DebugHelper.draw3dBox(unit.unit().getPosition().toPoint2d(), Color.GREEN, 0.5f);

        //set repair target
        selectTargetUnit();

        //move to target unit
        Optional<ActionIssued> curOrder = ActionIssued.getCurOrder(unit.unit());
        if (curOrder.isEmpty() ||
                (curOrder.get().ability == Abilities.MOVE && UnitUtils.getHealthPercentage(targetUnit.unit()) < 99) ||
                !curOrder.get().targetTag.equals(targetUnit.getTag())) {
            ActionHelper.unitCommand(unit.unit(), Abilities.SMART, targetUnit.unit(), false);
        }
    }

    private void selectTargetUnit() {
        //stay with current repair target
        if (targetUnit != null &&
                targetUnit.isAlive() &&
                UnitUtils.getHealthPercentage(targetUnit.unit()) < 99) {
            return;
        }

        //remove ScvRepairer if no tanks exist
        List<TankOffense> tankList = UnitMicroList.getUnitSubList(TankOffense.class);
        if (tankList.isEmpty()) {
            remove();
            return;
        }

        //set target to closest injured tank
        TankOffense repairTank = tankList.stream()
                .filter(tank -> UnitUtils.getHealthPercentage(tank.unit.unit()) < 99)
                .min(Comparator.comparing(tank -> UnitUtils.getDistance(unit.unit(), tank.unit.unit())))
                .orElse(null);
        if (repairTank != null) {
            targetUnit = repairTank.unit;
            return;
        }

        //if on defense and everything repaired, send scvs back to mining
        if (repairTank == null && !ArmyManager.doOffense) {
            remove();
            return;
        }

        //stay with same target if every tank is full hp
        if (targetUnit != null && targetUnit.isAlive()) {
            return;
        }

        //set target to closest tank if all tanks are full hp and no target is currently set
        if (repairTank == null && (targetUnit == null || !targetUnit.isAlive())) {
            repairTank = tankList.stream()
                    .min(Comparator.comparing(tank -> UnitUtils.getDistance(unit.unit(), tank.unit.unit())))
                    .get(); //empty tank list already handled, so this is safe
            targetUnit = repairTank.unit;
            return;
        }
    }

    @Override
    public void onArrival() {

    }

    public void remove() {
        removeMe = true;
        if (unit.isAlive()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.STOP, false);
        }
    }

    public static boolean contains(Tag targetTag) {
        return UnitMicroList.getUnitSubList(ScvRepairer.class).stream()
                .anyMatch(scvAttackTarget -> scvAttackTarget.targetUnit != null && scvAttackTarget.targetUnit.getTag().equals(targetTag));
    }

}