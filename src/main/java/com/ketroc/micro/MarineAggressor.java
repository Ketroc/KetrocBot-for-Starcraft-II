package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.strategies.defenses.CannonRushDefense;

import java.util.ArrayList;
import java.util.List;

public class MarineAggressor extends Marine {
    private List<Units> unitKitingPriority = new ArrayList<>(List.of(
            Units.ZERG_ZERGLING, Units.ZERG_ROACH, Units.ZERG_DRONE, Units.ZERG_QUEEN, Units.ZERG_BROODLING
    ));
    private List<Units> unitCleanUp = new ArrayList<>(List.of(
            Units.ZERG_ROACH_WARREN, Units.ZERG_SPAWNING_POOL
    ));

    public MarineAggressor(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public MarineAggressor(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        priority = (CannonRushDefense.cannonRushStep == 2) ? MicroPriority.SURVIVAL : MicroPriority.DPS;
        //TODO: move target selection here??
        super.onStep();

        /*
        defense mode:
        hang on on main base high ground only.  default to insideMainWall

        offense mode:
        focus on getting to natural mineral line, then ramp, then main base mineral line
        go through list of priority targets



         */
    }

    public Unit getTarget() {
//        if (UnitUtils.isWeaponAvailable(unit.unit())) {
//            List<UnitInPool> priorityEnemiesWithin5Range = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> unitKitingPriority.contains(enemy.unit().getType()) &&
//                    UnitUtils.getDistance(enemy.unit(), unit.unit()) <= 5);
//            if (!priorityEnemiesWithin5Range.isEmpty()) {
//
//            }
//        List<UnitInPool> priorityEnemiesWithin11Range = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> unitKitingPriority.contains(enemy.unit().getType()) &&
//                UnitUtils.getDistance(enemy.unit(), unit.unit()) <= 11);
//        List<UnitInPool> cleanUpEnemiesWithin5Range = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> unitCleanUp.contains(enemy.unit().getType()) &&
//                UnitUtils.getDistance(enemy.unit(), unit.unit()) <= 5);
//        List<UnitInPool> cleanUpEnemiesWithin11Range = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> unitCleanUp.contains(enemy.unit().getType()) &&
//                UnitUtils.getDistance(enemy.unit(), unit.unit()) <= 11);
//        List<UnitInPool> anyEnemiesWithin5Range = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
//                UnitUtils.getDistance(enemy.unit(), unit.unit()) <= 5);
        return null;
    }
}
