package com.ketroc.gamestate;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EnemyCache {
    public static Set<Enemy> enemyList = new HashSet<>();

    public static void onStepStart() {
        enemyList.removeIf(enemy -> !enemy.isAlive());
    }

    public static void onStep() {

    }

    public static void onStepEnd() {
        //save this step's unit object for next game loop
        enemyList.forEach(enemy -> enemy.setPrevStepUnit(enemy.getUip().unit()));
    }

    public static void onUnitEnteredVision(UnitInPool uip) {
        if (uip.unit().getAlliance() == Alliance.ENEMY &&
                uip.unit().getDisplayType() != DisplayType.SNAPSHOT) {
            add(uip);
        }
    }

    public static boolean contains(Tag unitTag) {
        return enemyList.stream().anyMatch(enemy -> enemy.is(unitTag));
    }

    public static void add(UnitInPool uip) {
        if (!contains(uip.getTag())) {
            enemyList.add(new Enemy(uip));
        }
    }

    public static void print() {
        Map<UnitType, List<Enemy>> enemiesByType = enemyList.stream()
                .collect(Collectors.groupingBy(enemy -> enemy.getUip().unit().getType()));
        enemiesByType.forEach((unitType, enemies) -> System.out.println(enemies.size() + ": " + unitType));
    }
}
