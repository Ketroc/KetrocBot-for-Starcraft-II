package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MyUnitAbilities {
    public static Map<Tag, Set<Abilities>> map = new HashMap<>();

    public static void onStep() {
        List<Unit> myUnits = Bot.OBS.getUnits(Alliance.SELF).stream().map(UnitInPool::unit).collect(Collectors.toList());
        Bot.QUERY.getAbilitiesForUnits(myUnits, false)
                .forEach(unitAbils -> map.put(
                        unitAbils.getUnitTag(),
                        unitAbils.getAbilities().stream()
                                .map(availAbility -> (Abilities)availAbility.getAbility())
                                .collect(Collectors.toSet())
                ));
    }

    public static boolean isAbilityAvailable(Unit myUnit, Abilities ability) {
        return map.get(myUnit.getTag()).contains(ability);
    }
}
