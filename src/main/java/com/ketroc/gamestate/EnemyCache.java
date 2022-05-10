package com.ketroc.gamestate;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;

import java.util.HashSet;
import java.util.Set;

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

    public static void onUnitCreated(UnitInPool uip) {
        if (uip.unit().getAlliance() == Alliance.ENEMY) {
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
}
