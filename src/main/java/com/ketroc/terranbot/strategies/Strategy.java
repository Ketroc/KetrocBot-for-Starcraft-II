package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.managers.BuildManager;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.Switches;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.UpgradeManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.DelayedChat;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.utils.UnitUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Strategy {
    public static int selectedStrategy;

    public static int SKIP_FRAMES;
    public static final boolean ANTI_DROP_TURRET = false; //TODO: temporary for ANIbot
    public static boolean ANTI_NYDUS_BUILD; //TODO: temporary for Spiny

    public static boolean DO_INCLUDE_TANKS;
    public static final int NUM_TANKS_PER_EXPANSION = 2; //only works for 2 atm
    public static final int MAX_TANKS = 10;

    public static boolean DO_INCLUDE_LIBS;
    public static final int NUM_LIBS_PER_EXPANSION = 2; //only works for 2 atm
    public static final int MAX_LIBS = 10;

    public static final int FUNGAL_FRAMES = 16; //# of frames for fungal to land after being cast
    public static float VIKING_BANSHEE_RATIO = 0.2f;
    public static final int MAX_VIKINGS_TO_DIVE_TEMPESTS = 20; //always dive tempests if we reach this number
    public static final float DISTANCE_RAISE_DEPOT = 9;
    public static final int MIN_STRUCTURE_HEALTH = 40; //TODO: repair to this % to prevent burn
    public static int maxScvs = 90;
    public static final float KITING_BUFFER = 2.5f;
    public static int RETREAT_HEALTH = 40; //% health of mech unit to go home to get repaired
    public static boolean enemyHasAirThreat;
    public static final int NUM_DONT_EXPAND = 2; //number of bases to never try expanding to
    public static final float ENERGY_BEFORE_CLOAKING = 80f; //don't cloak banshee if their energy is under this value
    public static final int NUM_SCVS_REPAIR_STATION = 7;
    public static final float BANSHEE_RANGE = 6.1f; //range in which banshee will be given the command to attack
    public static final float AUTOTURRET_RANGE = 7f;
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

    public static boolean MASS_RAVENS;
    public static boolean DO_BANSHEE_HARASS = true;
    public static boolean PRIORITIZE_EXPANDING;
    public static boolean BUILD_EXPANDS_IN_MAIN;
    public static boolean EXPAND_SLOWLY;
    public static boolean DO_SEEKER_MISSILE;
    public static boolean DO_ANTIDROP_TURRETS;
    public static int AUTOTURRET_AT_ENERGY = 150;
    public static Abilities DEFAULT_STARPORT_UNIT = Abilities.TRAIN_BANSHEE;




    public static int step_TvtFastStart = 1;
    public static UnitInPool scv_TvtFastStart;
    public static int floatBaseAt = 50; //heath% to float base away at

    public static void onGameStart() {
        SKIP_FRAMES = (KetrocBot.isRealTime) ? 6 : 2;
        getGameStrategyChoice();
//
//        if (ANTI_NYDUS_BUILD) {
//            antiNydusBuild();
//        }
//        setSproutchStrategy();
    }

    private static void getGameStrategyChoice() {
        setStrategyNumber();
        switch (LocationConstants.opponentRace) {
            case TERRAN:
                chooseTvTStrategy();
                break;
            case PROTOSS:
                chooseTvPStrategy();
                break;
            case ZERG:
                chooseTvZStrategy();
                break;
        }

    }

    private static void chooseTvTStrategy() {
        int numStrategies = 4;
        selectedStrategy = 2;//selectedStrategy % numStrategies;

        switch (selectedStrategy) {
            case 0:
                DelayedChat.add("Standard Strategy");
                break;
            case 1:
                DelayedChat.add("Bunker Contain Strategy");
                BunkerContain.proxyBunkerLevel = 2;
                break;
            case 2:
                DelayedChat.add("Mass Raven Strategy");
                massRavenStrategy();
                break;
            case 3:
                DelayedChat.add("SCV Rush Strategy");
                Switches.scvRushComplete = false;
                break;
        }
    }

    private static void chooseTvPStrategy() {
        int numStrategies = 4;
        selectedStrategy = 2;//selectedStrategy % numStrategies;
        switch (selectedStrategy) {
            case 0:
                DelayedChat.add("Standard Strategy");
                break;
            case 1:
                DelayedChat.add("Bunker Contain Strategy");
                BunkerContain.proxyBunkerLevel = 1;
                break;
            case 2:
                DelayedChat.add("Mass Raven Strategy");
                massRavenStrategy();
                break;
            case 3:
                DelayedChat.add("SCV Rush Strategy");
                Switches.scvRushComplete = false;
                break;
        }
    }

    private static void chooseTvZStrategy() {
        int numStrategies = 3;
        selectedStrategy = 1;//selectedStrategy % numStrategies;

        switch (selectedStrategy) {
            case 0:
                DelayedChat.add("Standard Strategy");
                break;
            case 1:
                DelayedChat.add("Mass Raven Strategy");
                massRavenStrategy();
                break;
            case 2:
                DelayedChat.add("SCV Rush Strategy");
                Switches.scvRushComplete = false;
                break;
        }
    }
    //TODO: delete
    private static void setSproutchStrategy() {
        try {
            String[] fileText = Files.readString(Paths.get("./data/prevResult.txt")).split("~");
            String lastOpponentId = fileText[0];
            int lastOpponentStrategy = Integer.valueOf(fileText[1]);
            String lastOpponentResult = fileText[2];
            //game 1
            if (!lastOpponentId.equals(KetrocBot.opponentId) || LocationConstants.opponentRace == Race.RANDOM) {
                System.out.println("Game 1 - banshees");
                selectedStrategy = 0;
            }
            //game 2
            else if (lastOpponentStrategy == 0) {
                System.out.println("Game 2 - ravens");
                selectedStrategy = 1;
            }
            //game 3
            else {
                System.out.println("Game 3");
                selectedStrategy = (lastOpponentResult.equals("L")) ? 0 : 1;
            }

            if (selectedStrategy == 0) {
                DelayedChat.add("Mass Banshee Strategy");
            }
            else {
                DelayedChat.add("Mass Raven Strategy");
                massRavenStrategy();
            }
        }
        catch (Exception e) {
            selectedStrategy = 1;
            e.printStackTrace();
        }
        Switches.enemyCanProduceAir = true;
        DO_INCLUDE_TANKS = false;
        DO_INCLUDE_LIBS = false;
        DO_BANSHEE_HARASS = false;
        BUILD_EXPANDS_IN_MAIN = true;
        EXPAND_SLOWLY = true;
    }

    private static int getStrategyByOpponentId() {
        if (KetrocBot.opponentId == null) {
            return -1;
        }
        switch (KetrocBot.opponentId) {
//            case "d7bd5012-d526-4b0a-b63a-f8314115f101": //ANIbot
//            case "76cc9871-f9fb-4fc7-9165-d5b748f2734a": //dantheman_3
//                DO_ANTIDROP_TURRETS = true;
//                return 0;
//            case "9bcd0618-172f-4c70-8851-3807850b45a0": //snowbot
//                return 1;
//            case "b4d7dc43-3237-446f-bed1-bceae0868e89": //ThreeWayLover
//            case "7b8f5f78-6ca2-4079-b7c0-c7a3b06036c6": //Blinkerbot
//            case "9bd53605-334c-4f1c-95a8-4a735aae1f2d": //MadAI
//            case "ba7782ea-4dde-4a25-9953-6d5587a6bdcd": //AdditionalPylons
//                return 1;
//            case "16ab8b85-cf8b-4872-bd8d-ebddacb944a5": //sharpy_PVP_EZ
//                Switches.enemyCanProduceAir = true;
//                return 1;
//            case "c8ed3d8b-3607-40e3-b7fe-075d9c08a5fd": //QueenBot
//                DO_INCLUDE_TANKS = false;
//                DO_INCLUDE_LIBS = false;
//                return 0;
//            case "1574858b-d54f-47a4-b06a-0a6431a61ce9": //sproutch
//                Switches.enemyCanProduceAir = true;
//                DO_INCLUDE_TANKS = false;
//                DO_INCLUDE_LIBS = false;
//                DO_BANSHEE_HARASS = false;
//                BUILD_EXPANDS_IN_MAIN = true;
//                EXPAND_SLOWLY = true;
//                return 0;
            case "3c78e739-5bc8-4b8b-b760-6dca0a88b33b": //Fidolina
                DO_INCLUDE_TANKS = false;
                DO_BANSHEE_HARASS = false;
                EXPAND_SLOWLY = true;
                return 0;
//            case "12c39b76-7830-4c1f-9faa-37c68183396b": //WorthlessBot
//                BUILD_EXPANDS_IN_MAIN = true;
//                EXPAND_SLOWLY = true;
//                return 1;
//            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
//                BUILD_EXPANDS_IN_MAIN = true;
//                return 1;
//            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
//                BUILD_EXPANDS_IN_MAIN = true;
//                return 0;
            default:
                return -1;
        }
    }

    private static void massRavenStrategy() {
        MASS_RAVENS = true;
        UpgradeManager.starportUpgradeList = new ArrayList<>(List.of(Upgrades.RAVEN_CORVID_REACTOR));

        //2 +1 banshees kill a creep tumor so adjust banshee count and upgrades to this
        BuildManager.MIN_BANSHEES = 2;
        UpgradeManager.shipArmor.addAll(UpgradeManager.shipAttack);
        if (LocationConstants.opponentRace == Race.ZERG) { //ship weapons 1 first, for banshees to 2-shot tumors
            UpgradeManager.shipArmor.add(0, UpgradeManager.shipArmor.remove(3));
        }
        UpgradeManager.shipAttack.clear(); //no 2nd armory

        LocationConstants.STARPORTS = LocationConstants.STARPORTS.subList(0, 8);
        maxScvs = 80;
        DO_INCLUDE_LIBS = false;
        DO_INCLUDE_TANKS = false;
        DO_BANSHEE_HARASS = false;
        PRIORITIZE_EXPANDING = true;
        DO_SEEKER_MISSILE = false;
        RETREAT_HEALTH = 50;
        AUTOTURRET_AT_ENERGY = 50;
        DEFAULT_STARPORT_UNIT = Abilities.TRAIN_RAVEN;
    }

    private static void setStrategyNumber() {
        try {
            //hardcoded strategies by ID
            int strategyByOpponentId = getStrategyByOpponentId();
            if (strategyByOpponentId != -1) {
                selectedStrategy = strategyByOpponentId;
                return;
            }

            String[] fileText = Files.readString(Paths.get("./data/prevResult.txt")).split("~");
            String lastOpponentId = fileText[0];
            int lastOpponentStrategy = Integer.valueOf(fileText[1]);
            String lastResult = fileText[2];
            //start at 0 for new opponent
            if (!lastOpponentId.equals(KetrocBot.opponentId) || LocationConstants.opponentRace == Race.RANDOM) {
                selectedStrategy = 0;
            }
            //stay on same strategy if I win
            else if (lastResult.equals("W")) {
                selectedStrategy = lastOpponentStrategy;
            }
            //next strategy if I lose
            else if (lastResult.equals("L")) {
                selectedStrategy = lastOpponentStrategy + 1;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
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
                        KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
                        KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_SUPPLY_DEPOT));
                    }
                    else {
                        KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
                        KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    }
                    Switches.tvtFastStart = false;
                }
                break;
        }
    }

    public static void setMaxScvs() {
        //if no minerals left on the map
        if (GameCache.defaultRallyNode == null) {
            maxScvs = 6;
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
        KetrocBot.purchaseQueue.add(1, KetrocBot.purchaseQueue.remove(3));
        KetrocBot.purchaseQueue.add(4, new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.extraDepots.remove(LocationConstants.extraDepots.size()-1)));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));

        //get closest STARPORTS position
        Point2d closestStarportPos = LocationConstants.STARPORTS.stream()
                .min(Comparator.comparing(starportPos -> starportPos.distance(LocationConstants.MID_WALL_3x3)))
                .get();

        //build rax at closest position
        ((PurchaseStructure) KetrocBot.purchaseQueue.get(1)).setPosition(closestStarportPos);

        //save MID_WALL_3X3 for barracks' later position
        LocationConstants._3x3Structures.remove(0);
    }
}
