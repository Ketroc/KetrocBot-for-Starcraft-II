package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.Switches;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.UnitUtils;

public class Thor extends BasicUnitMicro {
    public Thor(Unit unit) {
        super(unit, unit.getPosition().toPoint2d(), MicroPriority.DPS);
    }

    public Thor(UnitInPool unit) {
        super(unit, unit.unit().getPosition().toPoint2d(), MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        if (unit == null || !unit.isAlive()) {
            onDeath();
            return;
        }

        if (isMorphing()) {
            return;
        }

        //attack && morph
        if (UnitUtils.isWeaponAvailable(unit.unit()) && !UnitUtils.isDisabled(unit.unit())) { //can morph = not disabled
            UnitInPool enemyTarget = selectTarget(false);
            if (enemyTarget != null) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, enemyTarget.unit(), false);
                return;
            }
            else if (isSafe() && doToggleAirAttackMode()) {
                toggleModes();
                return;
            }
        }

        //basic unit micro
        setTargetPos();
        super.onStep();
    }

    @Override
    public void onArrival() {

    }

    protected boolean doToggleAirAttackMode() {
        return ((isHiImpactMode() && !shouldHiImpact())
                || (!isHiImpactMode() && shouldHiImpact()));
    }

    protected boolean shouldHiImpact() {
        return Switches.enemyHasTempests ||
                UnitUtils.getEnemyUnitsOfType(UnitUtils.MASSIVE_AIR_TYPES).size() > 0 ||
                UnitUtils.getEnemyUnitsOfType(UnitUtils.LIGHT_AIR_TYPES).size() == 0;
    }

    private void toggleModes() {
        Abilities morphAbility = (isHiImpactMode()) ? Abilities.MORPH_THOR_EXPLOSIVE_MODE : Abilities.MORPH_THOR_HIGH_IMPACT_MODE;
        ActionHelper.unitCommand(unit.unit(), morphAbility, false);
    }

    protected boolean isHiImpactMode() {
        return unit.unit().getType() == Units.TERRAN_THOR_AP;
    }

}
