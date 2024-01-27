package com.ketroc;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.ketroc.gamestate.EnemyCache;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.UnitUtils;

public class Switches {
    public static UnitInPool bansheeDiveTarget; //target to snipe for banshees (spore/turret/cannon)
    public static UnitInPool vikingDiveTarget; //target to snipe for viking (overseer/raven/observer)
    public static boolean isDivingTempests; // true is vikingDiveTarget is a tempest
    public static boolean hasCastOCSpellThisFrame;

    public static boolean fastDepotBarracksOpener; //hardcoded start to get bunker up in time for reaper rush

    public static boolean scvRushComplete = true;
    public static boolean enemyCanProduceAir;
    public static boolean enemyHasTempests;
    public static boolean doNeedDetection; //enemy can produce cloaked/burrowed attack units
    public static boolean phoenixAreReal;

    public static boolean includeTanks;
    public static boolean scoutScanComplete = true; //TODO: testing - trying out no scout scan TvP
    public static int numScansToSave = 0;

    public static void onStep() {
        hasCastOCSpellThisFrame = false;

        if (numScansToSave == 2) {
            return;
        }

        //2 scans saved for observers, banshees, tank vs tank wars TODO: include all cloaked/burrowed units
        if (!UnitUtils.getEnemyUnitsOfType(UnitUtils.OBSERVER_TYPE).isEmpty() ||
                !UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BANSHEE).isEmpty() ||
                (Strategy.DO_OFFENSIVE_TANKS && !UnitUtils.getEnemyUnitsOfType(UnitUtils.SIEGE_TANK_TYPE).isEmpty()) ||
                ((Strategy.gamePlan == GamePlan.BC_RUSH || Strategy.gamePlan == GamePlan.BC_MACRO) && EnemyCache.enemyUpgrades.contains(Upgrades.BURROW))) {
            numScansToSave = 2;
        }
    }
}
