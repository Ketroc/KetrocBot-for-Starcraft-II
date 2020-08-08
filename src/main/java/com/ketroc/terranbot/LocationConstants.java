package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.TriangleOfNodes;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocationConstants {
    public static final Point2d SCREEN_BOTTOM_LEFT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP0().toPoint2d();
    public static final Point2d SCREEN_TOP_RIGHT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP1().toPoint2d();
    public static final int MAX_X = (int)SCREEN_TOP_RIGHT.getX();
    public static final int MAX_Y = (int)SCREEN_TOP_RIGHT.getY();
    public static Point2d insideMainWall;

    public static boolean isTopSpawn;
    public static String MAP;
    public static int numReaperWall;
    public static TriangleOfNodes myMineralTriangle;
    public static TriangleOfNodes enemyMineralTriangle;
    public static Point2d myMineralPos;
    public static Point2d enemyMineralPos;
    public static Point2d REPAIR_BAY;
    public static Point2d REAPER_JUMP2;
    public static Point2d BUNKER_NATURAL;
    public static Point2d FACTORY;
    public static Point2d WALL_2x2;
    public static Point2d WALL_3x3;
    public static Point2d MID_WALL_3x3;
    public static Point2d MID_WALL_2x2;
    public static List<Point2d> _3x3Structures = new ArrayList<>(); //barracks, engbay, and armory x2
    public static List<Point2d> extraDepots = new ArrayList<>();
    public static List<Point2d> STARPORTS = new ArrayList<>();
    public static List<Point2d> TURRETS = new ArrayList<>();
    public static List<Point2d> MACRO_OCS = new ArrayList<>();

    public static List<Point2d> baseLocations = new ArrayList<>();

    public static int baseAttackIndex = 2;
    public static Race opponentRace;

    public static void onStep() {
        if (Bot.OBS.getGameLoop() % 6720 == 0 && Base.numMyBases() >= 4) { //every ~5min
            baseAttackIndex = Math.max(2, getNewEnemyBaseIndex() - 2);
            skipBasesIOwn();
        }
    }

    //set baseAttackIndex to 2 past the newest enemy base I know about
    private static int getNewEnemyBaseIndex() {
        if (UnitUtils.enemyCommandStructures != null) { //if not an unscouted random player
            for (int i=0; i<baseLocations.size(); i++) {
                Point2d basePos = baseLocations.get(i);
                for (Units unitType : UnitUtils.enemyCommandStructures) { //loop through different enemy command structures
                    List<UnitInPool> enemyCommandStructures = GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList());
                    if (enemyCommandStructures.stream().anyMatch(enemyBase -> UnitUtils.getDistance(enemyBase.unit(), basePos) < 1)) {
                        return i;
                    }
                }
            }
        }
        return 1;
    }

    public static void rotateBaseAttackIndex() {
        if (baseAttackIndex == baseLocations.size()-1) {
            Switches.finishHim = true;
            Bot.ACTION.sendChat("Finish Him!", ActionChat.Channel.BROADCAST);
        }
        else {
            baseAttackIndex++;
        }
        skipBasesIOwn();
    }
    private static void skipBasesIOwn() {
        //skip bases where I have started a CC at
        while (baseAttackIndex < baseLocations.size()-1 &&
                UnitUtils.isUnitTypesNearby(Alliance.SELF, UnitUtils.COMMAND_CENTER_TYPE, baseLocations.get(baseAttackIndex), 1)) {
            baseAttackIndex++;
        }
    }

    public static void init(UnitInPool mainCC) {
        if (MAP.equals(MapNames.GOLDEN_WALL)) { //isTopSpawn == the left spawn for this map
            isTopSpawn = mainCC.unit().getPosition().getX() < 100;
        }
        else {
            isTopSpawn = mainCC.unit().getPosition().getY() > 100;
        }
        setStructureLocations();
        setBaseLocations();
        createBaseList(mainCC);
        insideMainWall = Position.towards(MID_WALL_3x3, baseLocations.get(0), 2.5f);
        initEnemyRaceSpecifics();

        //set probe rush mineral node
        enemyMineralTriangle = new TriangleOfNodes(enemyMineralPos);
        myMineralTriangle = new TriangleOfNodes(myMineralPos);
    }

    public static void setRepairBayLocation() {
        Base mainBase = GameCache.baseList.get(0);
        //REPAIR_BAY = Position.rotate(mainBase.getResourceMidPoint(), mainBase.getCcPos(), 20); //use this if making turret on this position in main
        REPAIR_BAY = mainBase.getResourceMidPoint();


        //initialize retreat/attack point to repair bay location
        ArmyManager.retreatPos = ArmyManager.attackPos = REPAIR_BAY;
    }

    private static void createBaseList(UnitInPool mainCC) {
        for (Point2d baseLocation : baseLocations) {
            GameCache.baseList.add(new Base(baseLocation));
        }
        GameCache.baseList.get(0).setCc(mainCC);
    }

    public static void setRaceStrategies() {
        switch (opponentRace) {
            case ZERG:
                Strategy.DO_INCLUDE_LIBS = true;
                Strategy.DO_INCLUDE_TANKS = true;
                break;
            case PROTOSS:
                Strategy.DIVE_RANGE = 25;
                Strategy.DO_INCLUDE_LIBS = true;
                Strategy.DO_INCLUDE_TANKS = true;
                break;
            case TERRAN:
                Strategy.DO_INCLUDE_LIBS = false;
                Strategy.DO_INCLUDE_TANKS = false;
                break;
        }
    }

    public static void initEnemyRaceSpecifics() {
        setEnemyTypes();
        setRaceStrategies();
    }

    public static boolean setEnemyTypes() {
        switch (opponentRace) {
            case TERRAN:
                UnitUtils.enemyCommandStructures = new ArrayList<>(List.of(Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING));
                UnitUtils.enemyWorkerType = Units.TERRAN_SCV;
                return true;
            case PROTOSS:
                UnitUtils.enemyCommandStructures = new ArrayList<>(List.of(Units.PROTOSS_NEXUS));
                UnitUtils.enemyWorkerType = Units.PROTOSS_PROBE;
                return true;
            case ZERG:
                UnitUtils.enemyCommandStructures = new ArrayList<>(List.of(Units.ZERG_HATCHERY, Units.ZERG_LAIR, Units.ZERG_HIVE));
                UnitUtils.enemyWorkerType = Units.ZERG_DRONE;
                return true;
            default: //case RANDOM:
                return false;
        }
    }

    private static void setStructureLocations() {
        switch (MAP) {
            case MapNames.ACROPOLIS:
                setLocationsForAcropolis(isTopSpawn);
                break;
            case MapNames.DEATH_AURA:
                setLocationsForDeathAura(isTopSpawn);
                break;
            case MapNames.DISCO_BLOODBATH:
                setLocationsForDiscoBloodBath(isTopSpawn);
                break;
            case MapNames.EPHEMERON: case MapNames.EPHEMERONLE:
                setLocationsForEphemeron(isTopSpawn);
                break;
            case MapNames.ETERNAL_EMPIRE:
                setLocationsForEternalEmpire(isTopSpawn);
                break;
            case MapNames.EVER_DREAM:
                setLocationsForEverDream(isTopSpawn);
                break;
            case MapNames.GOLDEN_WALL:
                setLocationsForGoldenWall(isTopSpawn);
                break;
            case MapNames.ICE_AND_CHROME:
                setLocationsForIceAndChrome(isTopSpawn);
                break;
            case MapNames.NIGHTSHADE:
                setLocationsForNightshade(isTopSpawn);
                break;
            case MapNames.PILLARS_OF_GOLD:
                setLocationsForPillarsOfGold(isTopSpawn);
                break;
            case MapNames.SIMULACRUM:
                setLocationsForSimulacrum(isTopSpawn);
                break;
            case MapNames.SUBMARINE:
                setLocationsForSubmarine(isTopSpawn);
                break;
            case MapNames.THUNDERBIRD:
                setLocationsForThunderBird(isTopSpawn);
                break;
            case MapNames.TRITON:
                setLocationsForTriton(isTopSpawn);
                break;
            case MapNames.WINTERS_GATE:
                setLocationsForWintersGate(isTopSpawn);
                break;
            case MapNames.WORLD_OF_SLEEPERS:
                setLocationsForWorldOfSleepers(isTopSpawn);
                break;
            case MapNames.ZEN:
                setLocationsForZen(isTopSpawn);
                break;
        }
        if (Strategy.ANTI_DROP_TURRET) { //replace first turret position with last depot position
            TURRETS.add(0, extraDepots.remove(extraDepots.size()-1));
            TURRETS.remove(2);
        }
    }

    private static void setLocationsForAcropolis(boolean isTopPos) {
        numReaperWall = 1;
        if (isTopPos) {
            myMineralPos = Point2d.of(33.0f, 145.5f);
            enemyMineralPos = Point2d.of(143f, 26.5f);
            //REPAIR_BAY = Point2d.of(38.0f, 127.0f);

            WALL_2x2 = Point2d.of(40.0f, 125.0f);
            MID_WALL_3x3 = Point2d.of(42.5f, 125.5f);
            WALL_3x3 = Point2d.of(43.5f, 122.5f);

            _3x3Structures.add(MID_WALL_3x3); //midwall 3x3
            _3x3Structures.add(WALL_3x3); //wall3x3
            _3x3Structures.add(Point2d.of(41.5f, 144.5f));
            _3x3Structures.add(Point2d.of(22.5f, 135.5f));

            //REAPER_JUMP1 = Point2d.of(52.0f, 132.0f); //moved to top of extraDepots
            BUNKER_NATURAL = Point2d.of(36.5f, 105.5f);

            STARPORTS.add(Point2d.of(22.5f, 138.5f));
            STARPORTS.add(Point2d.of(29.5f, 149.5f));
            STARPORTS.add(Point2d.of(26.5f, 146.5f));
            STARPORTS.add(Point2d.of(35.5f, 148.5f));
            STARPORTS.add(Point2d.of(40.5f, 148.5f));
            STARPORTS.add(Point2d.of(22.5f, 132.5f));
            STARPORTS.add(Point2d.of(25.5f, 128.5f));
            STARPORTS.add(Point2d.of(30.5f, 125.5f));
            STARPORTS.add(Point2d.of(28.5f, 131.5f));
            STARPORTS.add(Point2d.of(30.5f, 134.5f));
            STARPORTS.add(Point2d.of(41.5f, 129.5f));
            STARPORTS.add(Point2d.of(47.5f, 130.5f));
            STARPORTS.add(Point2d.of(24.5f, 120.5f));
            STARPORTS.add(Point2d.of(20.5f, 113.5f));
            STARPORTS.add(Point2d.of(33.5f, 127.5f));
            STARPORTS.add(Point2d.of(22.5f, 141.5f));

            TURRETS.add(Point2d.of(38.0f, 143.0f));
            TURRETS.add(Point2d.of(29.0f, 138.0f));
            TURRETS.add(Point2d.of(31.0f, 146.0f));

            MACRO_OCS.add(Point2d.of(38.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(43.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(48.5f, 139.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 144.5f));
            MACRO_OCS.add(Point2d.of(42.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(37.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(48.5f, 134.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 125.5f));

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(52.0f, 132.0f)); //reaperJump1
            extraDepots.add(Point2d.of(23.0f, 144.0f));
            extraDepots.add(Point2d.of(25.0f, 144.0f));
            extraDepots.add(Point2d.of(33.0f, 147.0f));
            extraDepots.add(Point2d.of(35.0f, 146.0f));
            extraDepots.add(Point2d.of(42.0f, 142.0f));
            extraDepots.add(Point2d.of(40.0f, 142.0f));
            extraDepots.add(Point2d.of(23.0f, 130.0f));
            extraDepots.add(Point2d.of(27.0f, 126.0f));
            extraDepots.add(Point2d.of(33.0f, 132.0f));
            extraDepots.add(Point2d.of(31.0f, 128.0f));
            extraDepots.add(Point2d.of(33.0f, 130.0f));
            extraDepots.add(Point2d.of(36.0f, 129.0f));
            extraDepots.add(Point2d.of(49.0f, 143.0f));
        }
        else {
            myMineralPos = Point2d.of(143f, 26.5f);
            enemyMineralPos = Point2d.of(33.0f, 145.5f);

            //REPAIR_BAY = Point2d.of(129f, 49.5f);
            WALL_2x2 = Point2d.of(136.0f, 47.0f);
            MID_WALL_3x3 = Point2d.of(133.5f, 46.5f);
            WALL_3x3 = Point2d.of(132.5f, 49.5f);
            _3x3Structures.add(MID_WALL_3x3); //midwall 3x3
            _3x3Structures.add(WALL_3x3); //wall3x3
            _3x3Structures.add(Point2d.of(153.5f, 35.5f));
            _3x3Structures.add(Point2d.of(128.5f, 27.5f));


            //REAPER_JUMP1 = Point2d.of(124.0f, 40.0f);
            BUNKER_NATURAL = Point2d.of(139.5f, 67.5f);

            STARPORTS.add(Point2d.of(133.5f, 24.5f));
            STARPORTS.add(Point2d.of(132.5f, 27.5f));
            STARPORTS.add(Point2d.of(139.5f, 23.5f));
            STARPORTS.add(Point2d.of(144.5f, 23.5f));
            STARPORTS.add(Point2d.of(147.5f, 26.5f));
            STARPORTS.add(Point2d.of(151.5f, 30.5f));
            STARPORTS.add(Point2d.of(144.5f, 37.5f));
            STARPORTS.add(Point2d.of(144.5f, 40.5f));
            STARPORTS.add(Point2d.of(150.5f, 39.5f));
            STARPORTS.add(Point2d.of(149.5f, 43.5f));
            STARPORTS.add(Point2d.of(124.5f, 37.5f));
            STARPORTS.add(Point2d.of(126.5f, 40.5f));
            STARPORTS.add(Point2d.of(127.5f, 43.5f));
            STARPORTS.add(Point2d.of(127.5f, 46.5f));
            STARPORTS.add(Point2d.of(133.5f, 42.5f));
            STARPORTS.add(Point2d.of(149.5f, 51.5f));

            TURRETS.add(Point2d.of(138.0f, 29.0f));
            TURRETS.add(Point2d.of(147.0f, 34.0f));
            TURRETS.add(Point2d.of(152.0f, 28.0f));

            MACRO_OCS.add(Point2d.of(137.5f, 33.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(127.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 38.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 37.5f));
            MACRO_OCS.add(Point2d.of(144.5f, 45.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 43.5f));

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(124.0f, 40.0f)); //reaperJump1
            extraDepots.add(Point2d.of(141.0f, 26.0f));
            extraDepots.add(Point2d.of(137.0f, 22.0f));
            extraDepots.add(Point2d.of(135.0f, 22.0f));
            extraDepots.add(Point2d.of(135.0f, 29.0f));
            extraDepots.add(Point2d.of(145.0f, 26.0f));
            extraDepots.add(Point2d.of(150.0f, 28.0f));
            extraDepots.add(Point2d.of(129.0f, 36.0f));
            extraDepots.add(Point2d.of(129.0f, 38.0f));
            extraDepots.add(Point2d.of(131.0f, 41.0f));
            extraDepots.add(Point2d.of(142.0f, 37.0f));
            extraDepots.add(Point2d.of(142.0f, 39.0f));
            extraDepots.add(Point2d.of(136.0f, 44.0f));
            extraDepots.add(Point2d.of(154.0f, 41.0f));
            extraDepots.add(Point2d.of(149.0f, 46.0f));
            extraDepots.add(Point2d.of(126.0f, 29.0f));
        }
    }

    private static void setLocationsForDeathAura(boolean isTopPos) {
        numReaperWall = 3;  //TODO: handle eng bay as reaperjump3
        if (isTopPos) {
            myMineralPos = Point2d.of(36.5f, 146.5f);
            enemyMineralPos = Point2d.of(155.5f, 42.5f);

            WALL_2x2 = Point2d.of(47.0f, 140.0f);
            WALL_3x3 = Point2d.of(49.5f, 136.5f);
            MID_WALL_3x3 = Point2d.of(46.5f, 137.5f);
            MID_WALL_2x2 = Point2d.of(47f, 138f);

            _3x3Structures.add(MID_WALL_3x3); //midwall 3x3
            _3x3Structures.add(WALL_3x3); //wall 3x3
            _3x3Structures.add(Point2d.of(38.5f, 132.5f));
            _3x3Structures.add(Point2d.of(26.5f, 139.5f));

            //REAPER_JUMP1 = Point2d.of(49.0f, 130.0f); //depot
            REAPER_JUMP2 = Point2d.of(46.5f, 128.5f); //barracks
            //REAPER_JUMP3 = Point2d.of(51.5f, 131.5f); //eng bay

            BUNKER_NATURAL = Point2d.of(62.5f, 138.5f);

            STARPORTS.add(Point2d.of(37.5f, 135.5f));
            STARPORTS.add(Point2d.of(40.5f, 148.5f));
            STARPORTS.add(Point2d.of(29.5f, 146.5f));
            STARPORTS.add(Point2d.of(41.5f, 144.5f)); //factory
            STARPORTS.add(Point2d.of(32.5f, 148.5f));
            STARPORTS.add(Point2d.of(35.5f, 150.5f));
            STARPORTS.add(Point2d.of(26.5f, 142.5f));
            STARPORTS.add(Point2d.of(27.5f, 134.5f));
            STARPORTS.add(Point2d.of(28.5f, 130.5f));
            STARPORTS.add(Point2d.of(34.5f, 129.5f));
            STARPORTS.add(Point2d.of(47.5f, 146.5f));
            STARPORTS.add(Point2d.of(46.5f, 152.5f));
            STARPORTS.add(Point2d.of(48.5f, 156.5f));
            STARPORTS.add(Point2d.of(65.5f, 155.5f));
            STARPORTS.add(Point2d.of(60.5f, 158.5f));
            STARPORTS.add(Point2d.of(54.5f, 158.5f));

            TURRETS.add(Point2d.of(38.0f, 144.0f));
            TURRETS.add(Point2d.of(33.0f, 136.0f));
            TURRETS.add(Point2d.of(33.0f, 139.0f));

            MACRO_OCS.add(Point2d.of(42.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(30.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(35.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(35.5f, 120.5f));
            MACRO_OCS.add(Point2d.of(40.5f, 122.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 127.5f));

            extraDepots.add(Point2d.of(49.0f, 130.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(51.0f, 134.0f));
            extraDepots.add(Point2d.of(44.0f, 146.0f));
            extraDepots.add(Point2d.of(49.0f, 134.0f));
            extraDepots.add(Point2d.of(43.0f, 150.0f));
            extraDepots.add(Point2d.of(49.0f, 132.0f));
            extraDepots.add(Point2d.of(26.0f, 137.0f));
            extraDepots.add(Point2d.of(38.0f, 152.0f));
            extraDepots.add(Point2d.of(28.0f, 137.0f));
            extraDepots.add(Point2d.of(29.0f, 144.0f));
            extraDepots.add(Point2d.of(29.0f, 139.0f));
            extraDepots.add(Point2d.of(37.0f, 148.0f));
            extraDepots.add(Point2d.of(31.0f, 132.0f));
            extraDepots.add(Point2d.of(30.0f, 149.0f));
            extraDepots.add(Point2d.of(41.0f, 133.0f));
        }
        else {
            myMineralPos = Point2d.of(155.5f, 42.5f);
            enemyMineralPos = Point2d.of(36.5f, 146.5f);

            WALL_2x2 = Point2d.of(145.0f, 48.0f);
            MID_WALL_3x3 = Point2d.of(145.5f, 50.5f);
            MID_WALL_2x2 = Point2d.of(145f, 50f);
            WALL_3x3 = Point2d.of(142.5f, 51.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(164.5f, 51.5f));
            _3x3Structures.add(Point2d.of(162.5f, 57.5f));

            //REAPER_JUMP1 = Point2d.of(143.0f, 58.0f); //depot
            REAPER_JUMP2 = Point2d.of(145.5f, 59.5f); //barracks
            //REAPER_JUMP3 = Point2d.of(140.5f, 56.5f); //eng bay

            BUNKER_NATURAL = Point2d.of(129.5f, 49.5f);

            STARPORTS.add(Point2d.of(148.5f, 39.5f));
            STARPORTS.add(Point2d.of(154.5f, 39.5f));
            STARPORTS.add(Point2d.of(153.5f, 36.5f));
            STARPORTS.add(Point2d.of(148.5f, 42.5f)); //factory
            STARPORTS.add(Point2d.of(160.5f, 40.5f));
            STARPORTS.add(Point2d.of(163.5f, 45.5f));
            STARPORTS.add(Point2d.of(162.5f, 54.5f));
            STARPORTS.add(Point2d.of(156.5f, 58.5f));
            STARPORTS.add(Point2d.of(152.5f, 52.5f));
            STARPORTS.add(Point2d.of(152.5f, 55.5f));
            STARPORTS.add(Point2d.of(141.5f, 42.5f));
            STARPORTS.add(Point2d.of(143.5f, 35.5f));
            STARPORTS.add(Point2d.of(141.5f, 31.5f));
            STARPORTS.add(Point2d.of(136.5f, 28.5f));
            STARPORTS.add(Point2d.of(131.5f, 28.5f));
            STARPORTS.add(Point2d.of(125.5f, 32.5f));

            TURRETS.add(Point2d.of(153.0f, 44.0f));
            TURRETS.add(Point2d.of(159.0f, 52.0f));
            TURRETS.add(Point2d.of(159.0f, 49.0f));

            MACRO_OCS.add(Point2d.of(149.5f, 48.5f));
            MACRO_OCS.add(Point2d.of(146.5f, 54.5f));
            MACRO_OCS.add(Point2d.of(161.5f, 62.5f));
            MACRO_OCS.add(Point2d.of(156.5f, 62.5f));
            MACRO_OCS.add(Point2d.of(156.5f, 67.5f));
            MACRO_OCS.add(Point2d.of(151.5f, 65.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 60.5f));


            extraDepots.add(Point2d.of(143.0f, 58.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(141.0f, 54.0f));
            extraDepots.add(Point2d.of(166.0f, 61.0f));
            extraDepots.add(Point2d.of(143.0f, 54.0f));
            extraDepots.add(Point2d.of(160.0f, 66.0f));
            extraDepots.add(Point2d.of(143.0f, 56.0f));
            extraDepots.add(Point2d.of(166.0f, 47.0f));
            extraDepots.add(Point2d.of(157.0f, 41.0f));
            extraDepots.add(Point2d.of(166.0f, 49.0f));
            extraDepots.add(Point2d.of(162.0f, 43.0f));
            extraDepots.add(Point2d.of(158.0f, 37.0f));
            extraDepots.add(Point2d.of(164.0f, 42.0f));
            extraDepots.add(Point2d.of(161.0f, 38.0f));
            extraDepots.add(Point2d.of(164.0f, 48.0f));
            extraDepots.add(Point2d.of(147.0f, 62.0f));
        }
    }

    private static void setLocationsForDiscoBloodBath(boolean isTopPos) {
        numReaperWall = 2;
        if (isTopPos) {
            myMineralPos = Point2d.of(39.0f, 108.5f);
            enemyMineralPos = Point2d.of(161.0f, 71.5f);
            //REPAIR_BAY = Point2d.of(36f, 134f);
            WALL_2x2 = Point2d.of(39.0f, 132.0f);
            MID_WALL_3x3 = Point2d.of(38.5f, 129.5f);
            WALL_3x3 = Point2d.of(41.5f, 128.5f);
            MID_WALL_2x2 = Point2d.of(39f, 130f);

            _3x3Structures.add(MID_WALL_3x3); //midwall 3x3
            _3x3Structures.add(WALL_3x3); //wall 3x3
            _3x3Structures.add(Point2d.of(37.5f, 104.5f));
            _3x3Structures.add(Point2d.of(30.5f, 112.5f));

            //REAPER_JUMP1 = Point2d.of(59.0f, 124.0f);
            REAPER_JUMP2 = Point2d.of(56.5f, 122.5f);

            BUNKER_NATURAL = Point2d.of(56.5f, 139.5f);

            STARPORTS.add(Point2d.of(43.5f, 113.5f));
            STARPORTS.add(Point2d.of(43.5f, 116.5f));
            STARPORTS.add(Point2d.of(43.5f, 119.5f));
            STARPORTS.add(Point2d.of(36.5f, 107.5f));
            STARPORTS.add(Point2d.of(42.5f, 105.5f));
            STARPORTS.add(Point2d.of(48.5f, 105.5f));
            STARPORTS.add(Point2d.of(47.5f, 108.5f));
            STARPORTS.add(Point2d.of(52.5f, 108.5f));
            STARPORTS.add(Point2d.of(54.5f, 126.5f));
            STARPORTS.add(Point2d.of(37.5f, 119.5f));
            STARPORTS.add(Point2d.of(37.5f, 122.5f));
            STARPORTS.add(Point2d.of(37.5f, 125.5f));
            STARPORTS.add(Point2d.of(31.5f, 121.5f));
            STARPORTS.add(Point2d.of(30.5f, 108.5f));

            TURRETS.add(Point2d.of(35.0f, 116.0f));
            TURRETS.add(Point2d.of(43.0f, 111.0f));
            TURRETS.add(Point2d.of(37.0f, 111.0f));

            MACRO_OCS.add(Point2d.of(34.5f, 130.5f));
            MACRO_OCS.add(Point2d.of(50.5f, 113.5f));
            MACRO_OCS.add(Point2d.of(50.5f, 118.5f));
            MACRO_OCS.add(Point2d.of(50.5f, 123.5f));
            MACRO_OCS.add(Point2d.of(44.5f, 123.5f));
            MACRO_OCS.add(Point2d.of(32.5f, 125.5f));

            extraDepots.add(Point2d.of(59.0f, 124.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(45.0f, 111.0f));
            extraDepots.add(Point2d.of(31.0f, 106.0f));
            extraDepots.add(Point2d.of(33.0f, 106.0f));
            extraDepots.add(Point2d.of(41.0f, 108.0f));
            extraDepots.add(Point2d.of(40.0f, 104.0f));
            extraDepots.add(Point2d.of(42.0f, 103.0f));
            extraDepots.add(Point2d.of(46.0f, 103.0f));
            extraDepots.add(Point2d.of(29.0f, 124.0f));
            extraDepots.add(Point2d.of(29.0f, 128.0f));
            extraDepots.add(Point2d.of(29.0f, 116.0f));
            extraDepots.add(Point2d.of(29.0f, 120.0f));
            extraDepots.add(Point2d.of(31.0f, 116.0f));
            extraDepots.add(Point2d.of(30.0f, 118.0f));
            extraDepots.add(Point2d.of(31.0f, 129.0f));
        }
        else {
            myMineralPos = Point2d.of(161.0f, 71.5f);
            enemyMineralPos = Point2d.of(39.0f, 108.5f);
            //REPAIR_BAY = Point2d.of(165.5f,47f);
            WALL_2x2 = Point2d.of(161.0f, 48.0f);
            MID_WALL_3x3 = Point2d.of(161.5f, 50.5f);
            MID_WALL_2x2 = Point2d.of(161f, 50f);
            WALL_3x3 = Point2d.of(158.5f, 51.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(154.5f, 74.5f));
            _3x3Structures.add(Point2d.of(169.5f, 67.5f));

            //REAPER_JUMP1 = Point2d.of(143.0f, 58.0f);
            REAPER_JUMP2 = Point2d.of(142.5f, 55.5f);

            BUNKER_NATURAL = Point2d.of(143.5f, 40.5f);

            STARPORTS.add(Point2d.of(168.5f, 72.5f));
            STARPORTS.add(Point2d.of(162.5f, 74.5f));
            STARPORTS.add(Point2d.of(157.5f, 74.5f));
            STARPORTS.add(Point2d.of(145.5f, 58.5f));
            STARPORTS.add(Point2d.of(160.5f, 60.5f));
            STARPORTS.add(Point2d.of(160.5f, 57.5f));
            STARPORTS.add(Point2d.of(161.5f, 54.5f));
            STARPORTS.add(Point2d.of(156.5f, 55.5f));
            STARPORTS.add(Point2d.of(166.5f, 58.5f));
            STARPORTS.add(Point2d.of(168.5f, 52.5f));
            STARPORTS.add(Point2d.of(165.5f, 50.5f));
            STARPORTS.add(Point2d.of(167.5f, 55.5f));
            STARPORTS.add(Point2d.of(160.5f, 32.5f));
            STARPORTS.add(Point2d.of(151.5f, 28.5f));
            STARPORTS.add(Point2d.of(157.5f, 29.5f));

            TURRETS.add(Point2d.of(157.0f, 68.0f));
            TURRETS.add(Point2d.of(165.0f, 64.0f));
            TURRETS.add(Point2d.of(165.0f, 72.0f));

            MACRO_OCS.add(Point2d.of(149.5f, 73.5f));
            MACRO_OCS.add(Point2d.of(155.5f, 64.5f));
            MACRO_OCS.add(Point2d.of(149.5f, 68.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 63.5f));
            MACRO_OCS.add(Point2d.of(146.5f, 54.5f));
            MACRO_OCS.add(Point2d.of(152.5f, 58.5f));

            extraDepots.add(Point2d.of(143.0f, 58.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(154.0f, 70.0f));
            extraDepots.add(Point2d.of(154.0f, 72.0f));
            extraDepots.add(Point2d.of(153.0f, 68.0f));
            extraDepots.add(Point2d.of(155.0f, 68.0f));
            extraDepots.add(Point2d.of(156.0f, 61.0f));
            extraDepots.add(Point2d.of(158.0f, 59.0f));
            extraDepots.add(Point2d.of(156.0f, 59.0f));
            extraDepots.add(Point2d.of(158.0f, 61.0f));
            extraDepots.add(Point2d.of(169.0f, 64.0f));
            extraDepots.add(Point2d.of(171.0f, 64.0f));
            extraDepots.add(Point2d.of(170.0f, 60.0f));
            extraDepots.add(Point2d.of(170.0f, 62.0f));
            extraDepots.add(Point2d.of(146.0f, 73.0f));
            extraDepots.add(Point2d.of(159.0f, 72.0f));
            extraDepots.add(Point2d.of(163.0f, 72.0f));
            extraDepots.add(Point2d.of(160.0f, 76.0f));
            extraDepots.add(Point2d.of(146.0f, 71.0f));
            extraDepots.add(Point2d.of(154.0f, 77.0f));
            extraDepots.add(Point2d.of(150.0f, 77.0f));
        }
    }

    private static void setLocationsForEphemeron(boolean isTopPos) {
        numReaperWall = 3;
        if (isTopPos) {
            myMineralPos = Point2d.of(22.0f, 139.5f);
            enemyMineralPos = Point2d.of(138.0f, 20.5f);
            //REPAIR_BAY = Point2d.of(40.0f, 125.5f);
            WALL_2x2 = Point2d.of(34.0f, 125.0f);
            MID_WALL_2x2 = Point2d.of(36f, 125f);
            MID_WALL_3x3 = Point2d.of(36.5f, 125.5f);
            WALL_3x3 = Point2d.of(37.5f, 122.5f);
            BUNKER_NATURAL = Point2d.of(38.5f, 111.5f);

            //REAPER_JUMP1 = Point2d.of(46.0f, 125.0f);
            REAPER_JUMP2 = Point2d.of(43.5f, 123.5f);
            //REAPER_JUMP3 = Point2d.of(42.0f, 121.0f); //moved to extraDepots

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(35.5f, 129.5f));
            _3x3Structures.add(Point2d.of(34.5f, 142.5f));

            STARPORTS.add(Point2d.of(25.5f, 149.5f));
            STARPORTS.add(Point2d.of(22.5f, 147.5f));
            STARPORTS.add(Point2d.of(31.5f, 148.5f));
            STARPORTS.add(Point2d.of(18.5f, 143.5f));
            STARPORTS.add(Point2d.of(18.5f, 138.5f));
            STARPORTS.add(Point2d.of(26.5f, 134.5f));
            STARPORTS.add(Point2d.of(37.5f, 146.5f));
            STARPORTS.add(Point2d.of(20.5f, 129.5f));
            STARPORTS.add(Point2d.of(23.5f, 131.5f));
            STARPORTS.add(Point2d.of(25.5f, 128.5f));
            STARPORTS.add(Point2d.of(30.5f, 127.5f));
            STARPORTS.add(Point2d.of(28.5f, 131.5f));
            STARPORTS.add(Point2d.of(40.5f, 148.5f));
            STARPORTS.add(Point2d.of(42.5f, 145.5f));
            STARPORTS.add(Point2d.of(20.5f, 118.5f));

            TURRETS.add(Point2d.of(25.0f, 138.0f));
            TURRETS.add(Point2d.of(30.0f, 143.0f));
            TURRETS.add(Point2d.of(26.0f, 142.0f));

            MACRO_OCS.add(Point2d.of(34.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(34.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(39.5f, 134.5f));
            MACRO_OCS.add(Point2d.of(44.5f, 135.5f));
            MACRO_OCS.add(Point2d.of(44.5f, 140.5f));
            MACRO_OCS.add(Point2d.of(39.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 130.5f));

            extraDepots.add(Point2d.of(46.0f, 125.0f)); //reaperJump1
            extraDepots.add(Point2d.of(42.0f, 121.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(28.0f, 151.0f));
            extraDepots.add(Point2d.of(35.0f, 146.0f));
            extraDepots.add(Point2d.of(21.0f, 145.0f));
            extraDepots.add(Point2d.of(17.0f, 136.0f));
            extraDepots.add(Point2d.of(19.0f, 134.0f));
            extraDepots.add(Point2d.of(27.0f, 147.0f));
            extraDepots.add(Point2d.of(29.0f, 147.0f));
            extraDepots.add(Point2d.of(23.0f, 150.0f));
            extraDepots.add(Point2d.of(20.0f, 147.0f));
            extraDepots.add(Point2d.of(20.0f, 132.0f));
            extraDepots.add(Point2d.of(18.0f, 141.0f));
            extraDepots.add(Point2d.of(20.0f, 141.0f));
            extraDepots.add(Point2d.of(32.0f, 151.0f));
            extraDepots.add(Point2d.of(38.0f, 149.0f));
        }
        else {
            myMineralPos = Point2d.of(138.0f, 20.5f);
            enemyMineralPos = Point2d.of(22.0f, 139.5f);
            //REPAIR_BAY = Point2d.of(126.5f, 31f);
            //REAPER_JUMP1 = Point2d.of(114.0f, 35.0f);
            REAPER_JUMP2 = Point2d.of(116.5f, 36.5f);
            //REAPER_JUMP3 = Point2d.of(118.0f, 39.0f);
            BUNKER_NATURAL = Point2d.of(121.5f, 48.5f);
            WALL_2x2 = Point2d.of(126.0f, 35.0f);
            WALL_3x3 = Point2d.of(122.5f, 37.5f);
            MID_WALL_2x2 = Point2d.of(124f, 35f);
            MID_WALL_3x3 = Point2d.of(123.5f, 34.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(132.5f, 26.5f));
            _3x3Structures.add(Point2d.of(123.5f, 13.5f));

            STARPORTS.add(Point2d.of(127.5f, 27.5f));
            STARPORTS.add(Point2d.of(130.5f, 29.5f));
            STARPORTS.add(Point2d.of(135.5f, 28.5f));
            STARPORTS.add(Point2d.of(138.5f, 30.5f));
            STARPORTS.add(Point2d.of(140.5f, 24.5f));
            STARPORTS.add(Point2d.of(140.5f, 20.5f));
            STARPORTS.add(Point2d.of(140.5f, 16.5f));
            STARPORTS.add(Point2d.of(137.5f, 14.5f));
            STARPORTS.add(Point2d.of(134.5f, 12.5f));
            STARPORTS.add(Point2d.of(129.5f, 11.5f));
            STARPORTS.add(Point2d.of(133.5f, 31.5f));
            STARPORTS.add(Point2d.of(128.5f, 32.5f));
            STARPORTS.add(Point2d.of(120.5f, 32.5f));
            STARPORTS.add(Point2d.of(117.5f, 30.5f));
            STARPORTS.add(Point2d.of(114.5f, 28.5f));

            TURRETS.add(Point2d.of(127.0f, 17.0f));
            TURRETS.add(Point2d.of(135.0f, 22.0f));
            TURRETS.add(Point2d.of(132.0f, 13.0f));

            MACRO_OCS.add(Point2d.of(125.5f, 21.5f));
            MACRO_OCS.add(Point2d.of(120.5f, 22.5f));
            MACRO_OCS.add(Point2d.of(120.5f, 17.5f));
            MACRO_OCS.add(Point2d.of(119.5f, 12.5f));
            MACRO_OCS.add(Point2d.of(115.5f, 17.5f));
            MACRO_OCS.add(Point2d.of(114.5f, 23.5f));
            MACRO_OCS.add(Point2d.of(123.5f, 27.5f));

            extraDepots.add(Point2d.of(114.0f, 35.0f)); //reaperJump1
            extraDepots.add(Point2d.of(118.0f, 39.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(139.0f, 12.0f));
            extraDepots.add(Point2d.of(137.0f, 10.0f));
            extraDepots.add(Point2d.of(135.0f, 10.0f));
            extraDepots.add(Point2d.of(132.0f, 9.0f));
            extraDepots.add(Point2d.of(128.0f, 9.0f));
            extraDepots.add(Point2d.of(130.0f, 14.0f));
            extraDepots.add(Point2d.of(140.0f, 28.0f));
            extraDepots.add(Point2d.of(136.0f, 33.0f));
            extraDepots.add(Point2d.of(138.0f, 33.0f));
            extraDepots.add(Point2d.of(136.0f, 35.0f));
            extraDepots.add(Point2d.of(134.0f, 34.0f));
            extraDepots.add(Point2d.of(132.0f, 34.0f));
            extraDepots.add(Point2d.of(128.0f, 30.0f));
            extraDepots.add(Point2d.of(127.0f, 12.0f));
        }
    }

    private static void setLocationsForEternalEmpire(boolean isTopPos) {
        numReaperWall = 3;
        if (isTopPos) {
            myMineralPos = Point2d.of(150.0f, 141.5f);
            enemyMineralPos = Point2d.of(26.0f, 30.5f);
            //REPAIR_BAY = Point2d.of(144.5f, 133f);
            WALL_2x2 = Point2d.of(144f, 125f);
            MID_WALL_2x2 = Point2d.of(146f, 125f);
            MID_WALL_3x3 = Point2d.of(146.5f, 125.5f);
            WALL_3x3 = Point2d.of(147.5f, 122.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(137.5f, 135.5f));
            _3x3Structures.add(Point2d.of(153.5f, 137.5f));

            FACTORY = Point2d.of(149.5f, 134.5f);
            BUNKER_NATURAL = Point2d.of(128.5f, 117.5f);
            //REAPER_JUMP1 = Point2d.of(130.0f, 127.0f);
            REAPER_JUMP2 = Point2d.of(128.5f, 129.5f);
            //REAPER_JUMP3 = Point2d.of(130.0f, 125.0f);

            STARPORTS.add(Point2d.of(128.5f, 132.5f));
            STARPORTS.add(Point2d.of(152.5f, 142.5f));
            STARPORTS.add(Point2d.of(153.5f, 146.5f));
            STARPORTS.add(Point2d.of(151.5f, 150.5f));
            STARPORTS.add(Point2d.of(147.5f, 148.5f));
            STARPORTS.add(Point2d.of(142.5f, 149.5f));
            STARPORTS.add(Point2d.of(146.5f, 151.5f));
            STARPORTS.add(Point2d.of(136.5f, 151.5f));
            STARPORTS.add(Point2d.of(133.5f, 145.5f));
            STARPORTS.add(Point2d.of(131.5f, 136.5f));
            STARPORTS.add(Point2d.of(131.5f, 129.5f));
            STARPORTS.add(Point2d.of(132.5f, 126.5f));
            STARPORTS.add(Point2d.of(151.5f, 125.5f));
            STARPORTS.add(Point2d.of(138.5f, 126.5f));
            STARPORTS.add(Point2d.of(153.5f, 111.5f));
            STARPORTS.add(Point2d.of(133.5f, 132.5f));

            TURRETS.add(Point2d.of(139.0f, 145.0f));
            TURRETS.add(Point2d.of(147.0f, 140.0f));
            TURRETS.add(Point2d.of(142.0f, 145.0f));

            MACRO_OCS.add(Point2d.of(132.5f, 150.5f));
            MACRO_OCS.add(Point2d.of(129.5f, 145.5f));
            MACRO_OCS.add(Point2d.of(137.5f, 140.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 140.5f));
            MACRO_OCS.add(Point2d.of(141.5f, 135.5f));
            MACRO_OCS.add(Point2d.of(141.5f, 130.5f));
            MACRO_OCS.add(Point2d.of(154.5f, 130.5f));
            MACRO_OCS.add(Point2d.of(149.5f, 130.5f));

            extraDepots.add(Point2d.of(130.0f, 127.0f)); //reaperJump1
            extraDepots.add(Point2d.of(130.0f, 125.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(135.0f, 154.0f));
            extraDepots.add(Point2d.of(137.0f, 154.0f));
            extraDepots.add(Point2d.of(136.0f, 149.0f));
            extraDepots.add(Point2d.of(136.0f, 147.0f));
            extraDepots.add(Point2d.of(144.0f, 152.0f));
            extraDepots.add(Point2d.of(142.0f, 152.0f));
            extraDepots.add(Point2d.of(129.0f, 142.0f));
            extraDepots.add(Point2d.of(136.0f, 128.0f));
            extraDepots.add(Point2d.of(136.0f, 130.0f));
            extraDepots.add(Point2d.of(138.0f, 133.0f));
            extraDepots.add(Point2d.of(138.0f, 131.0f));
            extraDepots.add(Point2d.of(138.0f, 129.0f));
            extraDepots.add(Point2d.of(131.0f, 134.0f));  //drop turret
        }
        else {
            myMineralPos = Point2d.of(26.0f, 30.5f);
            enemyMineralPos = Point2d.of(150.0f, 141.5f);
            //REPAIR_BAY = Point2d.of(32.5f, 41f);

            WALL_2x2 = Point2d.of(32f, 47f);
            MID_WALL_2x2 = Point2d.of(30f, 47f);
            MID_WALL_3x3 = Point2d.of(29.5f, 46.5f);
            WALL_3x3 = Point2d.of(28.5f, 49.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(22.5f, 35.5f));
            _3x3Structures.add(Point2d.of(27.5f, 20.5f));

            //REAPER_JUMP1 = Point2d.of(46.0f, 45.0f);
            REAPER_JUMP2 = Point2d.of(47.5f, 42.5f);
            //REAPER_JUMP3 = Point2d.of(46.0f, 47.0f);
            BUNKER_NATURAL = Point2d.of(47.5f, 54.5f);
            FACTORY = Point2d.of(31.5f, 36.5f);

            STARPORTS.add(Point2d.of(36.5f, 21.5f));
            STARPORTS.add(Point2d.of(22.5f, 21.5f));
            STARPORTS.add(Point2d.of(31.5f, 21.5f));
            STARPORTS.add(Point2d.of(22.5f, 32.5f));
            STARPORTS.add(Point2d.of(20.5f, 28.5f));
            STARPORTS.add(Point2d.of(20.5f, 25.5f));
            STARPORTS.add(Point2d.of(26.5f, 23.5f));
            STARPORTS.add(Point2d.of(40.5f, 26.5f));
            STARPORTS.add(Point2d.of(42.5f, 35.5f));
            STARPORTS.add(Point2d.of(43.5f, 38.5f));
            STARPORTS.add(Point2d.of(41.5f, 41.5f));
            STARPORTS.add(Point2d.of(20.5f, 59.5f));
            STARPORTS.add(Point2d.of(20.5f, 62.5f));
            STARPORTS.add(Point2d.of(20.5f, 65.5f));
            STARPORTS.add(Point2d.of(22.5f, 46.5f));

            TURRETS.add(Point2d.of(37.0f, 27.0f));
            TURRETS.add(Point2d.of(29.0f, 32.0f));
            TURRETS.add(Point2d.of(34.0f, 24.0f));

            MACRO_OCS.add(Point2d.of(38.5f, 31.5f));
            MACRO_OCS.add(Point2d.of(43.5f, 31.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 26.5f));
            MACRO_OCS.add(Point2d.of(43.5f, 21.5f));
            MACRO_OCS.add(Point2d.of(37.5f, 36.5f));
            MACRO_OCS.add(Point2d.of(36.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(21.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(26.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(42.5f, 46.5f));

            extraDepots.add(Point2d.of(46.0f, 45.0f)); //reaperJump1
            extraDepots.add(Point2d.of(46.0f, 47.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(41.0f, 18.0f));
            extraDepots.add(Point2d.of(39.0f, 18.0f));
            extraDepots.add(Point2d.of(39.0f, 23.0f));
            extraDepots.add(Point2d.of(26.0f, 26.0f));
            extraDepots.add(Point2d.of(23.0f, 30.0f));
            extraDepots.add(Point2d.of(25.0f, 29.0f));
            extraDepots.add(Point2d.of(21.0f, 38.0f));
            extraDepots.add(Point2d.of(23.0f, 38.0f));
            extraDepots.add(Point2d.of(25.0f, 38.0f));
            extraDepots.add(Point2d.of(27.0f, 38.0f));
            extraDepots.add(Point2d.of(34.0f, 38.0f));
            extraDepots.add(Point2d.of(47.0f, 30.0f));
            extraDepots.add(Point2d.of(43.0f, 28.0f)); //drop turret
        }
    }

    private static void setLocationsForEverDream(boolean isTopPos) {
        numReaperWall = 2;
        if (isTopPos) {
            myMineralPos = Point2d.of(147.0f, 164.5f);
            enemyMineralPos = Point2d.of(53f, 47.5f);
            WALL_2x2 = Point2d.of(144.0f, 151.0f);
            MID_WALL_2x2 = Point2d.of(142f, 151f);
            MID_WALL_3x3 = Point2d.of(141.5f, 151.5f);
            WALL_3x3 = Point2d.of(140.5f, 148.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(135.5f, 173.5f));
            _3x3Structures.add(Point2d.of(138.5f, 173.5f));

            BUNKER_NATURAL = Point2d.of(147.5f, 137.5f);
            //REAPER_JUMP1 = Point2d.of(136.0f, 144.0f);
            REAPER_JUMP2 = Point2d.of(138.5f, 144.5f);
            FACTORY = Point2d.of(144.5f, 157.5f);

            STARPORTS.add(Point2d.of(149.5f, 161.5f));
            STARPORTS.add(Point2d.of(149.5f, 165.5f));
            STARPORTS.add(Point2d.of(147.5f, 169.5f));
            STARPORTS.add(Point2d.of(142.5f, 172.5f));
            STARPORTS.add(Point2d.of(129.5f, 172.5f));
            STARPORTS.add(Point2d.of(124.5f, 172.5f));
            STARPORTS.add(Point2d.of(129.5f, 168.5f));
            STARPORTS.add(Point2d.of(136.5f, 151.5f));
            STARPORTS.add(Point2d.of(131.5f, 151.5f));
            STARPORTS.add(Point2d.of(137.5f, 155.5f));
            STARPORTS.add(Point2d.of(137.5f, 158.5f));
            STARPORTS.add(Point2d.of(131.5f, 155.5f));
            STARPORTS.add(Point2d.of(131.5f, 158.5f));
            STARPORTS.add(Point2d.of(121.5f, 159.5f));
            STARPORTS.add(Point2d.of(160.5f, 154.5f));

            TURRETS.add(Point2d.of(135.0f, 168.0f));
            TURRETS.add(Point2d.of(144.0f, 163.0f));
            TURRETS.add(Point2d.of(138.0f, 168.0f));

            MACRO_OCS.add(Point2d.of(134.5f, 163.5f));
            MACRO_OCS.add(Point2d.of(129.5f, 163.5f));
            MACRO_OCS.add(Point2d.of(124.5f, 163.5f));
            //MACRO_OCS.add(Point2d.of(118.5f, 163.5f)); //remove if drop turret
            MACRO_OCS.add(Point2d.of(120.5f, 168.5f));
            MACRO_OCS.add(Point2d.of(125.5f, 168.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 147.5f));
            MACRO_OCS.add(Point2d.of(127.5f, 158.5f));

            extraDepots.add(Point2d.of(136.0f, 144.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(152.0f, 163.0f));
            extraDepots.add(Point2d.of(150.0f, 159.0f));
            extraDepots.add(Point2d.of(138.0f, 171.0f));
            extraDepots.add(Point2d.of(132.0f, 170.0f));
            extraDepots.add(Point2d.of(132.0f, 174.0f));
            extraDepots.add(Point2d.of(127.0f, 174.0f));
            extraDepots.add(Point2d.of(129.0f, 155.0f));
            extraDepots.add(Point2d.of(134.0f, 153.0f));
            extraDepots.add(Point2d.of(139.0f, 153.0f));
            extraDepots.add(Point2d.of(134.0f, 160.0f));
            extraDepots.add(Point2d.of(140.0f, 160.0f));
            extraDepots.add(Point2d.of(146.0f, 155.0f));
            extraDepots.add(Point2d.of(144.0f, 160.0f));
            extraDepots.add(Point2d.of(142.0f, 160.0f));
            extraDepots.add(Point2d.of(119f, 163f)); //drop turret

        }
        else {
            myMineralPos = Point2d.of(53f, 47.5f);
            enemyMineralPos = Point2d.of(147.0f, 164.5f);

            WALL_2x2 = Point2d.of(56.0f, 61.0f);
            WALL_3x3 = Point2d.of(59.5f, 63.5f);
            MID_WALL_2x2 = Point2d.of(58f, 61f);
            MID_WALL_3x3 = Point2d.of(58.5f, 60.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(68.5f, 42.5f));
            _3x3Structures.add(Point2d.of(52.5f, 42.5f));

            //REAPER_JUMP1 = Point2d.of(64.0f, 68.0f);
            REAPER_JUMP2 = Point2d.of(61.5f, 67.5f);
            //REAPER_JUMP3 = Point2d.of(46.0f, 47.0f);
            BUNKER_NATURAL = Point2d.of(52.5f, 74.5f);
            FACTORY = Point2d.of(57.5f, 55.5f);

            STARPORTS.add(Point2d.of(57.5f, 38.5f));
            STARPORTS.add(Point2d.of(62.5f, 38.5f));
            STARPORTS.add(Point2d.of(67.5f, 38.5f));
            STARPORTS.add(Point2d.of(72.5f, 38.5f));
            STARPORTS.add(Point2d.of(55.5f, 41.5f));
            STARPORTS.add(Point2d.of(48.5f, 50.5f));
            STARPORTS.add(Point2d.of(48.5f, 47.5f));
            STARPORTS.add(Point2d.of(71.5f, 42.5f));
            STARPORTS.add(Point2d.of(60.5f, 52.5f));
            STARPORTS.add(Point2d.of(62.5f, 55.5f));
            STARPORTS.add(Point2d.of(62.5f, 58.5f));
            STARPORTS.add(Point2d.of(73.5f, 52.5f));
            STARPORTS.add(Point2d.of(79.5f, 50.5f));
            STARPORTS.add(Point2d.of(73.5f, 55.5f));
            STARPORTS.add(Point2d.of(37.5f, 58.5f));

            TURRETS.add(Point2d.of(65.0f, 44.0f));
            TURRETS.add(Point2d.of(56.0f, 49.0f));
            TURRETS.add(Point2d.of(62.0f, 44.0f));

            MACRO_OCS.add(Point2d.of(65.5f, 48.5f));
            MACRO_OCS.add(Point2d.of(70.5f, 47.5f));
            MACRO_OCS.add(Point2d.of(75.5f, 47.5f));
            MACRO_OCS.add(Point2d.of(80.5f, 46.5f));
            MACRO_OCS.add(Point2d.of(77.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(69.5f, 53.5f));
            MACRO_OCS.add(Point2d.of(68.5f, 58.5f));
            MACRO_OCS.add(Point2d.of(65.5f, 64.5f));

            extraDepots.add(Point2d.of(64.0f, 68.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(70.0f, 40.0f));
            extraDepots.add(Point2d.of(74.0f, 44.0f));
            extraDepots.add(Point2d.of(60.0f, 40.0f));
            extraDepots.add(Point2d.of(62.0f, 41.0f));
            extraDepots.add(Point2d.of(51.0f, 52.0f));
            extraDepots.add(Point2d.of(60.0f, 57.0f));
            extraDepots.add(Point2d.of(51.0f, 54.0f));
            extraDepots.add(Point2d.of(53.0f, 54.0f));
            extraDepots.add(Point2d.of(53.0f, 56.0f));
            extraDepots.add(Point2d.of(62.0f, 65.0f));
            extraDepots.add(Point2d.of(62.0f, 63.0f));
            extraDepots.add(Point2d.of(65.0f, 60.0f));
            extraDepots.add(Point2d.of(54.0f, 59.0f));
            extraDepots.add(Point2d.of(79.0f, 53.0f)); //turret drop
        }
    }

    private static void setLocationsForGoldenWall(boolean isTopPos) {
        numReaperWall = 1;
        if (isTopPos) { //left spawn
            myMineralPos = Point2d.of(25f, 51.5f);
            enemyMineralPos = Point2d.of(183f, 51.5f);
            //REPAIR_BAY = Point2d.of(144.5f, 133f);

            WALL_2x2 = Point2d.of(41.0f, 64.0f);
            MID_WALL_3x3 = Point2d.of(40.5f, 61.5f);
            WALL_3x3 = Point2d.of(43.5f, 60.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(22.5f, 48.5f));
            _3x3Structures.add(Point2d.of(22.5f, 53.5f));

            BUNKER_NATURAL = Point2d.of(48.5f, 78.5f);
            //REAPER_JUMP1 = Point2d.of(46.0f, 87.0f));
            REAPER_JUMP2 = Point2d.of(128.5f, 129.5f);
            //REAPER_JUMP3 = Point2d.of(130.0f, 125.0f);
            FACTORY = Point2d.of(34.5f, 57.5f);

            STARPORTS.add(Point2d.of(34.5f, 54.5f));
            STARPORTS.add(Point2d.of(36.5f, 50.5f));
            STARPORTS.add(Point2d.of(36.5f, 47.5f));
            STARPORTS.add(Point2d.of(33.5f, 45.5f));
            STARPORTS.add(Point2d.of(34.5f, 42.5f));
            STARPORTS.add(Point2d.of(39.5f, 44.5f));
            STARPORTS.add(Point2d.of(23.5f, 45.5f));
            STARPORTS.add(Point2d.of(23.5f, 42.5f));
            STARPORTS.add(Point2d.of(29.5f, 40.5f));
            STARPORTS.add(Point2d.of(25.5f, 38.5f));
            STARPORTS.add(Point2d.of(22.5f, 36.5f));
            STARPORTS.add(Point2d.of(32.5f, 36.5f));
            STARPORTS.add(Point2d.of(22.5f, 56.5f));
            STARPORTS.add(Point2d.of(25.5f, 58.5f));
            STARPORTS.add(Point2d.of(30.5f, 61.5f));
            STARPORTS.add(Point2d.of(31.5f, 64.5f));


            TURRETS.add(Point2d.of(30.0f, 46.0f));
            TURRETS.add(Point2d.of(30.0f, 55.0f));
            TURRETS.add(Point2d.of(29.0f, 50.0f));

            MACRO_OCS.add(Point2d.of(26.5f, 64.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 55.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 54.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 49.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 64.5f));
            MACRO_OCS.add(Point2d.of(51.5f, 69.5f));

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(46.0f, 87.0f)); //reaperJump1
            extraDepots.add(Point2d.of(23.0f, 51.0f));
            extraDepots.add(Point2d.of(23.0f, 62.0f));
            extraDepots.add(Point2d.of(25.0f, 61.0f));
            extraDepots.add(Point2d.of(27.0f, 61.0f));
            extraDepots.add(Point2d.of(28.0f, 43.0f));
            extraDepots.add(Point2d.of(28.0f, 45.0f));
            extraDepots.add(Point2d.of(21.0f, 46.0f));
            extraDepots.add(Point2d.of(21.0f, 44.0f));
            extraDepots.add(Point2d.of(23.0f, 40.0f));
            extraDepots.add(Point2d.of(39.0f, 42.0f));
            extraDepots.add(Point2d.of(39.0f, 40.0f));
            extraDepots.add(Point2d.of(41.0f, 42.0f));
            extraDepots.add(Point2d.of(37.0f, 40.0f));
            extraDepots.add(Point2d.of(35.0f, 40.0f));
            extraDepots.add(Point2d.of(42.0f, 46.0f)); //drop turret TODO: test location
        }
        else {
            myMineralPos = Point2d.of(183f, 51.5f);
            enemyMineralPos = Point2d.of(25f, 51.5f);
            //REPAIR_BAY = Point2d.of(32.5f, 41f);

            WALL_2x2 = Point2d.of(167.0f, 64.0f);
            WALL_3x3 = Point2d.of(164.5f, 60.5f);
            MID_WALL_3x3 = Point2d.of(167.5f, 61.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(171.5f, 50.5f));
            _3x3Structures.add(Point2d.of(169.5f, 40.5f));

            //REAPER_JUMP1 = Point2d.of(162.0f, 87.0f);
            BUNKER_NATURAL = Point2d.of(159.5f, 78.5f);
            FACTORY = Point2d.of(174.5f, 61.5f);

            STARPORTS.add(Point2d.of(184.5f, 45.5f));
            STARPORTS.add(Point2d.of(181.5f, 43.5f));
            STARPORTS.add(Point2d.of(172.5f, 37.5f));
            STARPORTS.add(Point2d.of(172.5f, 40.5f));
            STARPORTS.add(Point2d.of(178.5f, 40.5f));
            STARPORTS.add(Point2d.of(178.5f, 36.5f));
            STARPORTS.add(Point2d.of(167.5f, 54.5f));
            STARPORTS.add(Point2d.of(167.5f, 57.5f));
            STARPORTS.add(Point2d.of(164.5f, 46.5f));
            STARPORTS.add(Point2d.of(183.5f, 56.5f));
            STARPORTS.add(Point2d.of(180.5f, 59.5f));
            STARPORTS.add(Point2d.of(181.5f, 62.5f));
            STARPORTS.add(Point2d.of(175.5f, 64.5f));
            STARPORTS.add(Point2d.of(179.5f, 66.5f));
            STARPORTS.add(Point2d.of(175.5f, 71.5f));

            TURRETS.add(Point2d.of(178.0f, 46.0f));
            TURRETS.add(Point2d.of(178.0f, 55.0f));
            TURRETS.add(Point2d.of(184.0f, 49.0f));

            MACRO_OCS.add(Point2d.of(166.5f, 50.5f));
            MACRO_OCS.add(Point2d.of(161.5f, 50.5f));
            MACRO_OCS.add(Point2d.of(162.5f, 56.5f));
            MACRO_OCS.add(Point2d.of(170.5f, 45.5f));
            MACRO_OCS.add(Point2d.of(184.5f, 38.5f));
            MACRO_OCS.add(Point2d.of(155.5f, 64.5f));
            MACRO_OCS.add(Point2d.of(156.5f, 69.5f));

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(162.0f, 87.0f)); //reaperJump1
            extraDepots.add(Point2d.of(186.0f, 48.0f));
            extraDepots.add(Point2d.of(186.0f, 50.0f));
            extraDepots.add(Point2d.of(187.0f, 52.0f));
            extraDepots.add(Point2d.of(185.0f, 52.0f));
            extraDepots.add(Point2d.of(186.0f, 54.0f));
            extraDepots.add(Point2d.of(184.0f, 54.0f));
            extraDepots.add(Point2d.of(175.0f, 42.0f));
            extraDepots.add(Point2d.of(175.0f, 44.0f));
            extraDepots.add(Point2d.of(174.0f, 47.0f));
            extraDepots.add(Point2d.of(167.0f, 44.0f));
            extraDepots.add(Point2d.of(167.0f, 42.0f));
            extraDepots.add(Point2d.of(176.0f, 35.0f));
            extraDepots.add(Point2d.of(172.0f, 53.0f));
            extraDepots.add(Point2d.of(184.0f, 64.0f));
            extraDepots.add(Point2d.of(167.0f, 44.0f)); //drop turret TODO: test location

        }
    }

    private static void setLocationsForIceAndChrome(boolean isTopPos) {
        numReaperWall = 5; //TODO: handle a 5 structure wall
        if (isTopPos) {
            myMineralPos = Point2d.of(183.5f, 179.5f);
            enemyMineralPos = Point2d.of(72.5f, 56.5f);
            //REPAIR_BAY = Point2d.of(32.5f, 121.5f);
            WALL_2x2 = Point2d.of(178.0f, 159.0f);
            MID_WALL_2x2 = Point2d.of(176f, 159f);
            MID_WALL_3x3 = Point2d.of(175.5f, 159.5f);
            WALL_3x3 = Point2d.of(174.5f, 156.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(175.5f, 179.5f));
            _3x3Structures.add(Point2d.of(175.5f, 182.5f));

            BUNKER_NATURAL = Point2d.of(177.5f, 138.5f);

            //REAPER_JUMP1 = Point2d.of(164.0f, 165.0f); //depot
            REAPER_JUMP2 = Point2d.of(166.5f, 163.5f); //barracks
            //REAPER_JUMP3 = Point2d.of(168.0f, 161.0f); //depot
            //REAPER_JUMP4 = Point2d.of(168.5f, 158.5f); //eng bay
            //REAPER_JUMP5 = Point2d.of(169.0f, 156.0f); //depot

            STARPORTS.add(Point2d.of(182.5f, 163.5f));
            STARPORTS.add(Point2d.of(189.5f, 165.5f));
            STARPORTS.add(Point2d.of(192.5f, 168.5f));
            STARPORTS.add(Point2d.of(192.5f, 172.5f));
            STARPORTS.add(Point2d.of(189.5f, 179.5f));
            STARPORTS.add(Point2d.of(183.5f, 181.5f));
            STARPORTS.add(Point2d.of(178.5f, 182.5f));
            STARPORTS.add(Point2d.of(170.5f, 162.5f));
            STARPORTS.add(Point2d.of(169.5f, 165.5f));
            STARPORTS.add(Point2d.of(167.5f, 168.5f));
            STARPORTS.add(Point2d.of(173.5f, 168.5f));
            STARPORTS.add(Point2d.of(175.5f, 165.5f));
            STARPORTS.add(Point2d.of(176.5f, 162.5f));
            STARPORTS.add(Point2d.of(193.5f, 153.5f));
            STARPORTS.add(Point2d.of(187.5f, 154.5f));
            STARPORTS.add(Point2d.of(182.5f, 160.5f)); //factory

            TURRETS.add(Point2d.of(187.0f, 172.0f));
            TURRETS.add(Point2d.of(178.0f, 177.0f));
            TURRETS.add(Point2d.of(181.0f, 177.0f));

            MACRO_OCS.add(Point2d.of(182.5f, 167.5f));
            MACRO_OCS.add(Point2d.of(176.5f, 172.5f));
            MACRO_OCS.add(Point2d.of(170.5f, 173.5f));
            MACRO_OCS.add(Point2d.of(170.5f, 178.5f));
            MACRO_OCS.add(Point2d.of(189.5f, 161.5f));

            extraDepots.add(Point2d.of(164.0f, 165.0f)); //reaperJump1
            extraDepots.add(Point2d.of(168.0f, 161.0f)); //reaperJump3
            extraDepots.add(Point2d.of(169.0f, 156.0f)); //reaperJump5
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(194.0f, 166.0f));
            extraDepots.add(Point2d.of(194.0f, 164.0f));
            extraDepots.add(Point2d.of(187.0f, 166.0f));
            extraDepots.add(Point2d.of(187.0f, 168.0f));
            extraDepots.add(Point2d.of(193.0f, 162.0f));
            extraDepots.add(Point2d.of(189.0f, 158.0f));
            extraDepots.add(Point2d.of(187.0f, 158.0f));
            extraDepots.add(Point2d.of(191.0f, 177.0f));
            extraDepots.add(Point2d.of(191.0f, 175.0f));
            extraDepots.add(Point2d.of(187.0f, 179.0f));
            extraDepots.add(Point2d.of(181.0f, 180.0f));
            extraDepots.add(Point2d.of(189.0f, 182.0f));
            extraDepots.add(Point2d.of(171.0f, 160.0f));
        }
        else {
            myMineralPos = Point2d.of(72.5f, 56.5f);
            enemyMineralPos = Point2d.of(183.5f, 179.5f);
            //REPAIR_BAY = Point2d.of(158.5f, 49.5f);

            WALL_2x2 = Point2d.of(78.0f, 77.0f);
            WALL_3x3 = Point2d.of(81f, 80f);
            MID_WALL_2x2 = Point2d.of(153f, 50f);
            MID_WALL_3x3 = Point2d.of(80.5f, 76.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(80.5f, 56.5f));
            _3x3Structures.add(Point2d.of(63.5f, 62.5f));

            //REAPER_JUMP1 = Point2d.of(92.0f, 71.0f); //depot
            REAPER_JUMP2 = Point2d.of(89.5f, 72.5f); //barracks
            //REAPER_JUMP3 = Point2d.of(88.0f, 75.0f); //depot
            //REAPER_JUMP4 = Point2d.of(87.5f, 77.5f); //eng bay
            //REAPER_JUMP5 = Point2d.of(87.0f, 80.0f); //depot

            BUNKER_NATURAL = Point2d.of(78.5f, 97.5f);

            STARPORTS.add(Point2d.of(78.5f, 59.5f));
            STARPORTS.add(Point2d.of(78.5f, 53.5f));
            STARPORTS.add(Point2d.of(67.5f, 54.5f));
            STARPORTS.add(Point2d.of(72.5f, 54.5f));
            STARPORTS.add(Point2d.of(65.5f, 57.5f));
            STARPORTS.add(Point2d.of(60.5f, 66.5f));
            STARPORTS.add(Point2d.of(62.5f, 69.5f));
            STARPORTS.add(Point2d.of(67.5f, 69.5f));
            STARPORTS.add(Point2d.of(74.5f, 67.5f));
            STARPORTS.add(Point2d.of(80.5f, 67.5f));
            STARPORTS.add(Point2d.of(86.5f, 67.5f));
            STARPORTS.add(Point2d.of(89.5f, 69.5f));
            STARPORTS.add(Point2d.of(84.5f, 70.5f));
            STARPORTS.add(Point2d.of(83.5f, 73.5f));
            STARPORTS.add(Point2d.of(60.5f, 83.5f));
            STARPORTS.add(Point2d.of(74.5f, 73.5f));  //for tanks

            TURRETS.add(Point2d.of(75.0f, 59.0f));
            TURRETS.add(Point2d.of(69.0f, 64.0f));
            TURRETS.add(Point2d.of(71.0f, 59.0f));

            MACRO_OCS.add(Point2d.of(65.5f, 73.5f));
            MACRO_OCS.add(Point2d.of(70.5f, 73.5f));
            MACRO_OCS.add(Point2d.of(79.5f, 63.5f));
            MACRO_OCS.add(Point2d.of(85.5f, 63.5f));
            MACRO_OCS.add(Point2d.of(86.5f, 58.5f));

            extraDepots.add(Point2d.of(92.0f, 71.0f)); //reaperJump1
            extraDepots.add(Point2d.of(88.0f, 75.0f)); //reaperJump3
            extraDepots.add(Point2d.of(87.0f, 80.0f)); //reaperJump5
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(83.0f, 56.0f));
            extraDepots.add(Point2d.of(83.0f, 58.0f));
            extraDepots.add(Point2d.of(83.0f, 60.0f));
            extraDepots.add(Point2d.of(75.0f, 56.0f));
            extraDepots.add(Point2d.of(70.0f, 56.0f));
            extraDepots.add(Point2d.of(69.0f, 67.0f));
            extraDepots.add(Point2d.of(64.0f, 60.0f));
            extraDepots.add(Point2d.of(62.0f, 73.0f));
            extraDepots.add(Point2d.of(66.0f, 77.0f));
            extraDepots.add(Point2d.of(70.0f, 77.0f));
            extraDepots.add(Point2d.of(68.0f, 78.0f));
            extraDepots.add(Point2d.of(77.0f, 69.0f));
        }

    }

    private static void setLocationsForNightshade(boolean isTopPos) {
        numReaperWall = 3;
        if (isTopPos) {
            myMineralPos = Point2d.of(34f, 139.5f);
            enemyMineralPos = Point2d.of(158f, 32.5f);
            //REPAIR_BAY = Point2d.of(32.5f, 121.5f);
            WALL_2x2 = Point2d.of(42f, 123f);
            MID_WALL_2x2 = Point2d.of(40f, 123f);
            MID_WALL_3x3 = Point2d.of(39.5f, 123.5f);
            WALL_3x3 = Point2d.of(38.5f, 120.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(32.5f, 125.5f));
            _3x3Structures.add(Point2d.of(49.5f, 146.5f));

            BUNKER_NATURAL = Point2d.of(52.5f, 113.5f);
            //REAPER_JUMP1 = Point2d.of(52.0f, 126.0f);
            REAPER_JUMP2 = Point2d.of(54.5f, 128.5f);
            //REAPER_JUMP3 = Point2d.of(51.0f, 124.0f);
            FACTORY = Point2d.of(44.5f, 124.5f);

            STARPORTS.add(Point2d.of(45.5f, 150.5f));
            STARPORTS.add(Point2d.of(42.5f, 148.5f));
            STARPORTS.add(Point2d.of(36.5f, 147.5f));
            STARPORTS.add(Point2d.of(33.5f, 145.5f));
            STARPORTS.add(Point2d.of(30.5f, 138.5f));
            STARPORTS.add(Point2d.of(30.5f, 132.5f));
            STARPORTS.add(Point2d.of(30.5f, 129.5f));
            STARPORTS.add(Point2d.of(53.5f, 131.5f));
            STARPORTS.add(Point2d.of(56.5f, 133.5f));
            STARPORTS.add(Point2d.of(47.5f, 126.5f));
            STARPORTS.add(Point2d.of(39.5f, 134.5f));
            STARPORTS.add(Point2d.of(36.5f, 131.5f));
            STARPORTS.add(Point2d.of(47.5f, 129.5f));
            STARPORTS.add(Point2d.of(33.5f, 113.5f));
            STARPORTS.add(Point2d.of(36.5f, 128.5f));

            TURRETS.add(Point2d.of(46.0f, 143.0f));
            TURRETS.add(Point2d.of(37.0f, 138.0f));
            TURRETS.add(Point2d.of(43.0f, 143.0f));

            MACRO_OCS.add(Point2d.of(46.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(53.5f, 146.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 136.5f));
            MACRO_OCS.add(Point2d.of(58.5f, 143.5f));
            MACRO_OCS.add(Point2d.of(58.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(42.5f, 128.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 133.5f));

            extraDepots.add(Point2d.of(52.0f, 126.0f)); //reaperJump1
            extraDepots.add(Point2d.of(51.0f, 124.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(34.0f, 123.0f));
            extraDepots.add(Point2d.of(32.0f, 123.0f));
            extraDepots.add(Point2d.of(32.0f, 121.0f));
            extraDepots.add(Point2d.of(34.0f, 121.0f));
            extraDepots.add(Point2d.of(37.0f, 118.0f));
            extraDepots.add(Point2d.of(39.0f, 125.0f));
            extraDepots.add(Point2d.of(29.0f, 136.0f));
            extraDepots.add(Point2d.of(31.0f, 136.0f));
            extraDepots.add(Point2d.of(43.0f, 146.0f));
            extraDepots.add(Point2d.of(33.0f, 141.0f));
            extraDepots.add(Point2d.of(31.0f, 144.0f));
            extraDepots.add(Point2d.of(33.0f, 143.0f));
            extraDepots.add(Point2d.of(49.0f, 144.0f));
            extraDepots.add(Point2d.of(62.0f, 141.0f)); //drop turret TODO: test location
        }
        else {
            myMineralPos = Point2d.of(158f, 32.5f);
            enemyMineralPos = Point2d.of(34f, 139.5f);
            //REPAIR_BAY = Point2d.of(158.5f, 49.5f);

            WALL_2x2 = Point2d.of(153.0f, 52.0f);
            WALL_3x3 = Point2d.of(150.5f, 48.5f);
            MID_WALL_2x2 = Point2d.of(153f, 50f);
            MID_WALL_3x3 = Point2d.of(153.5f, 49.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(161.5f, 34.5f));
            _3x3Structures.add(Point2d.of(142.5f, 26.5f));

            //REAPER_JUMP1 = Point2d.of(140.0f, 46.0f);
            REAPER_JUMP2 = Point2d.of(137.5f, 43.5f);
            //REAPER_JUMP3 = Point2d.of(141.0f, 48.0f);
            BUNKER_NATURAL = Point2d.of(138.5f, 58.5f);
            FACTORY = Point2d.of(156.5f, 44.5f);

            STARPORTS.add(Point2d.of(145.5f, 41.5f));
            STARPORTS.add(Point2d.of(160.5f, 38.5f));
            STARPORTS.add(Point2d.of(152.5f, 37.5f));
            STARPORTS.add(Point2d.of(155.5f, 39.5f));
            STARPORTS.add(Point2d.of(147.5f, 38.5f));
            STARPORTS.add(Point2d.of(150.5f, 40.5f));
            STARPORTS.add(Point2d.of(157.5f, 26.5f));
            STARPORTS.add(Point2d.of(139.5f, 39.5f));
            STARPORTS.add(Point2d.of(152.5f, 24.5f));
            STARPORTS.add(Point2d.of(147.5f, 22.5f));
            STARPORTS.add(Point2d.of(142.5f, 23.5f));
            STARPORTS.add(Point2d.of(156.5f, 58.5f));
            STARPORTS.add(Point2d.of(148.5f, 43.5f));
            STARPORTS.add(Point2d.of(153.5f, 42.5f));
            STARPORTS.add(Point2d.of(158.5f, 41.5f));


            TURRETS.add(Point2d.of(146.0f, 29.0f));
            TURRETS.add(Point2d.of(155.0f, 34.0f));
            TURRETS.add(Point2d.of(149.0f, 29.0f));

            MACRO_OCS.add(Point2d.of(145.5f, 33.5f));
            MACRO_OCS.add(Point2d.of(133.5f, 28.5f));
            MACRO_OCS.add(Point2d.of(133.5f, 33.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 38.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 25.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 30.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 35.5f));

            extraDepots.add(Point2d.of(140.0f, 46.0f)); //reaperJump1
            extraDepots.add(Point2d.of(141.0f, 48.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(130.0f, 31.0f));
            extraDepots.add(Point2d.of(137.0f, 22.0f));
            extraDepots.add(Point2d.of(143.0f, 29.0f));
            extraDepots.add(Point2d.of(161.0f, 32.0f));
            extraDepots.add(Point2d.of(160.0f, 30.0f));
            extraDepots.add(Point2d.of(161.0f, 28.0f));
            extraDepots.add(Point2d.of(146.0f, 44.0f));
            extraDepots.add(Point2d.of(144.0f, 44.0f));
            extraDepots.add(Point2d.of(145.0f, 39.0f));
            extraDepots.add(Point2d.of(159.0f, 34.0f));
            extraDepots.add(Point2d.of(149.0f, 26.0f));
            extraDepots.add(Point2d.of(155.0f, 27.0f));
            extraDepots.add(Point2d.of(130.0f, 29.0f)); //drop turret
        }
    }

    private static void setLocationsForPillarsOfGold(boolean isTopPos) {

    }

    private static void setLocationsForSimulacrum(boolean isTopPos) {
        numReaperWall = 3;
        if (isTopPos) {
            myMineralPos = Point2d.of(47f, 140.5f);
            enemyMineralPos = Point2d.of(169f, 43.5f);
            //REPAIR_BAY = Point2d.of(53.5f, 133.5f);
            WALL_2x2 = Point2d.of(58.0f, 127.0f);
            MID_WALL_2x2 = Point2d.of(60f, 127f);
            MID_WALL_3x3 = Point2d.of(60.5f, 127.5f);
            WALL_3x3 = Point2d.of(61.5f, 124.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(43.5f, 142.5f));
            _3x3Structures.add(Point2d.of(66.5f, 137.5f));

            BUNKER_NATURAL = Point2d.of(58.5f, 105.5f);
            //REAPER_JUMP1 = Point2d.of(79.0f, 134.0f);
            REAPER_JUMP2 = Point2d.of(76.5f, 132.5f);
            //REAPER_JUMP3 = Point2d.of(80.0f, 136.0f);
            FACTORY = Point2d.of(54.5f, 132.5f);

            STARPORTS.add(Point2d.of(43.5f, 145.5f));
            STARPORTS.add(Point2d.of(43.5f, 139.5f));
            STARPORTS.add(Point2d.of(43.5f, 136.5f));
            STARPORTS.add(Point2d.of(43.5f, 133.5f));
            STARPORTS.add(Point2d.of(49.5f, 133.5f));
            STARPORTS.add(Point2d.of(49.5f, 136.5f));
            STARPORTS.add(Point2d.of(47.5f, 148.5f));
            STARPORTS.add(Point2d.of(50.5f, 150.5f));
            STARPORTS.add(Point2d.of(56.5f, 149.5f));
            STARPORTS.add(Point2d.of(61.5f, 148.5f));
            STARPORTS.add(Point2d.of(65.5f, 145.5f));
            STARPORTS.add(Point2d.of(65.5f, 141.5f));
            STARPORTS.add(Point2d.of(60.5f, 139.5f));
            STARPORTS.add(Point2d.of(60.5f, 136.5f));
            STARPORTS.add(Point2d.of(39.5f, 119.5f));

            TURRETS.add(Point2d.of(58.0f, 144.0f));
            TURRETS.add(Point2d.of(50.0f, 139.0f));
            TURRETS.add(Point2d.of(55.0f, 144.0f));

            MACRO_OCS.add(Point2d.of(49.5f, 128.5f));
            MACRO_OCS.add(Point2d.of(72.5f, 148.5f));
            MACRO_OCS.add(Point2d.of(71.5f, 142.5f));
            MACRO_OCS.add(Point2d.of(76.5f, 143.5f));
            MACRO_OCS.add(Point2d.of(70.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(76.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(70.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(64.5f, 131.5f));
            MACRO_OCS.add(Point2d.of(65.5f, 125.5f));

            extraDepots.add(Point2d.of(79.0f, 134.0f)); //reaperJump1
            extraDepots.add(Point2d.of(80.0f, 136.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(55.0f, 147.0f));
            extraDepots.add(Point2d.of(53.0f, 148.0f));
            extraDepots.add(Point2d.of(46.0f, 142.0f));
            extraDepots.add(Point2d.of(48.0f, 146.0f));
            extraDepots.add(Point2d.of(44.0f, 131.0f));
            extraDepots.add(Point2d.of(46.0f, 129.0f));
            extraDepots.add(Point2d.of(46.0f, 131.0f));
            extraDepots.add(Point2d.of(68.0f, 143.0f));
            extraDepots.add(Point2d.of(69.0f, 148.0f));
            extraDepots.add(Point2d.of(67.0f, 148.0f));
            extraDepots.add(Point2d.of(61.0f, 133.0f));
            extraDepots.add(Point2d.of(67.0f, 135.0f));
            extraDepots.add(Point2d.of(65.0f, 135.0f));
            extraDepots.add(Point2d.of(80.0f, 139.0f)); //drop turret
        }
        else {
            myMineralPos = Point2d.of(169f, 43.5f);
            enemyMineralPos = Point2d.of(47f, 140.5f);
            //REPAIR_BAY = Point2d.of(156.5f, 45.5f);

            WALL_2x2 = Point2d.of(158.0f, 57.0f);
            WALL_3x3 = Point2d.of(154.5f, 59.5f);
            MID_WALL_2x2 = Point2d.of(156f, 57f);
            MID_WALL_3x3 = Point2d.of(155.5f, 56.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(159.5f, 51.5f));
            _3x3Structures.add(Point2d.of(172.5f, 42.5f));

            //REAPER_JUMP1 = Point2d.of(137.0f, 50.0f);
            REAPER_JUMP2 = Point2d.of(139.5f, 51.5f);
            //REAPER_JUMP3 = Point2d.of(136.0f, 48.0f);
            BUNKER_NATURAL = Point2d.of(157.5f, 78.5f);
            FACTORY = Point2d.of(151.5f, 52.5f);

            STARPORTS.add(Point2d.of(170.5f, 38.5f));
            STARPORTS.add(Point2d.of(156.5f, 33.5f));
            STARPORTS.add(Point2d.of(161.5f, 33.5f));
            STARPORTS.add(Point2d.of(151.5f, 37.5f));
            STARPORTS.add(Point2d.of(164.5f, 35.5f));
            STARPORTS.add(Point2d.of(170.5f, 45.5f));
            STARPORTS.add(Point2d.of(164.5f, 48.5f));
            STARPORTS.add(Point2d.of(164.5f, 51.5f));
            STARPORTS.add(Point2d.of(159.5f, 48.5f));
            STARPORTS.add(Point2d.of(162.5f, 54.5f));
            STARPORTS.add(Point2d.of(167.5f, 54.5f));
            STARPORTS.add(Point2d.of(148.5f, 42.5f));
            STARPORTS.add(Point2d.of(149.5f, 46.5f));
            STARPORTS.add(Point2d.of(150.5f, 49.5f));
            STARPORTS.add(Point2d.of(174.5f, 64.5f));

            TURRETS.add(Point2d.of(158.0f, 40.0f));
            TURRETS.add(Point2d.of(166.0f, 45.0f));
            TURRETS.add(Point2d.of(161.0f, 40.0f));

            MACRO_OCS.add(Point2d.of(141.5f, 36.5f));
            MACRO_OCS.add(Point2d.of(147.5f, 37.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(144.5f, 42.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 46.5f));
            MACRO_OCS.add(Point2d.of(145.5f, 47.5f));
            MACRO_OCS.add(Point2d.of(146.5f, 52.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 58.5f));
            MACRO_OCS.add(Point2d.of(171.5f, 50.5f));

            extraDepots.add(Point2d.of(137.0f, 50.0f)); //reaperJump1
            extraDepots.add(Point2d.of(136.0f, 48.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(162.0f, 36.0f));
            extraDepots.add(Point2d.of(170.0f, 42.0f));
            extraDepots.add(Point2d.of(173.0f, 47.0f));
            extraDepots.add(Point2d.of(154.0f, 35.0f));
            extraDepots.add(Point2d.of(156.0f, 36.0f));
            extraDepots.add(Point2d.of(156.0f, 38.0f));
            extraDepots.add(Point2d.of(154.0f, 39.0f));
            extraDepots.add(Point2d.of(162.0f, 50.0f));
            extraDepots.add(Point2d.of(162.0f, 52.0f));
            extraDepots.add(Point2d.of(138.0f, 37.0f));
            extraDepots.add(Point2d.of(145.0f, 34.0f));
            extraDepots.add(Point2d.of(151.0f, 44.0f));
            extraDepots.add(Point2d.of(153.0f, 44.0f));
            extraDepots.add(Point2d.of(136.0f, 45.0f)); //drop turret

        }
    }

    private static void setLocationsForSubmarine(boolean isTopPos) {

    }

    private static void setLocationsForThunderBird(boolean isTopPos) {
        numReaperWall = 1;
        if (isTopPos) {
            myMineralPos = Point2d.of(38.0f, 140.5f);
            enemyMineralPos = Point2d.of(154.0f, 15.5f);
            //REPAIR_BAY = Point2d.of(41.5f, 122.5f);
            WALL_2x2 = Point2d.of(37.0f, 118.0f);
            MID_WALL_3x3 = Point2d.of(36.5f, 120.5f);
            WALL_3x3 = Point2d.of(39.5f, 121.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(42.5f, 128.5f));
            _3x3Structures.add(Point2d.of(28.5f, 136.5f));


            BUNKER_NATURAL = Point2d.of(52.5f, 106.5f);
            //REAPER_JUMP1 = Point2d.of(54.0f, 123.0f); //moved to top of extraDepots
            FACTORY = Point2d.of(28.5f, 125.5f);

            STARPORTS.add(Point2d.of(42.5f, 145.5f));
            STARPORTS.add(Point2d.of(45.5f, 137.5f));
            STARPORTS.add(Point2d.of(45.5f, 141.5f));
            STARPORTS.add(Point2d.of(39.5f, 126.5f));
            STARPORTS.add(Point2d.of(51.5f, 141.5f));
            STARPORTS.add(Point2d.of(28.5f, 128.5f));
            STARPORTS.add(Point2d.of(33.5f, 143.5f));
            STARPORTS.add(Point2d.of(39.5f, 143.5f));
            STARPORTS.add(Point2d.of(34.5f, 127.5f));
            STARPORTS.add(Point2d.of(34.5f, 124.5f));
            STARPORTS.add(Point2d.of(37.5f, 129.5f));
            STARPORTS.add(Point2d.of(32.5f, 110.5f));
            STARPORTS.add(Point2d.of(29.5f, 103.5f));

            TURRETS.add(Point2d.of(42.0f, 138.0f));
            TURRETS.add(Point2d.of(34.0f, 133.0f));
            TURRETS.add(Point2d.of(36.0f, 141.0f));

            MACRO_OCS.add(Point2d.of(43.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(48.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 127.5f));
            MACRO_OCS.add(Point2d.of(53.5f, 127.5f));
            MACRO_OCS.add(Point2d.of(54.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(56.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(51.5f, 137.5f));


            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(54.0f, 123.0f)); //reaperJump1
            extraDepots.add(Point2d.of(58.0f, 132.0f));
            extraDepots.add(Point2d.of(56.0f, 141.0f));
            extraDepots.add(Point2d.of(51.0f, 144.0f));
            extraDepots.add(Point2d.of(49.0f, 144.0f));
            extraDepots.add(Point2d.of(47.0f, 144.0f));
            extraDepots.add(Point2d.of(48.0f, 139.0f));
            extraDepots.add(Point2d.of(40.0f, 141.0f));
            extraDepots.add(Point2d.of(27.0f, 131.0f));
            extraDepots.add(Point2d.of(29.0f, 131.0f));
            extraDepots.add(Point2d.of(28.0f, 133.0f));
            extraDepots.add(Point2d.of(30.0f, 133.0f));
            extraDepots.add(Point2d.of(40.0f, 146.0f));
            extraDepots.add(Point2d.of(30.0f, 119.0f));
            extraDepots.add(Point2d.of(58.0f, 134.0f)); //drop turret

        }
        else {
            myMineralPos = Point2d.of(154.0f, 15.5f);
            enemyMineralPos = Point2d.of(38.0f, 140.5f);
            //REPAIR_BAY = Point2d.of(151.5f, 30f);
            WALL_2x2 = Point2d.of(155.0f, 38.0f);
            MID_WALL_3x3 = Point2d.of(155.5f, 35.5f);
            WALL_3x3 = Point2d.of(152.5f, 34.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(161.5f, 36.5f));
            _3x3Structures.add(Point2d.of(163.5f, 19.5f));

            BUNKER_NATURAL = Point2d.of(140.5f, 49.5f);
            //REAPER_JUMP1 = Point2d.of(139.0f, 34.0f);
            FACTORY = Point2d.of(160.5f, 30.5f);

            STARPORTS.add(Point2d.of(159.5f, 15.5f));
            STARPORTS.add(Point2d.of(156.5f, 13.5f));
            STARPORTS.add(Point2d.of(151.5f, 12.5f));
            STARPORTS.add(Point2d.of(146.5f, 12.5f));
            STARPORTS.add(Point2d.of(154.5f, 26.5f));
            STARPORTS.add(Point2d.of(157.5f, 28.5f));
            STARPORTS.add(Point2d.of(142.5f, 25.5f));
            STARPORTS.add(Point2d.of(137.5f, 28.5f));
            STARPORTS.add(Point2d.of(137.5f, 31.5f));
            STARPORTS.add(Point2d.of(143.5f, 29.5f));
            STARPORTS.add(Point2d.of(146.5f, 31.5f));
            STARPORTS.add(Point2d.of(161.5f, 53.5f)); //at nat
            STARPORTS.add(Point2d.of(157.5f, 45.5f)); //at nat
            STARPORTS.add(Point2d.of(136.5f, 25.5f));
            STARPORTS.add(Point2d.of(155.5f, 31.5f));

            TURRETS.add(Point2d.of(149.0f, 18.0f));
            TURRETS.add(Point2d.of(158.0f, 23.0f));
            TURRETS.add(Point2d.of(154.0f, 14.0f));

            MACRO_OCS.add(Point2d.of(148.5f, 22.5f));
            MACRO_OCS.add(Point2d.of(149.5f, 27.5f));
            MACRO_OCS.add(Point2d.of(143.5f, 21.5f));
            MACRO_OCS.add(Point2d.of(137.5f, 16.5f));
            MACRO_OCS.add(Point2d.of(142.5f, 16.5f));
            MACRO_OCS.add(Point2d.of(137.5f, 21.5f));
            MACRO_OCS.add(Point2d.of(142.5f, 34.5f));

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(139.0f, 34.0f)); //reaperJump1
            extraDepots.add(Point2d.of(134.0f, 17.0f));
            extraDepots.add(Point2d.of(134.0f, 19.0f));
            extraDepots.add(Point2d.of(134.0f, 23.0f));
            extraDepots.add(Point2d.of(134.0f, 25.0f));
            extraDepots.add(Point2d.of(153.0f, 10.0f));
            extraDepots.add(Point2d.of(149.0f, 10.0f));
            extraDepots.add(Point2d.of(147.0f, 10.0f));
            extraDepots.add(Point2d.of(151.0f, 10.0f));
            extraDepots.add(Point2d.of(144.0f, 12.0f));
            extraDepots.add(Point2d.of(140.0f, 13.0f));
            extraDepots.add(Point2d.of(142.0f, 12.0f));
            extraDepots.add(Point2d.of(152.0f, 15.0f));
            extraDepots.add(Point2d.of(162.0f, 17.0f));
            extraDepots.add(Point2d.of(164.0f, 23.0f));
            extraDepots.add(Point2d.of(162.0f, 23.0f));
            extraDepots.add(Point2d.of(146.0f, 15.0f));
            extraDepots.add(Point2d.of(146.0f, 17.0f));
            extraDepots.add(Point2d.of(147.0f, 19.0f));
            extraDepots.add(Point2d.of(134.0f, 21.0f)); //drop turret
        }
    }

    private static void setLocationsForTriton(boolean isTopPos) {
        numReaperWall = 2;
        if (isTopPos) {
            myMineralPos = Point2d.of(48f, 158.5f);
            enemyMineralPos = Point2d.of(168f, 45.5f);
            //REPAIR_BAY = Point2d.of(69.0f, 159.0f);
            //REAPER_JUMP1 = Point2d.of(69f, 144f);
            REAPER_JUMP2 = Point2d.of(71.5f, 144.5f);
            BUNKER_NATURAL = Point2d.of(87.5f, 152.5f);
            WALL_2x2 = Point2d.of(71.0f, 154.0f);
            WALL_3x3 = Point2d.of(73.5f, 150.5f);
            MID_WALL_2x2 = Point2d.of(71f, 152f);
            MID_WALL_3x3 = Point2d.of(70.5f, 151.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(53.5f, 169.5f));
            _3x3Structures.add(Point2d.of(44.5f, 149.5f));

            STARPORTS.add(Point2d.of(67.5f, 163.5f));
            STARPORTS.add(Point2d.of(64.5f, 161.5f));
            STARPORTS.add(Point2d.of(62.5f, 164.5f));
            STARPORTS.add(Point2d.of(58.5f, 168.5f));
            STARPORTS.add(Point2d.of(53.5f, 166.5f));
            STARPORTS.add(Point2d.of(47.5f, 164.5f));
            STARPORTS.add(Point2d.of(47.5f, 167.5f));
            STARPORTS.add(Point2d.of(42.5f, 162.5f));
            STARPORTS.add(Point2d.of(44.5f, 157.5f));
            STARPORTS.add(Point2d.of(45.5f, 152.5f));
            STARPORTS.add(Point2d.of(47.5f, 149.5f));
            STARPORTS.add(Point2d.of(47.5f, 146.5f));
            STARPORTS.add(Point2d.of(47.5f, 143.5f));
            STARPORTS.add(Point2d.of(51.5f, 153.5f));
            STARPORTS.add(Point2d.of(53.5f, 150.5f));

            TURRETS.add(Point2d.of(51.0f, 154.0f));
            TURRETS.add(Point2d.of(57.0f, 162.0f));
            TURRETS.add(Point2d.of(51.0f, 157.0f));

            MACRO_OCS.add(Point2d.of(60.5f, 157.5f));
            MACRO_OCS.add(Point2d.of(59.5f, 152.5f));
            MACRO_OCS.add(Point2d.of(54.5f, 146.5f));
            MACRO_OCS.add(Point2d.of(54.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(65.5f, 156.5f));
            MACRO_OCS.add(Point2d.of(65.5f, 151.5f));
            MACRO_OCS.add(Point2d.of(65.5f, 146.5f));
            MACRO_OCS.add(Point2d.of(59.5f, 147.5f));
            MACRO_OCS.add(Point2d.of(59.5f, 141.5f));

            extraDepots.add(Point2d.of(69f, 144f)); //Reaper Jump1
            extraDepots.add(WALL_2x2); //wall 2x2
            extraDepots.add(Point2d.of(70.0f, 157.0f));
            extraDepots.add(Point2d.of(70.0f, 159.0f));
            extraDepots.add(Point2d.of(70.0f, 161.0f));
            extraDepots.add(Point2d.of(42.0f, 151.0f));
            extraDepots.add(Point2d.of(42.0f, 149.0f));
            extraDepots.add(Point2d.of(65.0f, 166.0f));
            extraDepots.add(Point2d.of(67.0f, 166.0f));
            extraDepots.add(Point2d.of(65.0f, 168.0f));
            extraDepots.add(Point2d.of(63.0f, 168.0f));
            extraDepots.add(Point2d.of(56.0f, 170.0f));
            extraDepots.add(Point2d.of(56.0f, 168.0f));
            extraDepots.add(Point2d.of(45.0f, 166.0f));
            extraDepots.add(Point2d.of(45.0f, 164.0f));
            extraDepots.add(Point2d.of(43.0f, 160.0f));
            extraDepots.add(Point2d.of(44.0f, 155.0f));
            extraDepots.add(Point2d.of(46.0f, 155.0f));
        }
        else {
            myMineralPos = Point2d.of(168f, 45.5f);
            enemyMineralPos = Point2d.of(48f, 158.5f);
            //REPAIR_BAY = Point2d.of(146.5f, 44.0f);
            //REAPER_JUMP1 = Point2d.of(143f, 60f);
            REAPER_JUMP2 = Point2d.of(145.5f, 59.5f);
            BUNKER_NATURAL = Point2d.of(128.5f, 51.5f);
            WALL_2x2 = Point2d.of(145.0f, 50.0f);
            WALL_3x3 = Point2d.of(142.5f, 53.5f);
            MID_WALL_2x2 = Point2d.of(145f, 52f);
            MID_WALL_3x3 = Point2d.of(145.5f, 52.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(171.5f, 48.5f));
            _3x3Structures.add(Point2d.of(160.5f, 51.5f));

            STARPORTS.add(Point2d.of(152.5f, 36.5f));
            STARPORTS.add(Point2d.of(159.5f, 37.5f));
            STARPORTS.add(Point2d.of(158.5f, 34.5f));
            STARPORTS.add(Point2d.of(165.5f, 36.5f));
            STARPORTS.add(Point2d.of(165.5f, 39.5f));
            STARPORTS.add(Point2d.of(170.5f, 44.5f));
            STARPORTS.add(Point2d.of(170.5f, 41.5f));
            STARPORTS.add(Point2d.of(170.5f, 52.5f));
            STARPORTS.add(Point2d.of(170.5f, 55.5f));
            STARPORTS.add(Point2d.of(164.5f, 52.5f));
            STARPORTS.add(Point2d.of(164.5f, 55.5f));
            STARPORTS.add(Point2d.of(164.5f, 58.5f));
            STARPORTS.add(Point2d.of(164.5f, 58.5f));
            STARPORTS.add(Point2d.of(164.5f, 61.5f));
            STARPORTS.add(Point2d.of(158.5f, 60.5f));
            STARPORTS.add(Point2d.of(158.5f, 63.5f));

            TURRETS.add(Point2d.of(165.0f, 50.0f));
            TURRETS.add(Point2d.of(159.0f, 42.0f));
            TURRETS.add(Point2d.of(163.0f, 42.0f));

            MACRO_OCS.add(Point2d.of(150.5f, 45.5f));
            MACRO_OCS.add(Point2d.of(149.5f, 40.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 50.5f));
            MACRO_OCS.add(Point2d.of(155.5f, 46.5f));
            MACRO_OCS.add(Point2d.of(157.5f, 56.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 55.5f));
            MACRO_OCS.add(Point2d.of(156.5f, 51.5f));
            MACRO_OCS.add(Point2d.of(152.5f, 60.5f));

            extraDepots.add(Point2d.of(143f, 60f)); //reaper jump1
            extraDepots.add(WALL_2x2); //wall 2x2
            extraDepots.add(Point2d.of(153.0f, 39.0f));
            extraDepots.add(Point2d.of(153.0f, 41.0f));
            extraDepots.add(Point2d.of(146.0f, 47.0f));
            extraDepots.add(Point2d.of(146.0f, 45.0f));
            extraDepots.add(Point2d.of(146.0f, 43.0f));
            extraDepots.add(Point2d.of(146.0f, 41.0f));
            extraDepots.add(Point2d.of(150.0f, 37.0f));
            extraDepots.add(Point2d.of(157.0f, 37.0f));
            extraDepots.add(Point2d.of(167.0f, 63.0f));
            extraDepots.add(Point2d.of(170.0f, 61.0f));
            extraDepots.add(Point2d.of(170.0f, 59.0f));
            extraDepots.add(Point2d.of(161.0f, 57.0f));
        }
    }

    private static void setLocationsForWintersGate(boolean isTopPos) {
        numReaperWall = 2;
        if (isTopPos) {
            myMineralPos = Point2d.of(47f, 141.5f);
            enemyMineralPos = Point2d.of(145f, 22.5f);
            //REPAIR_BAY = Point2d.of(50.5f, 124.5f);
            WALL_2x2 = Point2d.of(52.0f, 121.0f);
            MID_WALL_2x2 = Point2d.of(54f, 121f);
            MID_WALL_3x3 = Point2d.of(54.5f, 121.5f);
            WALL_3x3 = Point2d.of(55.5f, 118.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(43.5f, 129.5f));
            _3x3Structures.add(Point2d.of(55.5f, 140.5f));


            BUNKER_NATURAL = Point2d.of(49.5f, 103.5f);
            //REAPER_JUMP1 = Point2d.of(68.0f, 125.0f);
            REAPER_JUMP2 = Point2d.of(65.5f, 124.5f);

            STARPORTS.add(Point2d.of(36.5f, 134.5f));
            STARPORTS.add(Point2d.of(36.5f, 139.5f));
            STARPORTS.add(Point2d.of(39.5f, 143.5f));
            STARPORTS.add(Point2d.of(45.5f, 143.5f));
            STARPORTS.add(Point2d.of(51.5f, 144.5f));
            STARPORTS.add(Point2d.of(38.5f, 123.5f));
            STARPORTS.add(Point2d.of(38.5f, 127.5f));
            STARPORTS.add(Point2d.of(52.5f, 136.5f));
            STARPORTS.add(Point2d.of(52.5f, 133.5f));
            STARPORTS.add(Point2d.of(55.5f, 130.5f));
            STARPORTS.add(Point2d.of(64.5f, 139.5f));
            STARPORTS.add(Point2d.of(65.5f, 136.5f));
            STARPORTS.add(Point2d.of(66.5f, 133.5f));
            STARPORTS.add(Point2d.of(66.5f, 130.5f));
            STARPORTS.add(Point2d.of(66.5f, 127.5f));
            STARPORTS.add(Point2d.of(52.5f, 128.5f));

            TURRETS.add(Point2d.of(52.0f, 139.0f));
            TURRETS.add(Point2d.of(43.0f, 134.0f));
            TURRETS.add(Point2d.of(45.0f, 139.0f));

            MACRO_OCS.add(Point2d.of(47.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(44.5f, 124.5f));
            MACRO_OCS.add(Point2d.of(59.5f, 142.5f));
            MACRO_OCS.add(Point2d.of(59.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(62.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(61.5f, 127.5f));
            MACRO_OCS.add(Point2d.of(58.5f, 122.5f));

            extraDepots.add(Point2d.of(68.0f, 125.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(37.0f, 142.0f));
            extraDepots.add(Point2d.of(36.0f, 137.0f));
            extraDepots.add(Point2d.of(39.0f, 137.0f));
            extraDepots.add(Point2d.of(39.0f, 141.0f));
            extraDepots.add(Point2d.of(41.0f, 141.0f));
            extraDepots.add(Point2d.of(43.0f, 141.0f));
            extraDepots.add(Point2d.of(38.0f, 130.0f));
            extraDepots.add(Point2d.of(37.0f, 132.0f));
            extraDepots.add(Point2d.of(41.0f, 125.0f));
            extraDepots.add(Point2d.of(65.0f, 142.0f));
            extraDepots.add(Point2d.of(56.0f, 144.0f));
            extraDepots.add(Point2d.of(63.0f, 142.0f));
            extraDepots.add(Point2d.of(71.0f, 132.0f));
            extraDepots.add(Point2d.of(70.0f, 135.0f));
            extraDepots.add(Point2d.of(41.0f, 129.0f));
        }
        else {
            myMineralPos = Point2d.of(145f, 22.5f);
            enemyMineralPos = Point2d.of(47f, 141.5f);
            //REPAIR_BAY = Point2d.of(140.5f, 38.5f);
            WALL_2x2 = Point2d.of(140.0f, 43.0f);
            MID_WALL_2x2 = Point2d.of(138f, 43f);
            MID_WALL_3x3 = Point2d.of(137.5f, 42.5f);
            WALL_3x3 = Point2d.of(136.5f, 45.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(137.5f, 23.5f));
            _3x3Structures.add(Point2d.of(129.5f, 32.5f));

            BUNKER_NATURAL = Point2d.of(141.5f, 60.5f);
            //REAPER_JUMP1 = Point2d.of(124.0f, 39.0f);
            REAPER_JUMP2 = Point2d.of(126.5f, 39.5f);

            STARPORTS.add(Point2d.of(154.5f, 28.5f));
            STARPORTS.add(Point2d.of(153.5f, 24.5f));
            STARPORTS.add(Point2d.of(150.5f, 20.5f));
            STARPORTS.add(Point2d.of(144.5f, 20.5f));
            STARPORTS.add(Point2d.of(137.5f, 20.5f));
            STARPORTS.add(Point2d.of(125.5f, 24.5f));
            STARPORTS.add(Point2d.of(125.5f, 27.5f));
            STARPORTS.add(Point2d.of(123.5f, 33.5f));
            STARPORTS.add(Point2d.of(124.5f, 30.5f));
            STARPORTS.add(Point2d.of(123.5f, 36.5f));
            STARPORTS.add(Point2d.of(129.5f, 36.5f));
            STARPORTS.add(Point2d.of(130.5f, 39.5f));
            STARPORTS.add(Point2d.of(147.5f, 45.5f));
            STARPORTS.add(Point2d.of(153.5f, 47.5f));
            STARPORTS.add(Point2d.of(150.5f, 35.5f));
            STARPORTS.add(Point2d.of(136.5f, 38.5f));

            TURRETS.add(Point2d.of(140.0f, 25.0f));
            TURRETS.add(Point2d.of(149.0f, 30.0f));
            TURRETS.add(Point2d.of(147.0f, 22.0f));

            MACRO_OCS.add(Point2d.of(144.5f, 34.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 29.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 34.5f));
            MACRO_OCS.add(Point2d.of(147.5f, 39.5f));
            MACRO_OCS.add(Point2d.of(152.5f, 39.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 21.5f));
            MACRO_OCS.add(Point2d.of(133.5f, 31.5f));
            MACRO_OCS.add(Point2d.of(133.5f, 26.5f));

            extraDepots.add(Point2d.of(124.0f, 39.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(155.0f, 22.0f));
            extraDepots.add(Point2d.of(141.0f, 18.0f));
            extraDepots.add(Point2d.of(153.0f, 22.0f));
            extraDepots.add(Point2d.of(142.0f, 20.0f));
            extraDepots.add(Point2d.of(151.0f, 23.0f));
            extraDepots.add(Point2d.of(156.0f, 31.0f));
            extraDepots.add(Point2d.of(149.0f, 23.0f));
            extraDepots.add(Point2d.of(154.0f, 33.0f));
            extraDepots.add(Point2d.of(154.0f, 31.0f));
            extraDepots.add(Point2d.of(121.0f, 32.0f));
            extraDepots.add(Point2d.of(122.0f, 30.0f));
            extraDepots.add(Point2d.of(123.0f, 28.0f));
            extraDepots.add(Point2d.of(127.0f, 22.0f));
            extraDepots.add(Point2d.of(129.0f, 22.0f));
            extraDepots.add(Point2d.of(130.0f, 25.0f));
            extraDepots.add(Point2d.of(130.0f, 27.0f));
            extraDepots.add(Point2d.of(130.0f, 29.0f));
        }
    }

    private static void setLocationsForWorldOfSleepers(boolean isTopPos) {
        numReaperWall = 2;
        if (isTopPos) {
            myMineralPos = Point2d.of(150f, 148.5f);
            enemyMineralPos = Point2d.of(34f, 19.5f);
            //REPAIR_BAY = Point2d.of(145.5f, 130.5f);
            WALL_2x2 = Point2d.of(149.0f, 124.0f);
            MID_WALL_2x2 = Point2d.of(149f, 126f);
            MID_WALL_3x3 = Point2d.of(149.5f, 126.5f);
            WALL_3x3 = Point2d.of(146.5f, 127.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(159.5f, 139.5f));
            _3x3Structures.add(Point2d.of(139.5f, 153.5f));

            BUNKER_NATURAL = Point2d.of(137.5f, 106.5f);
            //REAPER_JUMP1 = Point2d.of(136.0f, 131.0f);
            REAPER_JUMP2 = Point2d.of(135.5f, 133.5f);

            STARPORTS.add(Point2d.of(159.5f, 143.5f));
            STARPORTS.add(Point2d.of(159.5f, 135.5f));
            STARPORTS.add(Point2d.of(159.5f, 147.5f));
            STARPORTS.add(Point2d.of(154.5f, 148.5f));
            STARPORTS.add(Point2d.of(154.5f, 152.5f));
            STARPORTS.add(Point2d.of(148.5f, 151.5f));
            STARPORTS.add(Point2d.of(143.5f, 152.5f));
            STARPORTS.add(Point2d.of(136.5f, 150.5f));
            STARPORTS.add(Point2d.of(139.5f, 147.5f));
            STARPORTS.add(Point2d.of(140.5f, 144.5f));
            STARPORTS.add(Point2d.of(141.5f, 141.5f));
            STARPORTS.add(Point2d.of(142.5f, 138.5f));
            STARPORTS.add(Point2d.of(142.5f, 135.5f));
            STARPORTS.add(Point2d.of(136.5f, 136.5f));
            STARPORTS.add(Point2d.of(138.5f, 132.5f));
            STARPORTS.add(Point2d.of(141.5f, 128.5f));

            TURRETS.add(Point2d.of(149.0f, 146.0f));
            TURRETS.add(Point2d.of(154.0f, 141.0f));
            TURRETS.add(Point2d.of(151.0f, 153.0f));

            MACRO_OCS.add(Point2d.of(149.5f, 136.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 146.5f));
            MACRO_OCS.add(Point2d.of(136.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(154.5f, 124.5f));
            MACRO_OCS.add(Point2d.of(158.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(153.5f, 131.5f));

            extraDepots.add(Point2d.of(136.0f, 131.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(162.0f, 145.0f));
            extraDepots.add(Point2d.of(159.0f, 150.0f));
            extraDepots.add(Point2d.of(157.0f, 150.0f));
            extraDepots.add(Point2d.of(152.0f, 149.0f));
            extraDepots.add(Point2d.of(148.0f, 149.0f));
            extraDepots.add(Point2d.of(141.0f, 150.0f));
            extraDepots.add(Point2d.of(143.0f, 150.0f));
            extraDepots.add(Point2d.of(162.0f, 133.0f));
            extraDepots.add(Point2d.of(158.0f, 133.0f));
            extraDepots.add(Point2d.of(160.0f, 133.0f));
            extraDepots.add(Point2d.of(133.0f, 142.0f));
            extraDepots.add(Point2d.of(138.0f, 145.0f));
            extraDepots.add(Point2d.of(157.0f, 136.0f));
            extraDepots.add(Point2d.of(155.0f, 136.0f));
        }
        else {
            myMineralPos = Point2d.of(34f, 19.5f);
            enemyMineralPos = Point2d.of(150f, 148.5f);
            //REPAIR_BAY = Point2d.of(36.5f, 35.5f);
            WALL_2x2 = Point2d.of(35.0f, 44.0f);
            MID_WALL_2x2 = Point2d.of(35f, 42f);
            MID_WALL_3x3 = Point2d.of(34.5f, 41.5f);
            WALL_3x3 = Point2d.of(37.5f, 40.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(45.5f, 34.5f));
            _3x3Structures.add(Point2d.of(24.5f, 29.5f));

            BUNKER_NATURAL = Point2d.of(46.5f, 61.5f);
            //REAPER_JUMP1 = Point2d.of(48.0f, 37.0f);
            REAPER_JUMP2 = Point2d.of(48.5f, 34.5f);

            STARPORTS.add(Point2d.of(22.5f, 33.5f));
            STARPORTS.add(Point2d.of(22.5f, 36.5f));
            STARPORTS.add(Point2d.of(24.5f, 40.5f));
            STARPORTS.add(Point2d.of(28.5f, 32.5f));
            STARPORTS.add(Point2d.of(28.5f, 35.5f));
            STARPORTS.add(Point2d.of(30.5f, 38.5f));
            STARPORTS.add(Point2d.of(22.5f, 24.5f));
            STARPORTS.add(Point2d.of(22.5f, 21.5f));
            STARPORTS.add(Point2d.of(29.5f, 19.5f));
            STARPORTS.add(Point2d.of(26.5f, 17.5f));
            STARPORTS.add(Point2d.of(39.5f, 16.5f));
            STARPORTS.add(Point2d.of(34.5f, 16.5f));
            STARPORTS.add(Point2d.of(43.5f, 24.5f));
            STARPORTS.add(Point2d.of(43.5f, 21.5f));
            STARPORTS.add(Point2d.of(49.5f, 21.5f));
            STARPORTS.add(Point2d.of(49.5f, 25.5f));

            TURRETS.add(Point2d.of(39.0f, 22.0f));
            TURRETS.add(Point2d.of(30.0f, 27.0f));
            TURRETS.add(Point2d.of(31.0f, 16.0f));

            MACRO_OCS.add(Point2d.of(39.5f, 26.5f));
            MACRO_OCS.add(Point2d.of(35.5f, 31.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(42.5f, 38.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 29.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 17.5f));
            MACRO_OCS.add(Point2d.of(29.5f, 43.5f));

            extraDepots.add(Point2d.of(48.0f, 37.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(24.0f, 19.0f));
            extraDepots.add(Point2d.of(33.0f, 14.0f));
            extraDepots.add(Point2d.of(31.0f, 14.0f));
            extraDepots.add(Point2d.of(29.0f, 14.0f));
            extraDepots.add(Point2d.of(43.0f, 14.0f));
            extraDepots.add(Point2d.of(41.0f, 14.0f));
            extraDepots.add(Point2d.of(45.0f, 14.0f));
            extraDepots.add(Point2d.of(49.0f, 18.0f));
            extraDepots.add(Point2d.of(42.0f, 18.0f));
            extraDepots.add(Point2d.of(41.0f, 20.0f));
            extraDepots.add(Point2d.of(24.0f, 27.0f));
            extraDepots.add(Point2d.of(26.0f, 27.0f));
            extraDepots.add(Point2d.of(27.0f, 21.0f));
            extraDepots.add(Point2d.of(36.0f, 19.0f));
        }
    }

    private static void setLocationsForZen(boolean isTopPos) {
        numReaperWall = 3;
        if (isTopPos) {
            myMineralPos = Point2d.of(150f, 140.5f);
            enemyMineralPos = Point2d.of(42f, 31.5f);
            //REPAIR_BAY = Point2d.of(139.5f, 135.5f);
            WALL_2x2 = Point2d.of(135.0f, 130.0f);
            MID_WALL_2x2 = Point2d.of(135f, 132f);
            MID_WALL_3x3 = Point2d.of(135.5f, 132.5f);
            WALL_3x3 = Point2d.of(132.5f, 133.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(136.5f, 146.5f));
            _3x3Structures.add(Point2d.of(128.5f, 135.5f));

            BUNKER_NATURAL = Point2d.of(126.5f, 117.5f);
            //REAPER_JUMP1 = Point2d.of(144.0f, 121.0f);
            REAPER_JUMP2 = Point2d.of(126.5f, 144.5f);
            //REAPER_JUMP3 = Point2d.of(126.0f, 147.0f);
            FACTORY = Point2d.of(144.5f, 134.5f);

            STARPORTS.add(Point2d.of(146.5f, 131.5f));
            STARPORTS.add(Point2d.of(153.5f, 138.5f));
            STARPORTS.add(Point2d.of(152.5f, 135.5f));
            STARPORTS.add(Point2d.of(128.5f, 148.5f));
            STARPORTS.add(Point2d.of(149.5f, 133.5f));
            STARPORTS.add(Point2d.of(151.5f, 130.5f));
            STARPORTS.add(Point2d.of(148.5f, 128.5f));
            STARPORTS.add(Point2d.of(145.5f, 126.5f));
            STARPORTS.add(Point2d.of(145.5f, 123.5f));
            STARPORTS.add(Point2d.of(152.5f, 142.5f));
            STARPORTS.add(Point2d.of(149.5f, 146.5f));
            STARPORTS.add(Point2d.of(142.5f, 148.5f));
            STARPORTS.add(Point2d.of(145.5f, 150.5f));
            STARPORTS.add(Point2d.of(137.5f, 149.5f));
            STARPORTS.add(Point2d.of(136.5f, 152.5f));

            TURRETS.add(Point2d.of(139.0f, 144.0f));
            TURRETS.add(Point2d.of(147.0f, 139.0f));
            TURRETS.add(Point2d.of(142.0f, 144.0f));

            MACRO_OCS.add(Point2d.of(137.5f, 139.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 143.5f));
            MACRO_OCS.add(Point2d.of(127.5f, 140.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 151.5f));
            MACRO_OCS.add(Point2d.of(141.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(125.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(120.5f, 126.5f));

            extraDepots.add(Point2d.of(144.0f, 121.0f)); //reaperJump1
            extraDepots.add(Point2d.of(126.0f, 147.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(143.0f, 151.0f));
            extraDepots.add(Point2d.of(141.0f, 151.0f));
            extraDepots.add(Point2d.of(147.0f, 147.0f));
            extraDepots.add(Point2d.of(151.0f, 139.0f));
            extraDepots.add(Point2d.of(155.0f, 133.0f));
            extraDepots.add(Point2d.of(154.0f, 128.0f));
            extraDepots.add(Point2d.of(152.0f, 126.0f));
            extraDepots.add(Point2d.of(150.0f, 126.0f));
            extraDepots.add(Point2d.of(150.0f, 124.0f));
            extraDepots.add(Point2d.of(129.0f, 151.0f));
            extraDepots.add(Point2d.of(126.0f, 149.0f));
            extraDepots.add(Point2d.of(129.0f, 144.0f));
            extraDepots.add(Point2d.of(129.0f, 146.0f));
            extraDepots.add(Point2d.of(133.0f,148.0f)); //turret
        }
        else {
            myMineralPos = Point2d.of(42f, 31.5f);
            enemyMineralPos = Point2d.of(150f, 140.5f);
            //REPAIR_BAY = Point2d.of(51.5f, 36.5f);

            WALL_2x2 = Point2d.of(60.0f, 39.0f);
            WALL_3x3 = Point2d.of(56.5f, 41.5f);
            MID_WALL_2x2 = Point2d.of(58f, 39f);
            MID_WALL_3x3 = Point2d.of(57.5f, 38.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(65.5f, 29.5f));
            _3x3Structures.add(Point2d.of(54.5f, 18.5f));

            //REAPER_JUMP1 = Point2d.of(48.0f, 51.0f);
            REAPER_JUMP2 = Point2d.of(65.5f, 24.5f);
            //REAPER_JUMP3 = Point2d.of(66.0f, 27.0f);
            BUNKER_NATURAL = Point2d.of(64.5f, 53.5f);
            FACTORY = Point2d.of(49.5f, 36.5f);

            STARPORTS.add(Point2d.of(36.5f, 37.5f));
            STARPORTS.add(Point2d.of(36.5f, 34.5f));
            STARPORTS.add(Point2d.of(37.5f, 40.5f));
            STARPORTS.add(Point2d.of(42.5f, 38.5f));
            STARPORTS.add(Point2d.of(40.5f, 43.5f));
            STARPORTS.add(Point2d.of(41.5f, 46.5f));
            STARPORTS.add(Point2d.of(47.5f, 46.5f));
            STARPORTS.add(Point2d.of(44.5f, 49.5f));
            STARPORTS.add(Point2d.of(37.5f, 31.5f));
            STARPORTS.add(Point2d.of(39.5f, 27.5f));
            STARPORTS.add(Point2d.of(46.5f, 20.5f));
            STARPORTS.add(Point2d.of(49.5f, 22.5f));
            STARPORTS.add(Point2d.of(44.5f, 23.5f));
            STARPORTS.add(Point2d.of(55.5f, 24.5f));

            TURRETS.add(Point2d.of(53.0f, 28.0f));
            TURRETS.add(Point2d.of(45.0f, 33.0f));
            TURRETS.add(Point2d.of(50.0f, 28.0f));

            MACRO_OCS.add(Point2d.of(58.5f, 19.5f));
            MACRO_OCS.add(Point2d.of(61.5f, 24.5f));
            MACRO_OCS.add(Point2d.of(59.5f, 29.5f));
            MACRO_OCS.add(Point2d.of(62.5f, 34.5f));
            MACRO_OCS.add(Point2d.of(54.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 42.5f));
            MACRO_OCS.add(Point2d.of(66.5f, 42.5f));
            MACRO_OCS.add(Point2d.of(71.5f, 45.5f));

            extraDepots.add(Point2d.of(48.0f, 51.0f)); //reaperJump1
            extraDepots.add(Point2d.of(66.0f, 27.0f)); //reaperJump3
            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(Point2d.of(44.0f, 41.0f));
            extraDepots.add(Point2d.of(42.0f, 41.0f));
            extraDepots.add(Point2d.of(37.0f, 43.0f));
            extraDepots.add(Point2d.of(46.0f, 52.0f));
            extraDepots.add(Point2d.of(41.0f, 33.0f));
            extraDepots.add(Point2d.of(66.0f, 33.0f));
            extraDepots.add(Point2d.of(63.0f, 28.0f));
            extraDepots.add(Point2d.of(63.0f, 30.0f));
            extraDepots.add(Point2d.of(65.0f, 22.0f));
            extraDepots.add(Point2d.of(55.0f, 21.0f));
            extraDepots.add(Point2d.of(59.0f, 33.0f));
            extraDepots.add(Point2d.of(56.0f, 29.0f));
            extraDepots.add(Point2d.of(63.0f, 21.0f)); //turret drop position
        }
    }


    public static boolean isWallStructure(Unit structure) {
        float x1 = WALL_2x2.getX();
        float x2 = WALL_3x3.getX();
        float y1 = WALL_2x2.getY();
        float y2 = WALL_3x3.getY();

        float xMin = Math.min(x1, x2)-1;
        float xMax = Math.max(x1, x2)+1;
        float yMin = Math.min(y1, y2)-1;
        float yMax = Math.max(y1, y2)+1;

        float xStructure = structure.getPosition().getX();
        float yStructure = structure.getPosition().getY();

        return (xStructure >= xMin && xStructure <= xMax && yStructure >= yMin && yStructure <= yMax);


    }

    public static void setBaseLocations() {
        switch(MAP) {
            case MapNames.ACROPOLIS:
                baseLocations.add(Point2d.of(33.5f, 138.5f));
                baseLocations.add(Point2d.of(31.5f, 113.5f));
                baseLocations.add(Point2d.of(58.5f, 111.5f));
                baseLocations.add(Point2d.of(32.5f, 85.5f));
                baseLocations.add(Point2d.of(29.5f, 53.5f));
                baseLocations.add(Point2d.of(73.5f, 138.5f));
                baseLocations.add(Point2d.of(47.5f, 28.5f));
                baseLocations.add(Point2d.of(107.5f, 129.5f));
                baseLocations.add(Point2d.of(68.5f, 42.5f));
                baseLocations.add(Point2d.of(128.5f, 143.5f));
                baseLocations.add(Point2d.of(102.5f, 33.5f));
                baseLocations.add(Point2d.of(146.5f, 118.5f));
                baseLocations.add(Point2d.of(143.5f, 86.5f));
                baseLocations.add(Point2d.of(117.5f, 60.5f));
                baseLocations.add(Point2d.of(144.5f, 58.5f));
                baseLocations.add(Point2d.of(142.5f, 33.5f));
                break;

            case MapNames.DISCO_BLOODBATH:
                baseLocations.add(Point2d.of(39.5f, 115.5f));
                baseLocations.add(Point2d.of(48.5f, 142.5f));
                baseLocations.add(Point2d.of(64.5f, 113.5f));
                baseLocations.add(Point2d.of(83.5f, 144.5f));
                baseLocations.add(Point2d.of(117.5f, 143.5f));
                baseLocations.add(Point2d.of(38.5f, 81.5f));
                baseLocations.add(Point2d.of(159.5f, 141.5f));
                baseLocations.add(Point2d.of(143.5f, 115.5f));
                baseLocations.add(Point2d.of(56.5f, 64.5f));
                baseLocations.add(Point2d.of(40.5f, 38.5f));
                baseLocations.add(Point2d.of(161.5f, 98.5f));
                baseLocations.add(Point2d.of(82.5f, 36.5f));
                baseLocations.add(Point2d.of(116.5f, 35.5f));
                baseLocations.add(Point2d.of(135.5f, 66.5f));
                baseLocations.add(Point2d.of(151.5f, 37.5f));
                baseLocations.add(Point2d.of(160.5f, 64.5f));
                break;

            case MapNames.EPHEMERON:
            case MapNames.EPHEMERONLE:
                baseLocations.add(Point2d.of(29.5f, 138.5f));
                baseLocations.add(Point2d.of(29.5f, 111.5f));
                baseLocations.add(Point2d.of(61.5f, 134.5f));
                baseLocations.add(Point2d.of(94.5f, 141.5f));
                baseLocations.add(Point2d.of(131.5f, 131.5f));
                baseLocations.add(Point2d.of(28.5f, 73.5f));
                baseLocations.add(Point2d.of(91.5f, 111.5f));
                baseLocations.add(Point2d.of(68.5f, 48.5f));
                baseLocations.add(Point2d.of(131.5f, 86.5f));
                baseLocations.add(Point2d.of(28.5f, 28.5f));
                baseLocations.add(Point2d.of(65.5f, 18.5f));
                baseLocations.add(Point2d.of(98.5f, 25.5f));
                baseLocations.add(Point2d.of(130.5f, 48.5f));
                baseLocations.add(Point2d.of(130.5f, 21.5f));
                break;

            case MapNames.ETERNAL_EMPIRE:
                baseLocations.add(Point2d.of(142.5f, 140.5f));
                baseLocations.add(Point2d.of(142.5f, 110.5f));
                baseLocations.add(Point2d.of(115.5f, 134.5f));
                baseLocations.add(Point2d.of(79.5f, 146.5f));
                baseLocations.add(Point2d.of(131.5f, 83.5f));
                baseLocations.add(Point2d.of(86.5f, 126.5f));
                baseLocations.add(Point2d.of(126.5f, 55.5f));
                baseLocations.add(Point2d.of(33.5f, 139.5f));
                baseLocations.add(Point2d.of(49.5f, 116.5f));
                baseLocations.add(Point2d.of(142.5f, 32.5f));
                baseLocations.add(Point2d.of(89.5f, 45.5f));
                baseLocations.add(Point2d.of(44.5f, 88.5f));
                baseLocations.add(Point2d.of(96.5f, 25.5f));
                baseLocations.add(Point2d.of(60.5f, 37.5f));
                baseLocations.add(Point2d.of(33.5f, 61.5f));
                baseLocations.add(Point2d.of(33.5f, 31.5f));
                break;

            case MapNames.EVER_DREAM:
                baseLocations.add(Point2d.of(139.5f, 163.5f));
                baseLocations.add(Point2d.of(154.5f, 147.5f));
                baseLocations.add(Point2d.of(153.5f, 113.5f));
                baseLocations.add(Point2d.of(118.5f, 145.5f));
                baseLocations.add(Point2d.of(94.5f, 161.5f));
                baseLocations.add(Point2d.of(156.5f, 84.5f));
                baseLocations.add(Point2d.of(131.5f, 114.5f));
                baseLocations.add(Point2d.of(154.5f, 51.5f));
                baseLocations.add(Point2d.of(45.5f, 160.5f));
                baseLocations.add(Point2d.of(68.5f, 97.5f));
                baseLocations.add(Point2d.of(105.5f, 50.5f));
                baseLocations.add(Point2d.of(43.5f, 127.5f));
                baseLocations.add(Point2d.of(81.5f, 66.5f));
                baseLocations.add(Point2d.of(46.5f, 98.5f));
                baseLocations.add(Point2d.of(45.5f, 64.5f));
                baseLocations.add(Point2d.of(60.5f, 48.5f));
                break;

            case MapNames.GOLDEN_WALL:
                baseLocations.add(Point2d.of(32.5f, 50.5f));
                baseLocations.add(Point2d.of(40.5f, 77.5f));
                baseLocations.add(Point2d.of(71.5f, 71.5f));
                baseLocations.add(Point2d.of(42.5f, 109.5f));
                baseLocations.add(Point2d.of(39.5f, 141.5f));
                baseLocations.add(Point2d.of(75.5f, 130.5f));
                baseLocations.add(Point2d.of(51.5f, 30.5f));
                baseLocations.add(Point2d.of(79.5f, 29.5f));
                baseLocations.add(Point2d.of(128.5f, 29.5f));
                baseLocations.add(Point2d.of(156.5f, 30.5f));
                baseLocations.add(Point2d.of(132.5f, 130.5f));
                baseLocations.add(Point2d.of(168.5f, 141.5f));
                baseLocations.add(Point2d.of(165.5f, 109.5f));
                baseLocations.add(Point2d.of(136.5f, 71.5f));
                baseLocations.add(Point2d.of(167.5f, 77.5f));
                baseLocations.add(Point2d.of(175.5f, 50.5f));
                break;

            case MapNames.NIGHTSHADE:
                baseLocations.add(Point2d.of(41.5f, 138.5f));
                baseLocations.add(Point2d.of(42.5f, 107.5f));
                baseLocations.add(Point2d.of(73.5f, 133.5f));
                baseLocations.add(Point2d.of(105.5f, 141.5f));
                baseLocations.add(Point2d.of(60.5f, 94.5f));
                baseLocations.add(Point2d.of(150.5f, 140.5f));
                baseLocations.add(Point2d.of(127.5f, 121.5f));
                baseLocations.add(Point2d.of(39.5f, 69.5f));
                baseLocations.add(Point2d.of(152.5f, 102.5f));
                baseLocations.add(Point2d.of(64.5f, 50.5f));
                baseLocations.add(Point2d.of(41.5f, 31.5f));
                baseLocations.add(Point2d.of(131.5f, 77.5f));
                baseLocations.add(Point2d.of(86.5f, 30.5f));
                baseLocations.add(Point2d.of(118.5f, 38.5f));
                baseLocations.add(Point2d.of(149.5f, 64.5f));
                baseLocations.add(Point2d.of(150.5f, 33.5f));
                break;

            case MapNames.SIMULACRUM:
                baseLocations.add(Point2d.of(54.5f, 139.5f));
                baseLocations.add(Point2d.of(50.5f, 113.5f));
                baseLocations.add(Point2d.of(78.5f, 118.5f));
                baseLocations.add(Point2d.of(99.5f, 141.5f));
                baseLocations.add(Point2d.of(49.5f, 80.5f));
                baseLocations.add(Point2d.of(159.5f, 140.5f));
                baseLocations.add(Point2d.of(130.5f, 128.5f));
                baseLocations.add(Point2d.of(85.5f, 55.5f));
                baseLocations.add(Point2d.of(56.5f, 43.5f));
                baseLocations.add(Point2d.of(116.5f, 42.5f));
                baseLocations.add(Point2d.of(166.5f, 103.5f));
                baseLocations.add(Point2d.of(137.5f, 65.5f));
                baseLocations.add(Point2d.of(165.5f, 70.5f));
                baseLocations.add(Point2d.of(161.5f, 44.5f));
                break;

            case MapNames.THUNDERBIRD:
                baseLocations.add(Point2d.of(38.5f, 133.5f));
                baseLocations.add(Point2d.of(40.5f, 103.5f));
                baseLocations.add(Point2d.of(67.5f, 126.5f));
                baseLocations.add(Point2d.of(57.5f, 87.5f));
                baseLocations.add(Point2d.of(98.5f, 130.5f));
                baseLocations.add(Point2d.of(37.5f, 62.5f));
                baseLocations.add(Point2d.of(131.5f, 136.5f));
                baseLocations.add(Point2d.of(111.5f, 100.5f));
                baseLocations.add(Point2d.of(80.5f, 55.5f));
                baseLocations.add(Point2d.of(60.5f, 19.5f));
                baseLocations.add(Point2d.of(154.5f, 93.5f));
                baseLocations.add(Point2d.of(93.5f, 25.5f));
                baseLocations.add(Point2d.of(134.5f, 68.5f));
                baseLocations.add(Point2d.of(124.5f, 29.5f));
                baseLocations.add(Point2d.of(151.5f, 52.5f));
                baseLocations.add(Point2d.of(153.5f, 22.5f));
                break;

            case MapNames.TRITON:
                baseLocations.add(Point2d.of(55.5f, 157.5f));
                baseLocations.add(Point2d.of(82.5f, 160.5f));
                baseLocations.add(Point2d.of(71.5f, 132.5f));
                baseLocations.add(Point2d.of(112.5f, 159.5f));
                baseLocations.add(Point2d.of(49.5f, 114.5f));
                baseLocations.add(Point2d.of(154.5f, 157.5f));
                baseLocations.add(Point2d.of(120.5f, 132.5f));
                baseLocations.add(Point2d.of(150.5f, 131.5f));
                baseLocations.add(Point2d.of(65.5f, 72.5f));
                baseLocations.add(Point2d.of(95.5f, 71.5f));
                baseLocations.add(Point2d.of(61.5f, 46.5f));
                baseLocations.add(Point2d.of(166.5f, 89.5f));
                baseLocations.add(Point2d.of(103.5f, 44.5f));
                baseLocations.add(Point2d.of(144.5f, 71.5f));
                baseLocations.add(Point2d.of(133.5f, 43.5f));
                baseLocations.add(Point2d.of(160.5f, 46.5f));
                break;

            case MapNames.WINTERS_GATE:
                baseLocations.add(Point2d.of(47.5f, 134.5f));
                baseLocations.add(Point2d.of(44.5f, 109.5f));
                baseLocations.add(Point2d.of(70.5f, 112.5f));
                baseLocations.add(Point2d.of(43.5f, 81.5f));
                baseLocations.add(Point2d.of(92.5f, 135.5f));
                baseLocations.add(Point2d.of(44.5f, 53.5f));
                baseLocations.add(Point2d.of(44.5f, 28.5f));
                baseLocations.add(Point2d.of(147.5f, 135.5f));
                baseLocations.add(Point2d.of(147.5f, 110.5f));
                baseLocations.add(Point2d.of(148.5f, 82.5f));
                baseLocations.add(Point2d.of(99.5f, 28.5f));
                baseLocations.add(Point2d.of(121.5f, 51.5f));
                baseLocations.add(Point2d.of(147.5f, 54.5f));
                baseLocations.add(Point2d.of(144.5f, 29.5f));
                break;

            case MapNames.WORLD_OF_SLEEPERS:
                baseLocations.add(Point2d.of(149.5f, 141.5f));
                baseLocations.add(Point2d.of(147.5f, 112.5f));
                baseLocations.add(Point2d.of(120.5f, 140.5f));
                baseLocations.add(Point2d.of(148.5f, 83.5f));
                baseLocations.add(Point2d.of(118.5f, 115.5f));
                baseLocations.add(Point2d.of(149.5f, 56.5f));
                baseLocations.add(Point2d.of(84.5f, 142.5f));
                baseLocations.add(Point2d.of(147.5f, 25.5f));
                baseLocations.add(Point2d.of(36.5f, 142.5f));
                baseLocations.add(Point2d.of(99.5f, 25.5f));
                baseLocations.add(Point2d.of(65.5f, 52.5f));
                baseLocations.add(Point2d.of(34.5f, 111.5f));
                baseLocations.add(Point2d.of(63.5f, 27.5f));
                baseLocations.add(Point2d.of(35.5f, 84.5f));
                baseLocations.add(Point2d.of(36.5f, 55.5f));
                baseLocations.add(Point2d.of(34.5f, 26.5f));
                break;

            case MapNames.ZEN:
                baseLocations.add(Point2d.of(142.5f, 139.5f));
                baseLocations.add(Point2d.of(134.5f, 117.5f));
                baseLocations.add(Point2d.of(149.5f, 95.5f));
                baseLocations.add(Point2d.of(111.5f, 146.5f));
                baseLocations.add(Point2d.of(149.5f, 63.5f));
                baseLocations.add(Point2d.of(104.5f, 121.5f));
                baseLocations.add(Point2d.of(150.5f, 27.5f));
                baseLocations.add(Point2d.of(71.5f, 134.5f));
                baseLocations.add(Point2d.of(120.5f, 37.5f));
                baseLocations.add(Point2d.of(41.5f, 144.5f));
                baseLocations.add(Point2d.of(42.5f, 108.5f));
                baseLocations.add(Point2d.of(87.5f, 50.5f));
                baseLocations.add(Point2d.of(80.5f, 25.5f));
                baseLocations.add(Point2d.of(42.5f, 76.5f));
                baseLocations.add(Point2d.of(57.5f, 54.5f));
                baseLocations.add(Point2d.of(49.5f, 32.5f));
                break;
        }

        //reverse list for bottom spawn
        if (!isTopSpawn) {
            Collections.reverse(baseLocations);
        }
    }
}
