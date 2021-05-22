package com.ketroc;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

public class Switches {
    public static UnitInPool bansheeDiveTarget; //target to snipe for banshees (spore/turret/cannon)
    public static UnitInPool vikingDiveTarget; //target to snipe for viking (overseer/raven/observer)
    public static boolean isDivingTempests; // true is vikingDiveTarget is a tempest

    public static boolean tvtFastStart; //hardcoded start to get bunker up in time for reaper rush
    public static boolean finishHim; //after cycling his bases twice, start final mop up.

    public static boolean firstObserverSpotted;
    public static boolean firstTankSpotted;
    public static boolean scvRushComplete = true;
    public static boolean enemyCanProduceAir;
    public static boolean enemyHasCloakThreat; //enemy can produce cloaked/burrowed attack units
    public static boolean phoenixAreReal;

    public static boolean hotkey8; //begin planetary doom
    public static boolean includeTanks;
    public static boolean scoutScanComplete;
    public static int numScansToSave = 0;

    public static void onStep() {
        //observer check
        if (LocationConstants.opponentRace == Race.PROTOSS &&
                !firstObserverSpotted &&
                !UnitUtils.getEnemyUnitsOfTypes(UnitUtils.OBSERVER_TYPE).isEmpty()) {
            numScansToSave = 2;
            firstObserverSpotted = true;
        }

        //tank check
        if (LocationConstants.opponentRace == Race.TERRAN &&
                !firstTankSpotted &&
                !UnitUtils.getEnemyUnitsOfTypes(UnitUtils.SIEGE_TANK_TYPE).isEmpty()) {
            numScansToSave = 3;
            firstTankSpotted = true;
        }
    }
}
