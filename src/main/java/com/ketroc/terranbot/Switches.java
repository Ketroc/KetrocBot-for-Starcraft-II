package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

public class Switches {
    public static UnitInPool bansheeDiveTarget; //target to snipe for banshees (spore/turret/cannon)
    public static UnitInPool vikingDiveTarget; //target to snipe for viking (overseer/raven/observer)
    public static boolean isDivingTempests; // true is vikingDiveTarget is a tempest

    public static boolean tvtFastStart; //hardcoded start to get bunker up in time for reaper rush
    public static boolean finishHim; //after cycling his bases twice, start final mop up.

    public static boolean isExpectingEnemyBCs; //true if BC has blinked into my main
    public static boolean firstObserverSpotted;
    public static boolean scvRushComplete = true;
    public static boolean enemyCanProduceAir;
    public static boolean enemyHasCloakThreat; //enemy can produce cloaked/burrowed attack units
    public static boolean phoenixAreReal;

    public static boolean hotkey8; //begin planetary doom
    public static boolean includeTanks;
    public static boolean scoutScanComplete;
    public static boolean doBuildMainBaseTurrets = true;

    public static void onStep() {
        //BC Rush Defense - add 3rd turret at main base
        if (!isExpectingEnemyBCs && Time.nowFrames() < Time.toFrames("8:00") && LocationConstants.opponentRace == Race.TERRAN &&
                (!UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BATTLECRUISER).isEmpty() || !UnitUtils.getEnemyUnitsOfType(Units.TERRAN_FUSION_CORE).isEmpty())) {
            KetrocBot.purchaseQueue.addFirst(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0).getTag())));
            if (!Strategy.NO_TURRETS) {
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(2)));
            }
            isExpectingEnemyBCs = true;
        }

        //observer check
        if (LocationConstants.opponentRace == Race.PROTOSS) {
            if (!firstObserverSpotted) {
                if (!UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_OBSERVER).isEmpty()) {
                    Strategy.energyToMuleAt = 100;
                    firstObserverSpotted = true;
                }
            }
        }

    }

}
