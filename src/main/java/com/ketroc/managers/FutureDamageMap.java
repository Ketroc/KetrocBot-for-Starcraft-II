package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.models.FutureDamage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FutureDamageMap {
    private static List<FutureDamage> list = new ArrayList<>();
    public static Map<Tag, Integer> map = new HashMap<>();

    public static void onStep() {
        //remove old values
        list.removeIf(futureDamage -> futureDamage.doRemove());

        //create enemy damage map
        map = list.stream().collect(Collectors.groupingBy(
                futureDamage -> futureDamage.enemyUip.getTag(),
                Collectors.summingInt(futureDamage -> futureDamage.calculateDamage())
        ));
    }

    public static void addToMap(UnitInPool myUip, UnitInPool enemyUip, Abilities ability) {
        list.add(new FutureDamage(myUip, enemyUip, ability));
    }

    public static int get(Tag enemyTag) {
        return map.getOrDefault(enemyTag, 0);
    }
}
