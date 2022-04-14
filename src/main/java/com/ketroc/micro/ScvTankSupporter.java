package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/*
    Scv for repairing TankOffense units and other ScvRepairer units
 */
public class ScvTankSupporter extends Scv { //TODO: include thors

    public UnitInPool targetUnit;

    public ScvTankSupporter(UnitInPool scv) {
        super(scv, ArmyManager.attackGroundPos, MicroPriority.DPS);
        Base.releaseScv(scv.unit());
    }

    public ScvTankSupporter(Unit scv) {
        this(Bot.OBS.getUnit(scv.getTag()));
    }

    @Override
    public void onStep() {
        //scv died
        if (!isAlive()) {
            onDeath();
            return;
        }

        //DebugHelper.draw3dBox(unit.unit().getPosition().toPoint2d(), Color.GREEN, 0.5f);

        //set repair target
        selectTargetUnit();

        //move to target unit
        Optional<ActionIssued> curOrder = ActionIssued.getCurOrder(unit);
        if (curOrder.isEmpty() ||
                (curOrder.get().ability == Abilities.MOVE && UnitUtils.getHealthPercentage(targetUnit.unit()) < 99) ||
                !targetUnit.getTag().equals(curOrder.get().targetTag)) {
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
        if (!ArmyManager.doOffense) {
            remove();
            return;
        }

        //if every tank is repaired, repair other ScvRepairers
        List<UnitInPool> injuredRepairScvs = UnitMicroList.getUnitSubList(ScvTankSupporter.class)
                .stream()
                .filter(scvRepairer -> !scvRepairer.equals(this) &&
                        UnitUtils.getHealthPercentage(scvRepairer.unit.unit()) < 100)
                .map(scvRepairer -> scvRepairer.unit)
                .collect(Collectors.toList());
        if (!injuredRepairScvs.isEmpty()) {
            targetUnit = injuredRepairScvs.stream()
                    .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), unit.unit())))
                    .get();
            return;
        }

        //set target to closest tank if all tanks are full hp and no tank target is currently set
        if (targetUnit == null ||
                !targetUnit.isAlive() ||
                !UnitUtils.SIEGE_TANK_TYPE.contains(targetUnit.unit().getType())) {
            targetUnit = tankList.stream()
                    .min(Comparator.comparing(tank -> UnitUtils.getDistance(unit.unit(), tank.unit.unit())))
                    .get()
                    .unit;
            return;
        }
    }

    @Override
    public void onArrival() {

    }

    public void remove() {
        removeMe = true;
        if (unit.isAlive()) {
            UnitUtils.returnAndStopScv(unit);
        }
    }

    public static boolean contains(Tag targetTag) {
        return UnitMicroList.getUnitSubList(ScvTankSupporter.class).stream()
                .anyMatch(scvAttackTarget -> scvAttackTarget.targetUnit != null && scvAttackTarget.targetUnit.getTag().equals(targetTag));
    }

}
