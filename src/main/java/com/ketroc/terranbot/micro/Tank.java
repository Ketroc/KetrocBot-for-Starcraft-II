package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.List;
import java.util.function.Predicate;

public class Tank extends BasicUnitMicro {

    public Tank(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, true);
    }

    protected boolean siegeUpMicro() {
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 1 || !getTankTargets(13).isEmpty()) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
            return true;
        }
        return false;
    }

    protected void unsiegeMicro() {
        if (unit.unit().getWeaponCooldown().orElse(1f) == 0f &&
                UnitUtils.getDistance(unit.unit(), targetPos) > 1 &&
                getTankTargets(15).isEmpty()) {
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MORPH_UNSIEGE, false);
        }
    }

    protected List<UnitInPool> getTankTargets(float range) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                (UnitUtils.getUnitSpeed((Units)enemy.unit().getType()) == 0
                        ? UnitUtils.getDistance(enemy.unit(), targetPos) <= 13 + enemy.unit().getRadius()
                        : UnitUtils.getDistance(enemy.unit(), targetPos) <= range + enemy.unit().getRadius()) &&
                !enemy.unit().getFlying().orElse(true) &&
                !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                !enemy.unit().getHallucination().orElse(false) &&
                !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD) &&
                enemy.unit().getDisplayType() == DisplayType.VISIBLE);
    }
}
