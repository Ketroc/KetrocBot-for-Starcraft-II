package com.ketroc.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.BuildManager;
import com.ketroc.managers.UpgradeManager;
import com.ketroc.models.DelayedChat;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.utils.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Strategy {
    public static final int NUM_OFFENSE_SCVS = 2;
    public static int NUM_BASES_TO_OC = 1;
    public static boolean WALL_OFF_IMMEDIATELY;
    public static GamePlan gamePlan = null;
    public static final List<GamePlan> availableGamePlans = new ArrayList<>(Arrays.asList(GamePlan.values()));

    public static int STEP_SIZE = 2;
    public static boolean DO_DIVE_RAVENS = true;
    public static boolean EARLY_BANSHEE_SPEED;
    public static boolean DO_LEAVE_UP_BUNKER;
    public static boolean NO_TURRETS;

    public static boolean DO_DEFENSIVE_TANKS;
    public static boolean DO_OFFENSIVE_TANKS;
    public static final int NUM_TANKS_PER_EXPANSION = 2; //only works for 2 atm
    public static int MAX_TANKS = 10;

    public static boolean DO_INCLUDE_LIBS;
    public static final int NUM_LIBS_PER_EXPANSION = 2; //only works for 2 atm
    public static final int MAX_LIBS = 10;

    public static int NUM_MARINES = 3;
    public static int MAX_OCS = 7;
    public static final int FUNGAL_FRAMES = 16; //# of frames for fungal to land after being cast
    public static float VIKING_BANSHEE_RATIO = 0.2f;
    public static final int MAX_VIKINGS_TO_DIVE_TEMPESTS = 20; //always dive tempests if we reach this number
    public static final float DISTANCE_RAISE_DEPOT = 10;
    public static final int MIN_STRUCTURE_HEALTH = 40; //TODO: repair to this % to prevent burn
    public static int maxScvs = 90;
    public static final float KITING_BUFFER = 2.4f + (STEP_SIZE > 2 ? 0.2f : 0);
    public static final float STATIONARY_KITING_BUFFER = 1.6f + (STEP_SIZE > 2 ? 0.2f : 0);
    public static int RETREAT_HEALTH = 42; //% health of mech unit to go home to get repaired
    public static final int NUM_DONT_EXPAND = 2; //number of bases to never try expanding to
    public static final float ENERGY_BEFORE_CLOAKING = 80f; //don't cloak banshee if their energy is under this value
    public static final int NUM_SCVS_REPAIR_STATION = 5;
    public static final float BANSHEE_RANGE = 6.1f; //range in which banshee will be given the command to attack
    public static final float MARINE_RANGE = 5.1f; //range in which banshee will be given the command to attack
    public static float RAVEN_CAST_RANGE = 7f;
    public static final float VIKING_RANGE = 9.1f; //range in which viking will be given the command to attack
    public static final int MIN_GAS_FOR_REFINERY = 1; //only build a refinery on this vespian node if it has at least this much gas
    public static int DIVE_RANGE = 12;
    public static final int TEMPEST_DIVE_RANGE = 23;
    public static final float RAVEN_DISTANCING_BUFFER = 3f;
    public static final int CAST_SEEKER_RANGE = 15;
    public static final float SEEKER_RADIUS = 3;
    public static final float MIN_SUPPLY_TO_SEEKER = 22;
    public static boolean techBuilt;
    public static final int MAP_ENEMIES_IN_FOG_DURATION = 112; //number of game frames to map the threat from enemies that entered the fog of war (5seconds)

    public static boolean MASS_RAVENS;
    public static boolean MARINE_ALLIN;
    public static boolean DO_BANSHEE_HARASS = true;
    public static boolean PRIORITIZE_EXPANDING;
    public static boolean BUILD_EXPANDS_IN_MAIN = true; //TODO: true for testing only
    public static boolean EXPAND_SLOWLY;
    public static boolean DO_SEEKER_MISSILE;
    public static boolean DO_MATRIX;
    public static boolean DO_ANTIDROP_TURRETS;
    public static int AUTOTURRET_AT_ENERGY = 50;
    public static Abilities DEFAULT_STARPORT_UNIT = Abilities.TRAIN_BANSHEE;
    public static boolean DO_USE_CYCLONES;
    public static boolean ENEMY_DOES_BANSHEE_HARASS;


    public static int step_TvtFastStart = 1;
    public static UnitInPool scv_TvtFastStart;
    public static int floatBaseAt = 50; //health% to float base away at
    public static boolean NO_RAMP_WALL;

    public static void onGameStart() {
        STEP_SIZE = (KetrocBot.isRealTime) ? 4 : 2;
        getGameStrategyChoice();

        if (DO_MATRIX && !DO_SEEKER_MISSILE) {
            RAVEN_CAST_RANGE = 9f;
        }
//
//        if (ANTI_NYDUS_BUILD) {
//            antiNydusBuild();
//        }
    }

    public static void onStep() {
        maxScvs = getMaxScvs();
    }

    private static void getGameStrategyChoice() {
        setRaceStrategies();
        if (!Bot.isRealTime) {
//            setStrategyNumber(); TODO: this is off for ladder
        }
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
            case RANDOM:
                gamePlan = GamePlan.BANSHEE_CYCLONE;
                useCyclonesAdjustments();
                Switches.enemyCanProduceAir = true;
                Switches.enemyHasCloakThreat = true;
        }

        //TODO: delete - turning off 2nd factory based on strategy choice
        if (LocationConstants.opponentRace != Race.TERRAN ||
                (gamePlan != GamePlan.RAVEN_CYCLONE &&
                        gamePlan != GamePlan.TANK_VIKING)) {
            if (LocationConstants.FACTORIES.size() == 2) {
                LocationConstants.STARPORTS.add(LocationConstants.FACTORIES.remove(1));
            }
        }

        applyOpponentSpecificTweaks();
        setRampWall();
        setReaperBlockWall();
    }

    private static void applyOpponentSpecificTweaks() {
        switch (KetrocBot.opponentId) {
//        switch ("496ce221-f561-42c3-af4b-d3da4490c46e") { //RStrelok
            case "0da37654-1879-4b70-8088-e9d39c176f19": //Spiny
                DO_BANSHEE_HARASS = false;
                break;
            case "d7bd5012-d526-4b0a-b63a-f8314115f101": //ANIbot
            case "76cc9871-f9fb-4fc7-9165-d5b748f2734a": //dantheman_3
                DO_ANTIDROP_TURRETS = true;
                DO_BANSHEE_HARASS = false;
                break;
            case "9bcd0618-172f-4c70-8851-3807850b45a0": //snowbot
                break;
            case "b4d7dc43-3237-446f-bed1-bceae0868e89": //ThreeWayLover
            case "7b8f5f78-6ca2-4079-b7c0-c7a3b06036c6": //Blinkerbot
            case "9bd53605-334c-4f1c-95a8-4a735aae1f2d": //MadAI
            case "ba7782ea-4dde-4a25-9953-6d5587a6bdcd": //AdditionalPylons
                break;
            case "16ab8b85-cf8b-4872-bd8d-ebddacb944a5": //sharpy_PVP_EZ
                Switches.enemyCanProduceAir = true;
                break;
            case "c8ed3d8b-3607-40e3-b7fe-075d9c08a5fd": //QueenBot
                DO_DEFENSIVE_TANKS = false;
                DO_INCLUDE_LIBS = false;
                break;
            case "1574858b-d54f-47a4-b06a-0a6431a61ce9": //sproutch
                break;
            case "3c78e739-5bc8-4b8b-b760-6dca0a88b33b": //Fidolina
            case "8f94d1fd-e5ee-4563-96d1-619c9d81290e": //DominionDog
                DO_DEFENSIVE_TANKS = false;
                DO_BANSHEE_HARASS = false;
                //EXPAND_SLOWLY = true;
                BUILD_EXPANDS_IN_MAIN = true;
                NO_RAMP_WALL = true;
                NO_TURRETS = true;
                NUM_MARINES = 1;
                ENEMY_DOES_BANSHEE_HARASS = true;
                break;
            case "54bca4a3-7539-4364-b84b-e918784b488a": //Jensiii
            case "2aa93279-f382-4e26-bfbb-6ef3cc6f9104": //TestBot (jensiiibot)
                NUM_MARINES = 7;
                Switches.enemyCanProduceAir = true;
                BUILD_EXPANDS_IN_MAIN = true;
                DO_BANSHEE_HARASS = false;
                break;
            case "12c39b76-7830-4c1f-9faa-37c68183396b": //WorthlessBot
                BUILD_EXPANDS_IN_MAIN = true;
                EXPAND_SLOWLY = true;
                break;
            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
            case "10ecc3c36541ead": //RStrelok (LM)
                BUILD_EXPANDS_IN_MAIN = true;
                DO_DIVE_RAVENS = false;
                ENEMY_DOES_BANSHEE_HARASS = true;
                //Switches.enemyCanProduceAir = true;
                break;
            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
                Switches.enemyCanProduceAir = true;
                BUILD_EXPANDS_IN_MAIN = true;
                DO_DIVE_RAVENS = false;
                ENEMY_DOES_BANSHEE_HARASS = true;
                break;
        }
    }

    private static void setRampWall() {
        if (NO_RAMP_WALL) {
            LocationConstants._3x3Structures.add(LocationConstants._3x3Structures.remove(0));
            LocationConstants._3x3Structures.add(LocationConstants._3x3Structures.remove(0));
            LocationConstants.extraDepots.add(LocationConstants.extraDepots.remove(0));
        }
    }

    private static void chooseTvTStrategy() {
        Set<GamePlan> availableTvTGamePlans = new HashSet<>(Set.of(
                GamePlan.BANSHEE, GamePlan.MARINE_RUSH, GamePlan.SCV_RUSH, GamePlan.BUNKER_CONTAIN_STRONG, GamePlan.RAVEN,
                GamePlan.BANSHEE_CYCLONE, GamePlan.RAVEN_CYCLONE
        ));
        while (!availableTvTGamePlans.contains(gamePlan)) {
            gamePlan = getNextGamePlan(gamePlan);
        }
        gamePlan = GamePlan.TANK_VIKING; //FIXME: hardcoded strategy
        switch (gamePlan) {
            case BANSHEE:
                break;
            case BANSHEE_CYCLONE:
                useCyclonesAdjustments();
                break;
            case BANSHEE_TANK:
                useTanksAdjustments();
                break;
            case TANK_VIKING:
                useTankVikingAdjustments();
                break;
            case BUNKER_CONTAIN_STRONG:
                BunkerContain.proxyBunkerLevel = 2;
                break;
            case SCV_RUSH:
                Switches.scvRushComplete = false;
                break;
            case RAVEN:
                massRavenStrategy();
                break;
            case RAVEN_CYCLONE:
                massRavenStrategy();
                useCyclonesAdjustments();
                DO_MATRIX = true;
                Strategy.AUTOTURRET_AT_ENERGY = 125;
                break;
            case MARINE_RUSH:
                marineAllinStrategy();
                break;
        }
        DelayedChat.add("Strategy: " + gamePlan);
    }

    public static GamePlan getNextGamePlan(GamePlan curPlan) {
        int nextIndex = (availableGamePlans.indexOf(curPlan) + 1) % availableGamePlans.size();
        return availableGamePlans.get(nextIndex);
    }

    private static void marineAllinStrategy() {
        MARINE_ALLIN = true;
        NUM_MARINES = Integer.MAX_VALUE;
    }

    private static void chooseTvPStrategy() {
        Set<GamePlan> availableTvPGamePlans = new HashSet<>(Set.of(
                GamePlan.BANSHEE, GamePlan.MARINE_RUSH, GamePlan.SCV_RUSH, GamePlan.BUNKER_CONTAIN_WEAK, GamePlan.RAVEN
        ));
        while (!availableTvPGamePlans.contains(gamePlan)) {
            gamePlan = getNextGamePlan(gamePlan);
        }
        gamePlan = GamePlan.BANSHEE; //TODO: hardcoded strategy
        switch (gamePlan) {
            case BANSHEE:
                DelayedChat.add("Standard Strategy");
                break;
            case BANSHEE_CYCLONE:
                DelayedChat.add("Banshee & Cyclone Strategy");
                useCyclonesAdjustments();
                break;
            case BUNKER_CONTAIN_WEAK:
                DelayedChat.add("Light Bunker Contain Strategy");
                BunkerContain.proxyBunkerLevel = 1;
                break;
            case SCV_RUSH:
                DelayedChat.add("SCV Rush Strategy");
                DelayedChat.add(Time.nowFrames() + 100, "... because Ketroc has reached its allotted limit of long games");
                Switches.scvRushComplete = false;
                break;
            case RAVEN:
                DelayedChat.add("Mass Raven Strategy");
                massRavenStrategy();
                break;
//            case MASS_RAVEN_WITH_CYCLONES:
//                DelayedChat.add("Raven & Cyclone Strategy");
//                massRavenStrategy();
//                useCyclonesAdjustments();
//                break;
            case MARINE_RUSH:
                DelayedChat.add("7Rax Marine All in");
                DelayedChat.add(Time.nowFrames() + 100, "... because sometimes you gotta keep those greedy bots in check");
                marineAllinStrategy();
                break;
        }
    }

    private static void chooseTvZStrategy() {
        Set<GamePlan> availableTvZGamePlans = new HashSet<>(Set.of(
                GamePlan.BANSHEE, GamePlan.MARINE_RUSH, GamePlan.SCV_RUSH, GamePlan.BUNKER_CONTAIN_WEAK,
                GamePlan.RAVEN, GamePlan.BANSHEE_CYCLONE, GamePlan.RAVEN_CYCLONE
        ));
        while (!availableTvZGamePlans.contains(gamePlan)) {
            gamePlan = getNextGamePlan(gamePlan);
        }
        gamePlan = GamePlan.BANSHEE_CYCLONE; //TODO: hardcoded strategy
        switch (gamePlan) {
            case BANSHEE:
                DelayedChat.add("Standard Strategy");
                break;
            case BANSHEE_CYCLONE:
                DelayedChat.add("Banshee & Cyclone Strategy");
                useCyclonesAdjustments();
                break;
            case BUNKER_CONTAIN_WEAK:
                BunkerContain.proxyBunkerLevel = 1;
                break;
            case SCV_RUSH:
                DelayedChat.add("SCV Rush Strategy");
                DelayedChat.add(Time.nowFrames() + 100, "... because Ketroc has reached its allotted limit of long games");
                Switches.scvRushComplete = false;
                break;
            case RAVEN:
                DelayedChat.add("Mass Raven Strategy");
                massRavenStrategy();
                break;
            case RAVEN_CYCLONE:
                DelayedChat.add("Raven & Cyclone Strategy");
                massRavenStrategy();
                useCyclonesAdjustments();
                break;
            case MARINE_RUSH:
                DelayedChat.add("7Rax Marine All in");
                DelayedChat.add(Time.nowFrames() + 100, "... because sometimes you gotta keep those greedy bots in check");
                marineAllinStrategy();
                break;
        }
    }

    private static GamePlan getStrategyByOpponentId() {
        if (KetrocBot.opponentId == null) {
            return gamePlan;
        }
        switch (KetrocBot.opponentId) {
//        switch ("496ce221-f561-42c3-af4b-d3da4490c46e") {
//            case "0da37654-1879-4b70-8088-e9d39c176f19": //Spiny
//                return 4;
//            case "d7bd5012-d526-4b0a-b63a-f8314115f101": //ANIbot
//            case "76cc9871-f9fb-4fc7-9165-d5b748f2734a": //dantheman_3
//                return 1;
//            case "9bcd0618-172f-4c70-8851-3807850b45a0": //snowbot
//                return 1;
//            case "b4d7dc43-3237-446f-bed1-bceae0868e89": //ThreeWayLover
//            case "7b8f5f78-6ca2-4079-b7c0-c7a3b06036c6": //Blinkerbot
//            case "9bd53605-334c-4f1c-95a8-4a735aae1f2d": //MadAI
//            //case "ba7782ea-4dde-4a25-9953-6d5587a6bdcd": //AdditionalPylons
//                return 1;
//            case "16ab8b85-cf8b-4872-bd8d-ebddacb944a5": //sharpy_PVP_EZ
////                Switches.enemyCanProduceAir = true;
//                return 1;
//            case "c8ed3d8b-3607-40e3-b7fe-075d9c08a5fd": //QueenBot
////                DO_INCLUDE_TANKS = false;
////                DO_INCLUDE_LIBS = false;
//                return 0;
//            case "1574858b-d54f-47a4-b06a-0a6431a61ce9": //sproutch
////                Switches.enemyCanProduceAir = true;
////                DO_INCLUDE_TANKS = false;
////                DO_INCLUDE_LIBS = false;
////                DO_BANSHEE_HARASS = false;
////                BUILD_EXPANDS_IN_MAIN = true;
////                EXPAND_SLOWLY = true;
//                return 3;
//            case "3c78e739-5bc8-4b8b-b760-6dca0a88b33b": //Fidolina
//            case "8f94d1fd-e5ee-4563-96d1-619c9d81290e": //DominionDog
//                return 0;
//            case "12c39b76-7830-4c1f-9faa-37c68183396b": //WorthlessBot
////                BUILD_EXPANDS_IN_MAIN = true;
////                EXPAND_SLOWLY = true;
//                return 0;
            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
            case "10ecc3c36541ead": //RStrelok (LM)
                return GamePlan.BANSHEE;
            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
                return GamePlan.BANSHEE;
            default:
                return gamePlan;
        }
    }

    private static void massRavenStrategy() {
        MASS_RAVENS = true;
        UpgradeManager.starportUpgradeList = new ArrayList<>(List.of(Upgrades.RAVEN_CORVID_REACTOR));
        UpgradeManager.doStarportUpgrades = true;

        //get 2 banshees and +1attack for creep clearing and early defense
        if (LocationConstants.opponentRace == Race.ZERG) {
            BuildManager.MIN_BANSHEES = 2;
            UpgradeManager.armoryUpgradeList = new ArrayList<>();
            UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1);
            UpgradeManager.armoryUpgradeList.addAll(UpgradeManager.mechArmorUpgrades);
            UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2);
            UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3);

        }
        else {
            UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.mechArmorUpgrades);
            UpgradeManager.armoryUpgradeList.addAll(UpgradeManager.airUpgrades);
        }

        LocationConstants.STARPORTS = LocationConstants.STARPORTS.subList(0, 8);
        DO_BANSHEE_HARASS = false;
        DO_INCLUDE_LIBS = false;
        DO_DEFENSIVE_TANKS = false;
        EXPAND_SLOWLY = false;
        PRIORITIZE_EXPANDING = true;
        DO_SEEKER_MISSILE = false;
        RETREAT_HEALTH = 50;
        AUTOTURRET_AT_ENERGY = 50;
        DEFAULT_STARPORT_UNIT = Abilities.TRAIN_RAVEN;
    }

    private static void setStrategyNumber() {
        //hardcoded strategies by ID
//        gamePlan = getStrategyByOpponentId();
        try {
            // ====================================
            // ===== For Best-of Series Below =====
            // ====================================

            //get list of previous results vs this opponent
            String fileText = Files.readString(Paths.get("./data/prevResult.txt"));
            List<String[]> prevResults = new ArrayList<>();
            Print.print("fileText = " + fileText);
            if (fileText.contains(KetrocBot.opponentId)) {
                String[] rows = fileText.split("\r\n");
                for (String row : rows) {
                    Print.print(row);
                    prevResults.add(row.split("~"));
                }
            }
            playAllStrategiesFirst(fileText, prevResults);
//            if (KetrocBot.opponentId.equals("81fa0acc-93ea-479c-9ba5-08ae63b9e3f5")) { //Micromachine
//                playStrategyUntilItLoses(fileText, prevResults);
//            }
//            else {
//                playAllStrategiesFirst(fileText, prevResults);
//                playStrategyUntilItLoses(fileText, prevResults); //TODO: switch back
//            }

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private static void playStrategyUntilItLoses(String fileText, List<String[]> prevResults) {
//        //get strategy order by id
//        GamePlan[] strategies = getTournamentStrategyOrder();
//
//        if (prevResults.isEmpty()) {
//            return;
//        }
//        String[] prevGame = prevResults.get(prevResults.size()-1);
//        GamePlan prevGamePlan = GamePlan.valueOf(prevGame[1]);
//        String prevGameResult = prevGame[2];
//        if (prevGameResult.equals("W")) {
//            gamePlan = prevGamePlan;
//            Print.print("Selecting strategy: " + gamePlan + " cuz it won last game.");
//        }
//        else {
//            gamePlan = getNextGamePlan(prevGamePlan);
//            Print.print("Selecting strategy: " + gamePlan + " cuz prev strategy lost last game.");
//        }
//
//    }

//    private static void playStrategyUntilItLoses(String fileText, List<String[]> prevResultList) {
//        //get strategy order by id
//        GamePlan[] strategies = getTournamentStrategyOrder();
//
//        //no opponent specific strategy, and no history
//        if (strategies == null) {
//            if (prevResultList.isEmpty() || !prevResultList.get(0)[0].equals(KetrocBot.opponentId)) {
//                Print.print("using GamePlan.BANSHEES cuz no list and no history");
//                gamePlan = GamePlan.BANSHEES;
//            }
//            //no opponent specific strategy, and with history
//            else {
//                String[] prevResult = prevResultList.get(prevResultList.size() - 1);
//                if (prevResult[2].equals("L")) {
//                    gamePlan = getNextGamePlan(GamePlan.valueOf(prevResult[1]));
//                    Print.print("using " + gamePlan + " cuz no list and lost last game");
//                }
//                else {
//                    gamePlan = GamePlan.valueOf(prevResult[1]);
//                    Print.print("using " + gamePlan + " cuz no list and won last game");
//                }
//            }
//            return;
//        }
//        //if opponent specific strategy, and no history
//        if (prevResultList.isEmpty() || !prevResultList.get(0)[0].equals(KetrocBot.opponentId)) {
//            gamePlan = GamePlan.valueOf(prevResultList.get(0)[1]);
//            Print.print("using " + gamePlan + " cuz first in list and no history");
//        }
//        //if opponent specific strategy, and with history
//        else {
//            String[] prevResult = prevResultList.get(prevResultList.size() - 1);
//            if (prevResult[2].equals("W")) {
//                gamePlan = GamePlan.valueOf(prevResult[1]);
//                Print.print("using " + gamePlan + " cuz no list and won last game");
//            }
//            else {
//                int nextIndex = (strategies.indexOf(GamePlan.valueOf(prevResult[1])) + 1) % availableGamePlans.size();
//                .get(nextIndex);
//                gamePlan = getNextGamePlan(GamePlan.valueOf(prevResult[1]));
//                Print.print("using " + gamePlan + " cuz no list and lost last game");
//            }
//        }
//
//
//        if (!wereAllStrategiesUsed(strategies, fileText)) {
//            for (GamePlan strategy : strategies) {
//                Print.print("checking strategy: " + strategy);
//                if (!fileText.contains(KetrocBot.opponentId + "~" + strategy + "~")) {
//                    if (strategy == GamePlan.SCV_RUSH &&
//                            (LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD) || LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD506))) {
//                        Print.print("skipping scv rush cuz it's Pillars of Gold");
//                        continue;
//                    }
//                    gamePlan = strategy;
//                    Print.print("using " + gamePlan + " cuz it's in the strategy list");
//                    return;
//                }
//            }
//        }
//
//        //if no more planned strategies, pick whichever one won
//        gamePlan = prevResultList.stream()
//                .filter(result -> result[0].equals(KetrocBot.opponentId) && result[2].equals("W"))
//                .map(result -> GamePlan.valueOf(result[1]))
//                .findFirst()
//                .orElse(GamePlan.valueOf(prevResultList.get(0)[1]));
//        Print.print("using " + gamePlan + " cuz list finished and this is a strat that won (default: first in list)");
//    }


    private static void playAllStrategiesFirst(String fileText, List<String[]> prevResults) {
        //get strategy order by id
        GamePlan[] strategies = getTournamentStrategyOrder();

        //no opponent specific strategy, and no history
        if (strategies == null) {
            if (prevResults.isEmpty() || !prevResults.get(0)[0].equals(KetrocBot.opponentId)) {
                Print.print("using GamePlan.BANSHEES cuz no list and no history");
                gamePlan = GamePlan.BANSHEE;
            }
            //no opponent specific strategy, and with history
            else {
                String[] prevResult = prevResults.get(prevResults.size() - 1);
                if (prevResult[2].equals("L")) {
                    gamePlan = getNextGamePlan(GamePlan.valueOf(prevResult[1]));
                    Print.print("using " + gamePlan + " cuz no list and lost last game");
                }
                else {
                    gamePlan = GamePlan.valueOf(prevResult[1]);
                    Print.print("using " + gamePlan + " cuz no list and won last game");
                }
            }
            return;
        }
        //select next planned strategy
        if (!wereAllStrategiesUsed(strategies, fileText)) {
            for (GamePlan strategy : strategies) {
                Print.print("checking strategy: " + strategy);
                if (!fileText.contains(KetrocBot.opponentId + "~" + strategy + "~")) {
                    if (strategy == GamePlan.SCV_RUSH &&
                            (LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD) || LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD506))) {
                        Print.print("skipping scv rush cuz it's Pillars of Gold");
                        continue;
                    }
                    gamePlan = strategy;
                    Print.print("using " + gamePlan + " cuz it's in the strategy list");
                    return;
                }
            }
        }

        //if no more planned strategies, pick whichever one won
        gamePlan = prevResults.stream()
                .filter(result -> result[0].equals(KetrocBot.opponentId) && result[2].equals("W"))
                .map(result -> GamePlan.valueOf(result[1]))
                .findFirst()
                .orElse(GamePlan.valueOf(prevResults.get(0)[1]));
        Print.print("using " + gamePlan + " cuz list finished and this is a strat that won (default: first in list)");
    }

    private static boolean wereAllStrategiesUsed(GamePlan[] strategies, String fileText) {
        for (GamePlan strategy : strategies) {
            if (!fileText.contains("~" + strategy + "~")) {
                return false;
            }
        }
        return true;
    }

    private static GamePlan[] getTournamentStrategyOrder() {
        switch (KetrocBot.opponentId) { //0 standard, 1 proxy bunker, 2 scv rush, 3 mass raven, 4 marine all-in
            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
            case "10ecc3c36541ead": //RStrelok (LM)
                return new GamePlan[]{GamePlan.BANSHEE_CYCLONE, GamePlan.BUNKER_CONTAIN_STRONG};
            case "3c78e739-5bc8-4b8b-b760-6dca0a88b33b": //Fidolina
            case "8f94d1fd-e5ee-4563-96d1-619c9d81290e": //DominionDog
                return new GamePlan[]{GamePlan.BANSHEE_CYCLONE};
            case "0da37654-1879-4b70-8088-e9d39c176f19": //Spiny
            //case "b7b611bdaa2e2d1": //Spiny (LM)
                return new GamePlan[]{GamePlan.BANSHEE_CYCLONE, GamePlan.BUNKER_CONTAIN_WEAK, GamePlan.RAVEN};
            case "54bca4a3-7539-4364-b84b-e918784b488a": //Jensiii
            case "2aa93279-f382-4e26-bfbb-6ef3cc6f9104": //TestBot (jensiiibot)
                return new GamePlan[]{GamePlan.RAVEN, GamePlan.BANSHEE, GamePlan.BUNKER_CONTAIN_STRONG};
            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
                return new GamePlan[]{GamePlan.BUNKER_CONTAIN_STRONG, GamePlan.BANSHEE, GamePlan.BANSHEE_CYCLONE};
//            case "2557ad1d-ee42-4aaa-aa1b-1b46d31153d2": //BenBotBC
//                return new int[]{0, 1, 2};
//            case "7b8f5f78-6ca2-4079-b7c0-c7a3b06036c6": //BlinkerBot
//                return new int[]{0, 2, 1};
//            case "d7bd5012-d526-4b0a-b63a-f8314115f101": //ANIbot
//                return new int[]{0, 2};
//            case "b4d7dc43-3237-446f-bed1-bceae0868e89": //ThreeWayLover
//                return new int[]{3, 1};
        }
        return null;
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
                //remove depot and rax from production queue (temporarily)
//                KetrocBot.purchaseQueue.remove(0);
//                KetrocBot.purchaseQueue.remove(0);

                //rally cc to the depot pos
                ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, LocationConstants.extraDepots.get(0), false);
                step_TvtFastStart++;
                break;

            case 2:
                //rally cc to mineral node
                if (Bot.OBS.getFoodWorkers() == 13) {
                    ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER,
                            GameCache.baseList.get(0).getFullestMineralPatch(), false);
                    step_TvtFastStart++;
                }
                break;
            case 3:
                //add depot and rax with first scv to the production queue
                if (GameCache.mineralBank >= 100) {
                    scv_TvtFastStart = Bot.OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV).stream()
                            .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), LocationConstants.extraDepots.get(0))))
                            .get();
                    ((PurchaseStructure)KetrocBot.purchaseQueue.get(0)).setScv(scv_TvtFastStart.unit());
                    ((PurchaseStructure)KetrocBot.purchaseQueue.get(1)).setScv(scv_TvtFastStart.unit());
                    Switches.tvtFastStart = false;
                }
                break;
        }
    }

    public static int getMaxScvs() {
        //if no minerals left on the map
        if (GameCache.defaultRallyNode == null) {
            return 6;
        }

        //cutting workers to wall off at the start of the game
        if (Strategy.WALL_OFF_IMMEDIATELY && !UnitUtils.isWallComplete()) {
            return 13; //TODO: change to 14 when my bot sends scvs to build structures early
        }

        //marine all-in without an expansion
        if (MARINE_ALLIN && !GameCache.baseList.get(1).isMyBase()) {
            return 18;
        }

        //if maxed out on macro OCs
        if (LocationConstants.MACRO_OCS.isEmpty() && GameCache.mineralBank > 3000) {
            return 50;
        }

        if (MASS_RAVENS) {
            return 80;
        }

        return 90;
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

    public static void setRaceStrategies() {
        switch (LocationConstants.opponentRace) {
            case ZERG:
                DO_INCLUDE_LIBS = true;
                DO_DEFENSIVE_TANKS = true;
                MAX_OCS = 25;
                break;
            case PROTOSS:
                DIVE_RANGE = 25;
                DO_INCLUDE_LIBS = false;
                DO_DEFENSIVE_TANKS = false;
                NUM_MARINES = 5;
                break;
            case TERRAN:
                DO_DIVE_RAVENS = false;
                DO_INCLUDE_LIBS = false;
                DO_DEFENSIVE_TANKS = false;
                NUM_MARINES = 3;
                break;
        }
    }

    public static void useCyclonesAdjustments() {
        NUM_MARINES = Math.min(3, NUM_MARINES);
        DO_USE_CYCLONES = true;
        DO_DEFENSIVE_TANKS = false;
    }

    public static void useTanksAdjustments() {
        UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.allUpgrades);
        NUM_MARINES = Math.min(3, NUM_MARINES);
        DO_OFFENSIVE_TANKS = true;
        NUM_BASES_TO_OC = 3;
    }

    public static void useTankVikingAdjustments() {
        UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.allUpgrades);
        UpgradeManager.starportUpgradeList.clear();

        //NUM_MARINES = Math.min(3, NUM_MARINES);
        DO_OFFENSIVE_TANKS = true;
        NUM_BASES_TO_OC = 3;
        DEFAULT_STARPORT_UNIT = Abilities.TRAIN_RAVEN;
    }

    private static void setReaperBlockWall() {
        if (LocationConstants.opponentRace == Race.TERRAN) {
            LocationConstants.extraDepots.addAll(0, LocationConstants.reaperBlockDepots);
            LocationConstants._3x3Structures.addAll(0, LocationConstants.reaperBlock3x3s);

            //decide if middle structure in wall is a depot or barracks
            if (!LocationConstants.reaperBlock3x3s.isEmpty()) {
                LocationConstants._3x3Structures.remove(LocationConstants.MID_WALL_3x3);
            }
            else {
                LocationConstants.extraDepots.remove(LocationConstants.MID_WALL_2x2);
            }
        }
        else {
            LocationConstants.extraDepots.addAll(LocationConstants.reaperBlockDepots);
            LocationConstants._3x3Structures.addAll(LocationConstants.reaperBlock3x3s);
            LocationConstants.extraDepots.remove(LocationConstants.MID_WALL_2x2);
        }

        //remove 2nd entry of wall/midwall (duplicate) as they are both in extraDepots and in reaperBlockDepots lists
        if (LocationConstants.MAP.equals(MapNames.SUBMARINE) || LocationConstants.MAP.equals(MapNames.SUBMARINE505)) {
            for (int i = LocationConstants.extraDepots.size()-1; i>=2; i--) {
                if (LocationConstants.extraDepots.get(i).equals(LocationConstants.MID_WALL_2x2)) {
                    LocationConstants.extraDepots.remove(i);
                }
                else if (LocationConstants.extraDepots.get(i).equals(LocationConstants.WALL_2x2)) {
                    LocationConstants.extraDepots.remove(i);
                }
            }
        }
    }

    public static void printStrategySettings() {
        Print.print("selectedStrategy = " + gamePlan);
        Print.print("DO_DIVE_RAVENS = " + DO_DIVE_RAVENS);
        Print.print("EARLY_BANSHEE_SPEED = " + EARLY_BANSHEE_SPEED);
        Print.print("DO_LEAVE_UP_BUNKER = " + DO_LEAVE_UP_BUNKER);
        Print.print("NO_TURRETS = " + NO_TURRETS);

        Print.print("DO_INCLUDE_TANKS = " + DO_DEFENSIVE_TANKS);
        Print.print("MAX_TANKS = " + MAX_TANKS);

        Print.print("DO_INCLUDE_LIBS = " + DO_INCLUDE_LIBS);
        Print.print("MAX_LIBS = " + MAX_LIBS);

        Print.print("DO_BANSHEE_HARASS = " + DO_BANSHEE_HARASS);
        Print.print("PRIORITIZE_EXPANDING = " + PRIORITIZE_EXPANDING);
        Print.print("BUILD_EXPANDS_IN_MAIN = " + BUILD_EXPANDS_IN_MAIN);
        Print.print("EXPAND_SLOWLY = " + EXPAND_SLOWLY);
        Print.print("DO_SEEKER_MISSILE = " + DO_SEEKER_MISSILE);
        Print.print("DO_ANTIDROP_TURRETS = " + DO_ANTIDROP_TURRETS);
        Print.print("DEFAULT_STARPORT_UNIT = " + DEFAULT_STARPORT_UNIT);
        Print.print("NO_RAMP_WALL = " + NO_RAMP_WALL);
    }
}
