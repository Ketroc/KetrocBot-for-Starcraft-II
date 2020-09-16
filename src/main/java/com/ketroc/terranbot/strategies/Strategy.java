package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Switches;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.purchases.PurchaseStructure;

import java.util.Comparator;
import java.util.List;

public class Strategy {
    public static final boolean REAL_TIME = Bot.isRealTime;
    public static final int SKIP_FRAMES = (REAL_TIME) ? 6 : 2;
    public static final boolean ANTI_DROP_TURRET = false; //TODO: temporary for ANIbot
    public static boolean ANTI_NYDUS_BUILD; //TODO: temporary for Spiny

    public static boolean DO_INCLUDE_TANKS;
    public static final int NUM_TANKS_PER_EXPANSION = 2; //only works for 2 atm
    public static final int MAX_TANKS = 12;

    public static boolean DO_INCLUDE_LIBS;
    public static final int NUM_LIBS_PER_EXPANSION = 2; //only works for 2 atm
    public static final int MAX_LIBS = 12;

    public static final int FUNGAL_FRAMES = 16; //# of frames for fungal to land after being cast
    public static float VIKING_BANSHEE_RATIO = 0.2f;
    public static final int MAX_VIKINGS_TO_DIVE_TEMPESTS = 20; //always dive tempests if we reach this number
    public static final float DISTANCE_RAISE_DEPOT = 9;
    public static final int MIN_STRUCTURE_HEALTH = 40; //TODO: repair to this % to prevent burn
    public static int maxScvs = 80;
    public static final float KITING_BUFFER = 2.75f;
    public static final int RETREAT_HEALTH = 40; //% health of mech unit to go home to get repaired
    public static boolean enemyHasAirThreat;
    public static final int NUM_DONT_EXPAND = 2; //number of bases to never try expanding to
    public static final float ENERGY_BEFORE_CLOAKING = 80f; //don't cloak banshee if their energy is under this value
    public static final int NUM_SCVS_REPAIR_STATION = 7;
    public static final float BANSHEE_RANGE = 6.1f; //range in which banshee will be given the command to attack
    public static final float VIKING_RANGE = 9.1f; //range in which viking will be given the command to attack
    public static final int MIN_GAS_FOR_REFINERY = 1; //only build a refinery on this vespian node if it has at least this much gas
    public static int DIVE_RANGE = 12;
    public static final int TEMPEST_DIVE_RANGE = 23;
    public static final float RAVEN_DISTANCING_BUFFER = 2f;
    public static final int CAST_SEEKER_RANGE = 15;
    public static int energyToMuleAt = 50;
    public static final float SEEKER_RADIUS = 3;
    public static final float MIN_SUPPLY_TO_SEEKER = 22;
    public static boolean techBuilt;
    public static final int MAP_ENEMIES_IN_FOG_DURATION = 112; //number of game frames to map the threat from enemies that entered the fog of war (5seconds)
    public static boolean diveRavensVsVikings; //won't dive enemy ravens if opponent has 6+ vikings



    public static int step_TvtFastStart = 1;
    public static UnitInPool scv_TvtFastStart;
    public static int floatBaseAt = 50; //heath% to float base away at

    public static void onGameStart() {
        if (ANTI_NYDUS_BUILD) {
            antiNydusBuild();
        }
        BunkerContain.proxyBunkerLevel = 0;
        //set bunker contain
//        switch (LocationConstants.opponentRace) {
//            case TERRAN:
//                BunkerContain.proxyBunkerLevel = (Math.random()*10 < 2) ? 2 : 0; //1 in 5
//                break;
//            case PROTOSS:
//                double random =  Math.random()*10;
//                if (random < 2) {
//                    BunkerContain.proxyBunkerLevel = 1;
//                }
//                else if (random < 4) {
//                    BunkerContain.proxyBunkerLevel = 2;
//                }
//                else {
//                    BunkerContain.proxyBunkerLevel = 0;
//                }
//                break;
//            case ZERG:
//                BunkerContain.proxyBunkerLevel = 0;
//                break;
//        }
    }

    public static void onStep() {

    }

//    public static int getMaxScvs() {
//        int idealScvs = 0;
//        for (Base base : GameState.baseList) {
//            idealScvs += base.getCc().getIdealHarvesters().get(); //CCs in production return 0
//            for (Gas gas : base.getGases()) {
//                UnitInPool refinery = gas.getRefinery();
//                if (refinery != null) {
//                    idealScvs += 3;
//                }
//            }
//        }
//        idealScvs += 10;
//        return Math.min(maxScvs, Math.max(idealScvs, 44));
//    }

    public static void onStep_TvtFaststart() {
        switch (step_TvtFastStart) {
            case 1:
                //rally it to the depot1
                Bot.ACTION.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, LocationConstants.extraDepots.get(0), false);
                step_TvtFastStart++;
                break;
            case 2:
                //rally back to mineral node
                if (Bot.OBS.getFoodWorkers() == 13) {
                    Bot.ACTION.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, GameCache.baseList.get(0).getMineralPatches().get(0), false);
                    step_TvtFastStart++;
                }
                break;
            case 3:
                //queue up depot and rax with first scv
                if (GameCache.mineralBank >= 100) {
                    List<UnitInPool> scvNearDepot = WorkerManager.getAllScvs(LocationConstants.extraDepots.get(0), 6);
                    if (!scvNearDepot.isEmpty()) {
                        scv_TvtFastStart = scvNearDepot.get(0); //TODO: null check
                        Bot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
                        Bot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_SUPPLY_DEPOT));
                    }
                    else {
                        Bot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
                        Bot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    }
                    Switches.tvtFastStart = false;
                }
                break;
        }
    }

    public static void setMaxScvs() {
        //if no minerals left on the map
        if (GameCache.defaultRallyNode == null) {
            maxScvs = 5;
        }
        //if maxed out on macro OCs
        else if (LocationConstants.MACRO_OCS.isEmpty() && GameCache.mineralBank > 3000) {
            maxScvs = 50;
        }
//        else {
//            maxScvs = 80;
//        }
    }

    public static void antiNydusBuild() {
        //rax after depot, 2nd depot after cc since cc is late, earlier 2nd gas
        Bot.purchaseQueue.add(1, Bot.purchaseQueue.remove(3));
        Bot.purchaseQueue.add(4, new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.extraDepots.remove(LocationConstants.extraDepots.size()-1)));
        Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));

        //get closest STARPORTS position
        Point2d closestStarportPos = LocationConstants.STARPORTS.stream()
                .min(Comparator.comparing(starportPos -> starportPos.distance(LocationConstants.MID_WALL_3x3)))
                .get();

        //build rax at closest position
        ((PurchaseStructure)Bot.purchaseQueue.get(1)).setPosition(closestStarportPos);

        //save MID_WALL_3X3 for barracks' later position
        LocationConstants._3x3Structures.remove(0);
    }
}
