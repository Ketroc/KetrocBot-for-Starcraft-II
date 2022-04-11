package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.UnitUtils;

public class Marauder extends BasicUnitMicro {
    public Marauder(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public Marauder(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        setTargetPos();
        setMicroPriority();
        super.onStep();
    }

    @Override
    public void onArrival() {

    }

    //keep low health marauders safe
    private void setMicroPriority() { //TODO: remove when handled in basic unit micro
        //stay back when low in health
        if (UnitUtils.getHealthPercentage(unit.unit()) < 20) {
            priority = MicroPriority.SURVIVAL;
            return;
        }

        //stay back during first third of weapon cooldown
        if (unit.unit().getWeaponCooldown().orElse(1f) > 14f) {
            priority = MicroPriority.SURVIVAL;
            return;
        }

        //otherwise be aggressive
        priority = MicroPriority.DPS;
    }

    @Override
    protected void setTargetPos() {
        //"attack" enemy air units too, so that the marauder stick with the ghosts
        if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
            targetPos = ArmyManager.attackEitherPos;
            return;
        }
        super.setTargetPos();
    }
}
