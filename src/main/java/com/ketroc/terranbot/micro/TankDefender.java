package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.function.Predicate;

public class TankDefender extends BasicUnitMicro {

    public TankDefender(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, true);
    }

    @Override
    public void onStep() {
        //no tank
        if (unit == null || !unit.isAlive()) {
            super.onStep();
            return;
        }

        Predicate<UnitInPool> enemiesInSiegeRange = u -> u.unit().getFlying().orElse(true) == false &&
                UnitUtils.getDistance(unit.unit(), u.unit()) < 13f;

        //unsiege
        if (unit.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
            if (Bot.OBS.getUnits(Alliance.ENEMY, enemiesInSiegeRange).isEmpty() && unit.unit().getWeaponCooldown().orElse(1f) == 0f) {
                Bot.ACTION.unitCommand(unit.unit(), Abilities.MORPH_UNSIEGE, false);
            }
            return;
        }

        //siege up
        if (!Bot.OBS.getUnits(Alliance.ENEMY, enemiesInSiegeRange).isEmpty()) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
            return;
        }

        //normal micro
        super.onStep();
    }

    @Override
    public void onArrival() {
        super.onArrival();
        Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false)
                .unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, true);
    }
}
