package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.models.EnemyMappingUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EnemyUnitMemory {
    private static final Set<Units> MEMORY_UNIT_TYPES = new HashSet<>(Set.of(
            Units.TERRAN_SIEGE_TANK_SIEGED, Units.ZERG_LURKER_MP_BURROWED));

    public static List<UnitInPool> enemyUnitMemory = new ArrayList<>();

    public static void onStep() {
        updateEnemyUnitMemory();
        mapEnemyUnits();
        enemyUnitMemory.forEach(u -> DebugHelper.boxUnit(u.unit()));
    }

    private static void mapEnemyUnits() {
        enemyUnitMemory.forEach(enemy -> GameCache.enemyMappingList.add(new EnemyMappingUnit(enemy.unit())));
        if (Time.nowFrames() % Time.NUM_FRAMES_PER_MINUTE == 0) { //once a minute
            Print.print("enemy mapping list size: " + GameCache.enemyMappingList.size());
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

    public static List<UnitInPool> getAllOfType(Units unitType) {
        return enemyUnitMemory.stream()
                .filter(u -> u.unit().getType() == unitType)
                .collect(Collectors.toList());
    }
}
