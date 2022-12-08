package com.ketroc.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.gamestate.GameCache;
import com.ketroc.gson.GameResult;
import com.ketroc.gson.JsonUtil;
import com.ketroc.gson.Opponent;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.BuildManager;
import com.ketroc.managers.UpgradeManager;
import com.ketroc.micro.Harassers;
import com.ketroc.models.Base;
import com.ketroc.models.DelayedChat;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.utils.*;

import java.util.*;

public class Strategy {
    public static boolean ARCHON_MASS_RAVEN; //turn on for playing mass raven in archon mode
    public static boolean TOURNAMENT_MODE;
    public static boolean RANDOM_STRATEGY_SELECTION;
    public static boolean DO_WALL_NAT;
    public static boolean NO_UPGRADES;

    public static final int NUM_OFFENSE_SCVS = 4;
    public static int NUM_BASES_TO_OC = 1;
    public static boolean WALL_OFF_IMMEDIATELY;
    public static GamePlan gamePlan = GamePlan.NONE;
    public static final List<GamePlan> availableGamePlans = new ArrayList<>(Arrays.asList(GamePlan.values()));

    public static boolean DO_DIVE_MOBILE_DETECTORS = true;
    public static boolean EARLY_BANSHEE_SPEED;
    public static boolean DO_LEAVE_UP_BUNKER;
    public static boolean NO_TURRETS;

    public static boolean DO_DEFENSIVE_TANKS;
    public static boolean DO_OFFENSIVE_TANKS;
    public static final int NUM_TANKS_PER_EXPANSION = 2; //only works for 2 atm
    public static int MAX_TANKS = 10;

    public static boolean DO_DEFENSIVE_LIBS;
    public static final int NUM_LIBS_PER_EXPANSION = 2; //only works for 2 atm
    public static final int MAX_LIBS = 10;

    public static int MAX_MARINES = 3;
    public static int MAX_OCS = 15;
    public static final int FUNGAL_FRAMES = 16; //# of frames for fungal to land after being cast
    public static float VIKING_BANSHEE_RATIO = 0.2f;
    public static final int MAX_VIKINGS_TO_DIVE_TEMPESTS = 19; //always dive tempests if we reach this number
    public static final float DISTANCE_RAISE_DEPOT = 7f;
    public static final int MIN_STRUCTURE_HEALTH = 40; //TODO: repair to this % to prevent burn
    public static int maxScvs = 90;
    public static final float KITING_BUFFER = 2.4f + (Launcher.STEP_SIZE > 2 ? 0.3f : 0);
    public static final float STATIONARY_KITING_BUFFER = 1.55f + (Launcher.STEP_SIZE > 2 ? 0.3f : 0);
    public static int RETREAT_HEALTH = 42; //% health of mech unit to go home to get repaired
    public static final int NUM_DONT_EXPAND = 2; //number of bases to never try expanding to
    public static final float ENERGY_TO_SAVE = 80f; //don't cloak banshee if their energy is under this value
    public static final int NUM_SCVS_REPAIR_STATION = 5;
    public static final float BANSHEE_RANGE = 6.05f; //range in which banshee will be given the command to attack
    public static final float HELLBAT_RANGE = 2.05f; //range in which banshee will be given the command to attack
    public static final float GHOST_RANGE = 6.05f; //range in which ghost will be given the command to attack
    public static final float MARINE_RANGE = 5.05f; //range in which marine will be given the command to attack
    public static final float HELLION_RANGE = 5.05f; //range in which hellion will be given the command to attack
    public static float RAVEN_CAST_RANGE = 10f;
    public static final float VIKING_RANGE = 9.05f; //range in which viking will be given the command to attack
    public static final int MIN_GAS_FOR_REFINERY = 1; //only build a refinery on this vespian node if it has at least this much gas
    public static int DIVE_RANGE = 12;
    public static final int TEMPEST_DIVE_RANGE = 23;
    public static final float RAVEN_DISTANCING_BUFFER = 3f;
    public static final int CAST_SEEKER_RANGE = 15;
    public static final float SEEKER_RADIUS = 3;
    public static final float MIN_SUPPLY_TO_SEEKER = 25;
    public static boolean techBuilt;
    public static final int MAP_ENEMIES_IN_FOG_DURATION = 112; //number of game frames to map the threat from enemies that entered the fog of war (5seconds)

    public static boolean MASS_RAVENS;
    public static boolean MASS_MINE_OPENER;
    public static boolean MARINE_ALLIN;
    public static boolean DO_BANSHEE_HARASS = true;
    public static boolean PRIORITIZE_EXPANDING;
    public static boolean BUILD_EXPANDS_IN_MAIN;
    public static boolean EXPAND_SLOWLY;
    public static boolean DO_SEEKER_MISSILE;
    public static boolean DO_MATRIX;
    public static boolean DO_ANTIDROP_TURRETS;
    public static int AUTOTURRET_AT_ENERGY = 170;
    public static Abilities DEFAULT_STARPORT_UNIT = Abilities.TRAIN_BANSHEE;
    public static boolean DO_USE_CYCLONES;
    public static boolean DO_USE_HELLIONS;
    public static boolean DO_IGNORE_BUNKERS;
    public static boolean ENEMY_DOES_BANSHEE_HARASS;


    public static int step_TvtFastStart = 1;
    public static UnitInPool scv_TvtFastStart;
    public static int floatBaseAt = 50; //health% to float base away at
    public static boolean NO_RAMP_WALL;
    public static int MIN_BANSHEES = 1;
    public static int MAX_BANSHEES = 20;

    public static void onGameStart() {
        if (ARCHON_MASS_RAVEN) {
            Strategy.gamePlan = GamePlan.RAVEN;
            Chat.chat("play with me");
        }

        getGameStrategyChoice();

        if (DO_MATRIX && !DO_SEEKER_MISSILE) {
            RAVEN_CAST_RANGE = 9f;
        }
    }

    public static void onStep() {
        //end mass mine strategy when a mobile detector is spotted
        if (MASS_MINE_OPENER) {
            Chat.tag("MASS_MINE_OPENER");
            if (!UnitUtils.getEnemyUnitsOfType(UnitUtils.MOBILE_DETECTOR_TYPES).isEmpty() ||
                    !UnitUtils.getEnemyUnitsOfType(UnitUtils.SIEGE_TANK_TYPE).isEmpty()) {
                MASS_MINE_OPENER = false;
            }
        }
        maxScvs = getMaxScvs();
    }

    private static void getGameStrategyChoice() {
        System.out.println("in getGameStrategyChoice");
        setRaceStrategies();
        if (TOURNAMENT_MODE) {
            gamePlan = getTournamentGamePlan();
        }
        switch (PosConstants.opponentRace) {
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
                gamePlan = GamePlan.ONE_BASE_BANSHEE_CYCLONE;
                useCyclonesAdjustments();
                MAX_MARINES = 4;
                Switches.enemyCanProduceAir = true;
                Switches.doNeedDetection = true;
        }
        System.out.println("gamePlan = " + gamePlan);
        if (!Launcher.isRealTime) {
            DelayedChat.add("Strategy: " + gamePlan);
            Chat.tag(gamePlan.toString());
        }

        applyOpponentSpecificTweaks();
        setRampWall();
        setReaperBlockWall();
        setNatWall();
    }

    private static void applyOpponentSpecificTweaks() {
        switch (KetrocBot.opponentId) {
            case "71089047-c9cc-42f9-8657-8bafa0df89a0": //NegativeZero
                DO_BANSHEE_HARASS = false;
                Switches.phoenixAreReal = true;
                break;
            case "2540c0f3-238f-40a7-9c39-2e4f3dca2e2f": //sharkbot
                DO_BANSHEE_HARASS = false;
                break;
            case "6bcce16a-8139-4dc0-8e72-b7ee8b3da1d8": //Eris
            case "5b5220da-cc18-4c2e-acdf-68752a3701c3": //ErisTest
                DO_BANSHEE_HARASS = false;
                DO_DIVE_MOBILE_DETECTORS = false;
                break;
            case "841b33a8-e530-40f5-8778-4a2f8716095d": //Zoe
                NO_TURRETS = true;
                DO_DEFENSIVE_TANKS = true;
                break;
            case "0da37654-1879-4b70-8088-e9d39c176f19": //Spiny
                DO_DIVE_MOBILE_DETECTORS = false;
                break;
            case "d7bd5012-d526-4b0a-b63a-f8314115f101": //ANIbot
            case "76cc9871-f9fb-4fc7-9165-d5b748f2734a": //dantheman_3
                //DO_ANTIDROP_TURRETS = true;
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
                DO_DEFENSIVE_LIBS = false;
                break;
            case "1574858b-d54f-47a4-b06a-0a6431a61ce9": //sproutch
                break;
            case "3c78e739-5bc8-4b8b-b760-6dca0a88b33b": //Fidolina
            case "8f94d1fd-e5ee-4563-96d1-619c9d81290e": //DominionDog
                DO_BANSHEE_HARASS = false;
                BUILD_EXPANDS_IN_MAIN = true;
                ENEMY_DOES_BANSHEE_HARASS = true;
                Switches.enemyCanProduceAir = true;
                BuildManager.openingStarportUnits.clear();
                BuildManager.openingStarportUnits.add(Units.TERRAN_VIKING_FIGHTER);
                BuildManager.openingStarportUnits.add(Units.TERRAN_RAVEN);
                BuildManager.openingStarportUnits.add(Units.TERRAN_VIKING_FIGHTER);
                BuildManager.openingFactoryUnits.clear();
                BuildManager.openingFactoryUnits.add(Units.TERRAN_CYCLONE);
                break;
            case "54bca4a3-7539-4364-b84b-e918784b488a": //Jensiii
            case "2aa93279-f382-4e26-bfbb-6ef3cc6f9104": //TestBot (jensiiibot)
//                NUM_MARINES = 7;
//                Switches.enemyCanProduceAir = true;
//                BUILD_EXPANDS_IN_MAIN = true;
//                DO_BANSHEE_HARASS = false;
                break;
            case "12c39b76-7830-4c1f-9faa-37c68183396b": //WorthlessBot
                BUILD_EXPANDS_IN_MAIN = true;
                EXPAND_SLOWLY = true;
                break;
            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
            case "10ecc3c36541ead": //RStrelok (LM)
                BUILD_EXPANDS_IN_MAIN = true;
                DO_DIVE_MOBILE_DETECTORS = false;
                ENEMY_DOES_BANSHEE_HARASS = true;
                //Switches.enemyCanProduceAir = true;
                break;
            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
            case "1e0db23f174f455": //MM local
                Harassers.NUM_BAD_HARASS = 1;
                DO_IGNORE_BUNKERS = true;
                Switches.enemyCanProduceAir = true;
//                BUILD_EXPANDS_IN_MAIN = true;
//                DO_DIVE_MOBILE_DETECTORS = false;
                ENEMY_DOES_BANSHEE_HARASS = true;

                break;
            case "4fd044d8-909c-4624-bdf3-0378ea9c5ea1":
                DO_BANSHEE_HARASS = false;
                break;
        }
    }

    private static void setRampWall() {
        if (NO_RAMP_WALL) {
            PosConstants._3x3Structures.add(PosConstants._3x3Structures.remove(0));
            PosConstants._3x3Structures.add(PosConstants._3x3Structures.remove(0));
            PosConstants.extraDepots.add(PosConstants.extraDepots.remove(0));
        }
    }

    private static void chooseTvTStrategy() {
        if (gamePlan == GamePlan.NONE) {
            Set<GamePlan> availableTvTGamePlans = getAvailableTvTGamePlans();
            gamePlan = selectStrategy(availableTvTGamePlans);
        }

        if (gamePlan == GamePlan.NONE) {
            gamePlan = GamePlan.GHOST_HELLBAT;
        }

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
            case HELLBAT_ALL_IN:
                NUM_BASES_TO_OC = 2;
                DO_WALL_NAT = true;
                Strategy.NO_TURRETS = true;
                Strategy.DO_BANSHEE_HARASS = false;
                Strategy.MAX_MARINES = 1;
                break;
            case ONE_BASE_TANK_VIKING:
                useTankVikingAdjustments();
                break;
            case BUNKER_CONTAIN_STRONG:
                BunkerContain.proxyBunkerLevel = 2;
                BUILD_EXPANDS_IN_MAIN = false;
                useTanksAdjustments();
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
                break;
            case MARINE_RUSH:
                marineAllinStrategy();
                break;
        }
    }

    private static HashSet<GamePlan> getAvailableTvTGamePlans() {
        if (Launcher.isRealTime) { // TvT vs Humans
            HashSet<GamePlan> humansGamePlans = new HashSet<>(Set.of(
                    GamePlan.TANK_VIKING,
                    GamePlan.BANSHEE_CYCLONE
            ));
            return humansGamePlans;

//            HashSet<GamePlan> humansGamePlans = new HashSet<>(Set.of(
//                    GamePlan.TANK_VIKING
//            ));
//            if (Math.random() < 0.8) {
//                humansGamePlans.add(GamePlan.BANSHEE);
//            }
//            if (Math.random() < 0.8) {
//                humansGamePlans.add(GamePlan.BANSHEE_CYCLONE);
//            }
//            if (Math.random() < 0.4) {
//                humansGamePlans.add(GamePlan.RAVEN);
//            }
//            if (Math.random() < 0.3) {
//                humansGamePlans.add(GamePlan.RAVEN_CYCLONE);
//            }
//            if (Math.random() < 0.3) {
//                humansGamePlans.add(GamePlan.MARINE_RUSH);
//            }
////            if (Math.random() < 0.3) {
////                humansGamePlans.add(GamePlan.SCV_RUSH);
////            }
//            if (Math.random() < 0.1) {
//                humansGamePlans.add(GamePlan.ONE_BASE_TANK_VIKING);
//            }
//            if (Math.random() < 0.1) {
//                humansGamePlans.add(GamePlan.BANSHEE_TANK);
//            }
//            return humansGamePlans;
        }
        switch (Bot.opponentId) {
//            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
//            case "f50a7f8d4d49792": //RStrelok (LM)
//                return new HashSet<>(Set.of(
//                        GamePlan.TANK_VIKING,
//                        GamePlan.ONE_BASE_TANK_VIKING
//                ));
//            case "5714a116-b8c8-42f5-b8dc-93b28f4adf2d": //Spudde
//                return new HashSet<>(Set.of(
//                        GamePlan.RAVEN_CYCLONE,
//                        GamePlan.TANK_VIKING,
//                        GamePlan.BANSHEE
//                ));
            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
            case "1e0db23f174f455": //MM local
                return new HashSet<>(Set.of(
                        GamePlan.TANK_VIKING,
                        GamePlan.BUNKER_CONTAIN_STRONG
                ));
//            case "4fd044d8-909c-4624-bdf3-0378ea9c5ea1": //VeTerran
//                return new HashSet<>(Set.of(
//                        GamePlan.TANK_VIKING,
//                        GamePlan.MARINE_RUSH
//                ));
            case "3c78e739-5bc8-4b8b-b760-6dca0a88b33b": //Fidolina
            case "8f94d1fd-e5ee-4563-96d1-619c9d81290e": //DominionDog
                return new HashSet<>(Set.of(
                        GamePlan.TANK_VIKING,
                        GamePlan.BANSHEE_CYCLONE
                ));
            default:
                return new HashSet<>(Set.of(
                        //GamePlan.ONE_BASE_TANK_VIKING,
                        GamePlan.BANSHEE_CYCLONE,
                        GamePlan.BANSHEE,
                        //GamePlan.SCV_RUSH,
                        GamePlan.TANK_VIKING,
                        GamePlan.BUNKER_CONTAIN_STRONG
                        //GamePlan.RAVEN,
                        //GamePlan.MARINE_RUSH,
                        //GamePlan.RAVEN_CYCLONE,
                        //GamePlan.BANSHEE_TANK
                ));
        }
    }

    private static HashSet<GamePlan> getAvailableTvPGamePlans() {
        if (Launcher.isRealTime) { // TvP vs Humans
            HashSet<GamePlan> humansGamePlans = new HashSet<>(Set.of(
                    GamePlan.ONE_BASE_BANSHEE_CYCLONE
            ));
            return humansGamePlans;

//            HashSet<GamePlan> humansGamePlans = new HashSet<>(Set.of(
//                    GamePlan.BANSHEE_CYCLONE
//            ));
//            if (Math.random() < 0.8) {
//                humansGamePlans.add(GamePlan.ONE_BASE_BANSHEE_CYCLONE);
//            }
//            if (Math.random() < 0.6) {
//                humansGamePlans.add(GamePlan.BUNKER_CONTAIN_WEAK);
//            }
//            if (Math.random() < 0.5) {
//                humansGamePlans.add(GamePlan.BANSHEE);
//            }
//            if (Math.random() < 0.5) {
//                humansGamePlans.add(GamePlan.MARINE_RUSH);
//            }
//            if (Math.random() < 0.5) {
//                humansGamePlans.add(GamePlan.RAVEN_CYCLONE);
//            }
//            if (Math.random() < 0.3) {
//                humansGamePlans.add(GamePlan.RAVEN);
//            }
////            if (Math.random() < 0.5) {
////                humansGamePlans.add(GamePlan.SCV_RUSH);
////            }
//            return humansGamePlans;
        }
        switch (Bot.opponentId) {
            case "71089047-c9cc-42f9-8657-8bafa0df89a0": //NegativeZero
                return new HashSet<>(Set.of(
                        GamePlan.BC_RUSH,
                        GamePlan.BUNKER_CONTAIN_STRONG,
                        GamePlan.MECH_ALL_IN
//                        GamePlan.BANSHEE,
//                        GamePlan.BANSHEE_CYCLONE,
//                        GamePlan.ONE_BASE_BANSHEE_CYCLONE,
//                        GamePlan.MARINE_RUSH,
////                        GamePlan.SCV_RUSH,
//                        GamePlan.BUNKER_CONTAIN_WEAK,
//                        GamePlan.RAVEN
                ));
            default:
                return new HashSet<>(Set.of(
                        GamePlan.BANSHEE,
                        GamePlan.BANSHEE_CYCLONE,
                        GamePlan.ONE_BASE_BANSHEE_CYCLONE,
                        GamePlan.MARINE_RUSH,
//                        GamePlan.SCV_RUSH,
                        GamePlan.BUNKER_CONTAIN_WEAK,
                        GamePlan.BUNKER_CONTAIN_STRONG,
                        GamePlan.MECH_ALL_IN,
                        GamePlan.RAVEN
                ));
        }
    }

    private static HashSet<GamePlan> getAvailableTvZGamePlans() {
        if (Launcher.isRealTime) { // TvZ vs Humans
            HashSet<GamePlan> humansGamePlans = new HashSet<>(Set.of(
                    GamePlan.BANSHEE_CYCLONE,
                    GamePlan.MASS_MINE_OPENER
            ));
            return humansGamePlans;

//            HashSet<GamePlan> humansGamePlans = new HashSet<>(Set.of(
//                    GamePlan.BANSHEE,
//                    GamePlan.GHOST_HELLBAT,
//                    GamePlan.MASS_MINE_OPENER
//            ));
//            if (Math.random() < 0.5) {
//                humansGamePlans.add(GamePlan.MARINE_RUSH);
//            }
//            if (Math.random() < 0.5) {
//                humansGamePlans.add(GamePlan.RAVEN);
//            }
////            if (Math.random() < 0.5) {
////                humansGamePlans.add(GamePlan.RAVEN_CYCLONE);
////            }
//            if (Math.random() < 0.5) {
//                humansGamePlans.add(GamePlan.BUNKER_CONTAIN_WEAK);
//            }
////            if (Math.random() < 0.5) {
////                humansGamePlans.add(GamePlan.SCV_RUSH);
////            }
//            return humansGamePlans;
        }
        switch (Bot.opponentId) {
            case "6bcce16a-8139-4dc0-8e72-b7ee8b3da1d8": //Eris
            case "5b5220da-cc18-4c2e-acdf-68752a3701c3": //ErisTest
            return new HashSet<>(Set.of(
//                    GamePlan.BANSHEE,
//                    GamePlan.MASS_MINE_OPENER,
//                    GamePlan.BC_RUSH,
//                    GamePlan.MARINE_RUSH,
//                    GamePlan.SCV_RUSH,
//                    GamePlan.BUNKER_CONTAIN_WEAK,
//                    GamePlan.RAVEN_CYCLONE,
//                    GamePlan.GHOST_HELLBAT
                    GamePlan.BANSHEE_CYCLONE,
                    GamePlan.RAVEN,
                    GamePlan.BC_RUSH
            ));
            case "9cfcf297-5345-4987-a9f4-87162ebfa6b9": //EvilZoe
            case "841b33a8-e530-40f5-8778-4a2f8716095d": //Zoe
                return new HashSet<>(Set.of(
//                        GamePlan.BANSHEE,
//                        GamePlan.MASS_MINE_OPENER,
                        GamePlan.BC_RUSH
//                        GamePlan.BANSHEE_CYCLONE,
//                        GamePlan.MARINE_RUSH,
//                        //GamePlan.SCV_RUSH,
//                        GamePlan.BUNKER_CONTAIN_WEAK,
//                        GamePlan.RAVEN,
//                        GamePlan.RAVEN_CYCLONE,
//                        GamePlan.GHOST_HELLBAT
                ));
//            case "5e14c537-b8e7-4cd8-8aa4-1d6fcdb376cd": //Dovahkiin
//                return new HashSet<>(Set.of(
//                        GamePlan.BANSHEE
//                ));
            default:
                return new HashSet<>(Set.of(
                        GamePlan.BANSHEE,
                        GamePlan.MASS_MINE_OPENER,
                        GamePlan.BC_RUSH,
                        GamePlan.BANSHEE_CYCLONE,
                        GamePlan.MARINE_RUSH,
//                        GamePlan.SCV_RUSH,
                        GamePlan.BUNKER_CONTAIN_WEAK,
                        GamePlan.RAVEN,
                        GamePlan.RAVEN_CYCLONE,
                        GamePlan.GHOST_HELLBAT,
                        GamePlan.HELLBAT_ALL_IN
                ));
        }
    }

    public static GamePlan getNextGamePlan(GamePlan curPlan) {
        int nextIndex = (availableGamePlans.indexOf(curPlan) + 1) % availableGamePlans.size();
        return availableGamePlans.get(nextIndex);
    }

    private static void marineAllinStrategy() {
        MARINE_ALLIN = true;
        MAX_MARINES = 80; //Too many can cause the high-APM bug
    }

    private static void chooseTvPStrategy() {
        if (gamePlan == GamePlan.NONE) {
            Set<GamePlan> availableTvPGamePlans = getAvailableTvPGamePlans();
            gamePlan = selectStrategy(availableTvPGamePlans);
        }

        if (gamePlan == GamePlan.NONE) {
            gamePlan = GamePlan.BANSHEE;
        }

        switch (gamePlan) {
            case BANSHEE:
                DO_DEFENSIVE_TANKS = true;
                break;
            case BANSHEE_CYCLONE:
                useCyclonesAdjustments();
                UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.mechThenAirUpgrades);
                BuildManager.openingFactoryUnits.add(Units.TERRAN_SIEGE_TANK);
                MAX_MARINES = 4;
                NUM_BASES_TO_OC = 2;
                break;
            case BC_RUSH:
                NUM_BASES_TO_OC = PosConstants.baseLocations.size();
                DO_WALL_NAT = true;
                NO_UPGRADES = true;
                Strategy.techBuilt = true;
                Strategy.NO_TURRETS = true;
                Strategy.DO_BANSHEE_HARASS = false;
                break;
            case ONE_BASE_BANSHEE_CYCLONE:
                useCyclonesAdjustments();
                UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.mechThenAirUpgrades);
                MAX_MARINES = 4;
                break;
            case BUNKER_CONTAIN_WEAK:
                BunkerContain.proxyBunkerLevel = 1;
                break;
            case BUNKER_CONTAIN_STRONG:
                BunkerContain.proxyBunkerLevel = 2;
                DO_USE_CYCLONES = false;
                DO_OFFENSIVE_TANKS = true;
                BUILD_EXPANDS_IN_MAIN = false;
                Strategy.DO_USE_HELLIONS = false;
                break;
            case SCV_RUSH:
                Switches.scvRushComplete = false;
                break;
            case HELLBAT_ALL_IN:
                NUM_BASES_TO_OC = 2;
                DO_WALL_NAT = true;
                Strategy.NO_TURRETS = true;
                Strategy.DO_BANSHEE_HARASS = false;
                Strategy.MAX_MARINES = 1;
                break;
            case RAVEN:
                massRavenStrategy();
                break;
//            case MASS_RAVEN_WITH_CYCLONES:
//                massRavenStrategy();
//                useCyclonesAdjustments();
//                break;
            case MARINE_RUSH:
                marineAllinStrategy();
                break;
            case MECH_ALL_IN:
                DO_OFFENSIVE_TANKS = true;
                UpgradeManager.armoryUpgradeList = new ArrayList<>();
                UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1);
                break;
        }
    }

    private static void chooseTvZStrategy() {
        System.out.println("in chooseTvZStrategy");
        if (gamePlan == GamePlan.NONE) {
            Set<GamePlan> availableTvZGamePlans = getAvailableTvZGamePlans();
            gamePlan = selectStrategy(availableTvZGamePlans);
        }

        if (gamePlan == GamePlan.NONE) {
            gamePlan = GamePlan.GHOST_HELLBAT;
        }

        switch (gamePlan) {
            case MASS_MINE_OPENER:
                MASS_MINE_OPENER = true;
                //no break
            case BANSHEE_CYCLONE:
                useCyclonesAdjustments();
                UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.mechThenAirUpgrades);
                MAX_MARINES = 2;
                NUM_BASES_TO_OC = 2;
                BUILD_EXPANDS_IN_MAIN = true;
                PRIORITIZE_EXPANDING = true;
                DO_BANSHEE_HARASS = false; //TODO: remove me
                break;
            case BC_RUSH:
                NUM_BASES_TO_OC = PosConstants.baseLocations.size();
                DO_WALL_NAT = true;
                NO_UPGRADES = true;
                Strategy.techBuilt = true;
                Strategy.NO_TURRETS = true;
                Strategy.DO_BANSHEE_HARASS = false;
                break;
            case HELLBAT_ALL_IN:
                NUM_BASES_TO_OC = 2;
                DO_WALL_NAT = true;
                Strategy.NO_TURRETS = true;
                Strategy.DO_BANSHEE_HARASS = false;
                Strategy.MAX_MARINES = 1;
                break;
            case BANSHEE:
                break;
            case BUNKER_CONTAIN_WEAK:
                BunkerContain.proxyBunkerLevel = 1;
                break;
            case SCV_RUSH:
                Switches.scvRushComplete = false;
                Chat.chatNeverRepeat("How's this for Evil?");
                Chat.chatNeverRepeat("Can Terran even do this?");
                break;
            case RAVEN:
                massRavenStrategy();
                break;
            case GHOST_HELLBAT:
                UpgradeManager.armoryUpgradeList = new ArrayList<>(
                        List.of(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1,
                                Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
                                Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
                                Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3,
                                Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL2,
                                Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL3)
                );
                UpgradeManager.engBayUpgradeList = new ArrayList<>(UpgradeManager.bioAttackThenArmorUpgrades);
                UpgradeManager.engBayUpgradeList.addAll(UpgradeManager.structureUpgrades);
                BUILD_EXPANDS_IN_MAIN = true;
                NUM_BASES_TO_OC = 2;
                MAX_MARINES = 0;
                break;
            case RAVEN_CYCLONE:
                useCyclonesAdjustments();
                DO_BANSHEE_HARASS = false;
                DEFAULT_STARPORT_UNIT = Abilities.TRAIN_RAVEN;
                UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.airThenMechUpgrades);
                NUM_BASES_TO_OC = 2;
                BUILD_EXPANDS_IN_MAIN = false;
                PRIORITIZE_EXPANDING = true;
                break;
            case MARINE_RUSH:
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
//            case "496ce221-f561-42c3-af4b-d3da4490c46e": //RStrelok
//            case "10ecc3c36541ead": //RStrelok (LM)
//                return GamePlan.BANSHEE;
//            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
//            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
//            case "1e0db23f174f455": //MM local
//                return GamePlan.BANSHEE;
            default:
                return gamePlan;
        }
    }

    private static void massRavenStrategy() {
        MASS_RAVENS = true;
        UpgradeManager.starportUpgradeList = new ArrayList<>(List.of(Upgrades.RAVEN_CORVID_REACTOR));

        //get 2 banshees and +1attack for creep clearing and early defense
        if (PosConstants.opponentRace == Race.ZERG && !DO_USE_CYCLONES && !DO_OFFENSIVE_TANKS) {
            MIN_BANSHEES = 2;
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

        DO_BANSHEE_HARASS = false;
        DO_DEFENSIVE_LIBS = true;
        DO_DEFENSIVE_TANKS = true;
        EXPAND_SLOWLY = false;
        PRIORITIZE_EXPANDING = true;
        DO_SEEKER_MISSILE = false;
        RETREAT_HEALTH = 50;
        DEFAULT_STARPORT_UNIT = Abilities.TRAIN_RAVEN;
    }

    private static boolean wereAllStrategiesUsed(GamePlan[] strategies, String fileText) {
        for (GamePlan strategy : strategies) {
            if (!fileText.contains("~" + strategy + "~")) {
                return false;
            }
        }
        return true;
    }

    private static Set<GamePlan> getTournamentStrategyOrder() {
        switch (KetrocBot.opponentId) {
            case "6bcce16a-8139-4dc0-8e72-b7ee8b3da1d8": //Eris
            case "5b5220da-cc18-4c2e-acdf-68752a3701c3": //ErisTest
                return new HashSet<>(Set.of(GamePlan.RAVEN));
            case "841b33a8-e530-40f5-8778-4a2f8716095d": //Zoe
                return new HashSet<>(Set.of(GamePlan.BANSHEE_CYCLONE, GamePlan.GHOST_HELLBAT, GamePlan.MASS_MINE_OPENER));
//            case "71089047-c9cc-42f9-8657-8bafa0df89a0": //NegativeZero
//                return new HashSet<>(Set.of(GamePlan.BUNKER_CONTAIN_STRONG));
            case "81fa0acc-93ea-479c-9ba5-08ae63b9e3f5": //Micromachine
            case "ff9d6962-5b31-4dd0-9352-c8a157117dde": //MMTest
            case "1e0db23f174f455": //MM local
                return new HashSet<>(Set.of(GamePlan.TANK_VIKING, GamePlan.BUNKER_CONTAIN_STRONG));
//            case "5e14c537-b8e7-4cd8-8aa4-1d6fcdb376cd": //Dovahkiin
//                return new GamePlan[] { GamePlan.GHOST_HELLBAT, GamePlan.BANSHEE ));
            case "8f94d1fd-e5ee-4563-96d1-619c9d81290e": //DominionDog
                return new HashSet<>(Set.of(GamePlan.BANSHEE_CYCLONE));
        }
        return new HashSet<>();
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

    public static void onStep_Faststart() {
        switch (step_TvtFastStart) {
            case 1:
                //rally cc to the depot pos
                ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, PosConstants.extraDepots.get(0), false);
                step_TvtFastStart++;
                break;

            case 2:
                if (Bot.OBS.getMinerals() >= 70) {
                    //manually produce 2nd scv
                    ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.TRAIN_SCV, false);
                    step_TvtFastStart++;
                    break;
                }

            case 3:
                if (Bot.OBS.getFoodWorkers() == 13) {
                    //rally cc to mineral node
                    ActionHelper.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER,
                            GameCache.baseList.get(0).getFullestMineralPatch(), false);

                    //add depot and rax with first scv to the production queue
                    scv_TvtFastStart = Bot.OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV).stream()
                            .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), PosConstants.extraDepots.get(0))))
                            .get();
                    PurchaseStructure.getPurchase(Units.TERRAN_SUPPLY_DEPOT)
                            .ifPresent(purchase -> purchase.setScv(scv_TvtFastStart.unit()));
                    PurchaseStructure.getPurchase(Units.TERRAN_BARRACKS)
                            .ifPresent(purchase -> purchase.setScv(scv_TvtFastStart.unit()));
                    Switches.fastDepotBarracksOpener = false;
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
            return 19;
        }

        //marine all-in without an expansion
        if (gamePlan == GamePlan.HELLBAT_ALL_IN) {
            int numBases = 2 + (GameCache.baseList.stream().anyMatch(Base::isPocketBase) ? 1 : 0);
            return (int)GameCache.baseList.stream()
                    .limit(numBases)
                    .flatMap(base -> base.getMineralPatches().stream())
                    .count() * 2 + 3; //3 extra for building depots
        }

        //marine all-in without an expansion
        if (gamePlan == GamePlan.MECH_ALL_IN && !GameCache.baseList.get(1).isMyBase()) {
            return GameCache.baseList.get(0).getMineralPatches().size()*2 + 6 + NUM_OFFENSE_SCVS + 1;
        }

        //if maxed out on macro OCs
        if (UnitUtils.numMyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, false) > 6 && GameCache.mineralBank > 3000) {
            return 50;
        }

        return 90;
    }

    public static void setRaceStrategies() {
        switch (PosConstants.opponentRace) {
            case ZERG:
                DO_DEFENSIVE_LIBS = false;
                DO_DEFENSIVE_TANKS = false;
                MAX_OCS = 25;
                break;
            case PROTOSS:
                DIVE_RANGE = 25;
                DO_DEFENSIVE_LIBS = false;
                DO_DEFENSIVE_TANKS = false;
                MAX_MARINES = 5;
                break;
            case TERRAN:
                DO_DIVE_MOBILE_DETECTORS = false;
                DO_DEFENSIVE_LIBS = false;
                DO_DEFENSIVE_TANKS = false;
                MAX_MARINES = 4;
                break;
        }
    }

    public static void useCyclonesAdjustments() {
        MAX_MARINES = Math.min(2, MAX_MARINES);
        MIN_BANSHEES = 0;
        DO_USE_CYCLONES = true;
        DO_DEFENSIVE_TANKS = false;
    }

    public static void useTanksAdjustments() {
        UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.airThenMechUpgrades);
        MAX_MARINES = Math.min(3, MAX_MARINES);
        MIN_BANSHEES = 0;
        DO_OFFENSIVE_TANKS = true;
        NUM_BASES_TO_OC = 3;
    }

    public static void useTankVikingAdjustments() {
        UpgradeManager.armoryUpgradeList = new ArrayList<>(UpgradeManager.airThenMechUpgrades);

        MAX_MARINES = 4;
        DO_OFFENSIVE_TANKS = true;
        DO_SEEKER_MISSILE = true;
        MIN_BANSHEES = 0;
        MAX_BANSHEES = 4;
        NUM_BASES_TO_OC = 3;
        BuildManager.openingStarportUnits.add(Units.TERRAN_BANSHEE);
        BuildManager.openingStarportUnits.add(Units.TERRAN_VIKING_FIGHTER);
        BuildManager.openingStarportUnits.add(Units.TERRAN_RAVEN);
        BuildManager.openingStarportUnits.add(Units.TERRAN_VIKING_FIGHTER);
    }

    private static void setReaperBlockWall() {
        if (PosConstants.opponentRace == Race.TERRAN && gamePlan != GamePlan.MARINE_RUSH) {
            PosConstants.extraDepots.addAll(0, PosConstants.reaperBlockDepots);
            PosConstants._3x3Structures.addAll(0, PosConstants.reaperBlock3x3s);

            //decide if middle structure in wall is a depot or barracks
            if (!PosConstants.reaperBlock3x3s.isEmpty()) {
                PosConstants._3x3Structures.remove(PosConstants.MID_WALL_3x3);
            }
            else {
                PosConstants.extraDepots.remove(PosConstants.MID_WALL_2x2);
            }
        }
        else {
            if (gamePlan == GamePlan.ONE_BASE_BANSHEE_CYCLONE ||
                    gamePlan == GamePlan.MARINE_RUSH ||
                    gamePlan == GamePlan.MECH_ALL_IN ||
                    Strategy.NUM_BASES_TO_OC > 1) {
                PosConstants._3x3Structures.remove(PosConstants.MID_WALL_3x3);
            }
            else {
                PosConstants.extraDepots.remove(PosConstants.MID_WALL_2x2);
            }
        }
    }

    private static void setNatWall() {
        if (DO_WALL_NAT) {
            PosConstants.extraDepots.addAll(0, PosConstants.natWallDepots);
            PosConstants._3x3Structures.addAll(0, PosConstants.natWall3x3s);
        }
    }

    public static void printStrategySettings() {
        Print.print("selectedStrategy = " + gamePlan);
        Print.print("DO_DIVE_MOBILE_DETECTORS = " + DO_DIVE_MOBILE_DETECTORS);
        Print.print("EARLY_BANSHEE_SPEED = " + EARLY_BANSHEE_SPEED);
        Print.print("DO_LEAVE_UP_BUNKER = " + DO_LEAVE_UP_BUNKER);
        Print.print("NO_TURRETS = " + NO_TURRETS);

        Print.print("DO_INCLUDE_TANKS = " + DO_DEFENSIVE_TANKS);
        Print.print("MAX_TANKS = " + MAX_TANKS);

        Print.print("DO_INCLUDE_LIBS = " + DO_DEFENSIVE_LIBS);
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

    public static GamePlan selectStrategy(Set<GamePlan> gamePlans) {
        if (RANDOM_STRATEGY_SELECTION || Launcher.isRealTime) {
            return gamePlans.stream()
                    .skip(new Random().nextInt(gamePlans.size()))
                    .findFirst()
                    .get();
        }
        Opponent opponentRecords = JsonUtil.getOpponentRecords();
        opponentRecords.filterToGamePlans(gamePlans);

        //play 4 games of each strategy first
        GamePlan gamePlan = opponentRecords.getGamePlanNeedingMoreTests(4);

        //pick the winningest strategy (exclude most recent loss strategy)
        if (gamePlan == GamePlan.NONE) {
            gamePlan = opponentRecords.getWinningestGamePlan();
        }

        //don't lose to worker rush twice
        if (true) { // TOURNAMENT_MODE) {
            GameResult prevGameResult = opponentRecords.getPrevGameResult();
            if (prevGameResult != null && prevGameResult.getTags().stream().anyMatch(t -> t.endsWith("VS_WORKER_RUSH"))) {
                System.out.println("setting fast wall code");
                Strategy.BUILD_EXPANDS_IN_MAIN = true;
                Strategy.WALL_OFF_IMMEDIATELY = true;
                DelayedChat.add(120, "*Sniff* *Sniff*... Does this smell like last game?  Let me play it safe.");
            }
        }
        return gamePlan;
    }

    //plays each strategy once NOTE: make sure data is empty for this bot
    public static GamePlan getTournamentGamePlan() {
        Set<GamePlan> gamePlans = getTournamentStrategyOrder();
        if (gamePlans.isEmpty()) {
            return GamePlan.NONE;
        }

        Opponent opponentRecords = JsonUtil.getOpponentRecords();
        opponentRecords.filterToGamePlans(gamePlans);

        //return an unused gameplan
        GamePlan gamePlan = opponentRecords.getGamePlanNeedingMoreTests(1);
        if (gamePlan != GamePlan.NONE) {
            return gamePlan;
        }

        //or else return the winningest gameplan
        return opponentRecords.getWinningestGamePlan();
    }
}
