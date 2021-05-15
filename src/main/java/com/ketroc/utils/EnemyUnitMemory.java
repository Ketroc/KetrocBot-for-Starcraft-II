package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.models.EnemyUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnemyUnitMemory {
    private static final Set<Units> MEMORY_UNIT_TYPES = new HashSet<>(Set.of(
            Units.TERRAN_SIEGE_TANK_SIEGED, Units.ZERG_LURKER_MP_BURROWED));

    public static List<UnitInPool> enemyUnitMemory = new ArrayList<>();

    public static void onStep() {
        long startTime = System.currentTimeMillis();
        updateEnemyUnitMemory();
        mapEnemyUnits();
        if (Time.nowFrames() % 1344 == 0) { //once a minute
            System.out.println("Time to update enemy unit memory: " + (System.currentTimeMillis() - startTime));
        }
    }

    private static void mapEnemyUnits() {
        enemyUnitMemory.forEach(enemy -> GameCache.enemyMappingList.add(new EnemyUnit(enemy.unit())));
        if (Time.nowFrames() % 1344 == 0) { //once a minute
            System.out.println("enemy mapping list size: " + GameCache.enemyMappingList.size());
        }
    }

    private static void updateEnemyUnitMemory() {
        //save tanks/lurkers that I just lost vision of
        GameCache.allEnemiesList.stream()
                .filter(u -> MEMORY_UNIT_TYPES.contains(u.unit().getType()) &&
                        UnitUtils.justLostVisionOf(u))
                .forEach(u -> {
                    if (enemyUnitMemory.stream().anyMatch(enemy -> enemy.getTag().equals(u.getTag()))) {
                        //TODO: delete - for testing
                        System.out.println("duplicate memory unit added.");
                    }
                    enemyUnitMemory.add(u);
                });

        //remove if visible again
        enemyUnitMemory.removeIf(u -> u.getLastSeenGameLoop() == Time.nowFrames());

        //remove if it's not at its last known position
        enemyUnitMemory.removeIf(u -> {
            Point2d lastKnownPos = u.unit().getPosition().toPoint2d();
            return Bot.OBS.getVisibility(lastKnownPos) == Visibility.VISIBLE &&
                    (u.unit().getType() != Units.ZERG_LURKER_MP_BURROWED || UnitUtils.isInMyDetection(lastKnownPos));
        });
    }
}
