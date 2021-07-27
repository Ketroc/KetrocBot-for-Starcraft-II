package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.models.*;
import com.ketroc.strategies.Strategy;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;

import java.util.*;
import java.util.stream.Collectors;

public class LocationConstants {
    public static final Point2d SCREEN_BOTTOM_LEFT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP0().toPoint2d();
    public static final Point2d SCREEN_TOP_RIGHT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP1().toPoint2d();
    public static final int MIN_X = (int) SCREEN_BOTTOM_LEFT.getX();
    public static final int MIN_Y = (int) SCREEN_BOTTOM_LEFT.getY();
    public static final int MAX_X = (int) SCREEN_TOP_RIGHT.getX();
    public static final int MAX_Y = (int) SCREEN_TOP_RIGHT.getY();
    public static Point2d insideMainWall;
    public static Point2d mainBaseMidPos;
    public static Point2d enemyMainBaseMidPos;
    public static final List<Point2d> muleLetterPosList = new ArrayList<>();  //TODO: confirm top-left corner of letter
    public static Point2d pointOnMyRamp;
    public static Point2d pointOnEnemyRamp;


    public static boolean isTopSpawn;
    public static String MAP;
    public static TriangleOfNodes myMineralTriangle;
    public static TriangleOfNodes enemyMineralTriangle;
    public static Point2d myMineralPos;
    public static Point2d enemyMineralPos;
    public static Point2d REPAIR_BAY;
    public static Point2d REAPER_JUMP2;
    public static Point2d BUNKER_NATURAL;
    public static Point2d WALL_2x2;
    public static Point2d WALL_3x3;
    public static Point2d MID_WALL_3x3;
    public static Point2d MID_WALL_2x2;

    public static List<Point2d> reaperBlockDepots = new ArrayList<>();
    public static List<Point2d> reaperBlock3x3s = new ArrayList<>();

    public static List<Point2d> _3x3Structures = new ArrayList<>(); //barracks, engbay, and armory x2
    public static List<Point2d> extraDepots = new ArrayList<>();
    public static List<Point2d> FACTORIES = new ArrayList<>();
    public static List<Point2d> STARPORTS = new ArrayList<>();
    public static List<Point2d> TURRETS = new ArrayList<>();
    public static List<Point2d> MACRO_OCS = new ArrayList<>();
    public static Point2d proxyBarracksPos;
    public static Point2d proxyBunkerPos;
    public static Point2d proxyBunkerPos2;

    public static List<Point2d> baseLocations = new ArrayList<>();
    public static List<Point2d> clockBasePositions = new ArrayList<>();
    public static List<Point2d> counterClockBasePositions = new ArrayList<>();

    public static int baseAttackIndex;
    public static Race opponentRace;

    public static void onGameStart(UnitInPool mainCC) {
        if (MAP.contains("Golden Wall") || MAP.contains("Blackburn")) { //isTopSpawn == the left spawn for this map
            isTopSpawn = mainCC.unit().getPosition().getX() < 100;
        }
        else {
            isTopSpawn = mainCC.unit().getPosition().getY() > 100;
        }
        setStructureLocations();
        setBaseLocations();
        baseAttackIndex = LocationConstants.baseLocations.size()-2;
        setClockBaseLists();
        createBaseList(mainCC);
        setEnemyTypes();
        mapMainAndNatBases();
        mainBaseMidPos = getMainBaseMidPoint(false);
        enemyMainBaseMidPos = getMainBaseMidPoint(true);
        insideMainWall = Position.towards(
                Position.towards(MID_WALL_3x3, pointOnMyRamp, -1f),
                mainBaseMidPos,
                2f
        );

        //set probe rush mineral node
        enemyMineralTriangle = new TriangleOfNodes(enemyMineralPos);
        myMineralTriangle = new TriangleOfNodes(myMineralPos);
    }

    public static void onStep() { //TODO: rewrite this to use GameCache.baseList.isEnemyBase
        if (Time.nowFrames() % Time.toFrames("5:00") == 0 && Base.numMyBases() >= 4) { //every ~5min
            setNewEnemyBaseIndex();
        }
    }

    //returns base index of newest enemy base
    private static void setNewEnemyBaseIndex() {
        for (int i = 0; i< GameCache.baseList.size(); i++) {
            Base base = GameCache.baseList.get(i);
            if (base.isEnemyBase) {
                baseAttackIndex = Math.min(baseAttackIndex, i);
            }
        }
    }

    public static Point2d getNextBaseAttackPos() {
        baseAttackIndex++;
        setNewEnemyBaseIndex();
        if (baseAttackIndex >= baseLocations.size()) {
            Switches.finishHim = true;
            Chat.chatWithoutSpam("Finish Him!", 120);
            MuleMessages.doTrollMule = true;
            return null;
        }
        return baseLocations.get(baseAttackIndex);
    }


    //make array map of all points within the main bases
    private static void mapMainAndNatBases() {
        int xMin = 0;
        int xMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getX());
        int yMin = 0;
        int yMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getY());

        Point2d homePos = baseLocations.get(0);
        float homeZ = Bot.OBS.terrainHeight(homePos);

        Point2d enemyPos = baseLocations.get(baseLocations.size() - 1);
        float enemyZ = Bot.OBS.terrainHeight(enemyPos);

        Point2d natPos = baseLocations.get(1);
        float natZ = Bot.OBS.terrainHeight(natPos);

        Point2d enemyNatPos = baseLocations.get(baseLocations.size() - 2);
        float enemyNatZ = Bot.OBS.terrainHeight(enemyNatPos);

        float rampZ = (homeZ + natZ) / 2;

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                Point2d thisPos = Point2d.of(x/2f, y/2f);
                float thisZ = Bot.OBS.terrainHeight(thisPos);
                if (thisPos.distance(homePos) < 30 && Math.abs(thisZ - homeZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInMainBase[x][y] = true;
                    if (Math.abs(thisZ - rampZ) < 0.2f && thisPos.distance(natPos) < 15) {
                        pointOnMyRamp = Point2d.of(x/2, y/2);
                    }
                }
                else if (thisPos.distance(enemyPos) < 30 && Math.abs(thisZ - enemyZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInEnemyMainBase[x][y] = true;
                    if (Math.abs(thisZ - rampZ) < 0.2f && thisPos.distance(enemyNatPos) < 15) {
                        pointOnEnemyRamp = Point2d.of(x/2, y/2);
                    }
                }
                else if (thisPos.distance(natPos) < 14 && Math.abs(thisZ - natZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInNat[x][y] = true;
                    if (thisPos.distance(LocationConstants.BUNKER_NATURAL) > 8) {
                        InfluenceMaps.pointInNatExcludingBunkerRange[x][y] = true;
                    }
                }
                else if (thisPos.distance(enemyNatPos) < 16 && Math.abs(thisZ - enemyNatZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInEnemyNat[x][y] = true;
                }
            }
        }
    }

    //hack to make nodes/cc in starting position pathable
    private static boolean isPathable(Point2d thisPos) {
        return Bot.OBS.isPathable(thisPos) || thisPos.distance(Bot.OBS.getStartLocation().toPoint2d()) < 11;
    }

    public static void setRepairBayLocation() {
        Base mainBase = GameCache.baseList.get(0);
        //REPAIR_BAY = Position.rotate(mainBase.getResourceMidPoint(), mainBase.getCcPos(), 20); //use this if making turret on this position in main
        REPAIR_BAY = mainBase.getResourceMidPoint();


        //initialize retreat/attack point to repair bay location
        ArmyManager.retreatPos = ArmyManager.attackGroundPos = ArmyManager.attackAirPos = REPAIR_BAY;
    }

    private static void createBaseList(UnitInPool mainCC) {
        for (Point2d baseLocation : baseLocations) {
            Base newBase = new Base(baseLocation);
            newBase.setMineralPatches(getMineralPatches(newBase));
            newBase.setGases(getGases(newBase));
            GameCache.baseList.add(newBase);
        }
        GameCache.baseList.get(0).setCc(mainCC);
    }

    private static List<MineralPatch> getMineralPatches(Base base) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, node -> UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) &&
                UnitUtils.getDistance(node.unit(), base.getCcPos()) < 10)
                .stream()
                .map(node -> new MineralPatch(node.unit(), base.getCcPos()))
                .collect(Collectors.toList());
    }

    private static List<Gas> getGases(Base base) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, node -> UnitUtils.GAS_GEYSER_TYPE.contains(node.unit().getType()) &&
                UnitUtils.getDistance(node.unit(), base.getCcPos()) < 10)
                .stream()
                .map(node -> new Gas(node.unit(), base.getCcPos()))
                .collect(Collectors.toList());
    }

    public static boolean setEnemyTypes() {
        switch (opponentRace) {
            case TERRAN:
                UnitUtils.enemyCommandStructures = new HashSet<>(Set.of(Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING));
                UnitUtils.enemyWorkerType = Units.TERRAN_SCV;
                return true;
            case PROTOSS:
                UnitUtils.enemyCommandStructures = new HashSet<>(Set.of(Units.PROTOSS_NEXUS));
                UnitUtils.enemyWorkerType = Units.PROTOSS_PROBE;
                return true;
            case ZERG:
                UnitUtils.enemyCommandStructures = new HashSet<>(Set.of(Units.ZERG_HATCHERY, Units.ZERG_LAIR, Units.ZERG_HIVE));
                UnitUtils.enemyWorkerType = Units.ZERG_DRONE;
                return true;
            default: //case RANDOM:
                return false;
        }
    }

    private static void setStructureLocations() {
        switch (MAP) {
            case MapNames._2000_ATMOSPHERES_AIE:
                setLocationsFor2000Atmospheres(isTopSpawn);
                break;
            case MapNames.ACROPOLIS:
                setLocationsForAcropolis(isTopSpawn);
                break;
            case MapNames.ASCENSION_TO_AIUR:
                setLocationsForAscensionToAiur(isTopSpawn);
                break;
            case MapNames.BLACKBURN_AIE:
                setLocationsForBlackburn(isTopSpawn);
                break;
            case MapNames.CATALYST:
                setLocationsForCatalyst(isTopSpawn);
                break;
            case MapNames.DEATH_AURA:
            case MapNames.DEATH_AURA505:
            case MapNames.DEATH_AURA506:
                setLocationsForDeathAura(isTopSpawn);
                break;
            case MapNames.DISCO_BLOODBATH:
                setLocationsForDiscoBloodBath(isTopSpawn);
                break;
            case MapNames.EPHEMERON:
            case MapNames.EPHEMERONLE:
                setLocationsForEphemeron(isTopSpawn);
                break;
            case MapNames.ETERNAL_EMPIRE:
            case MapNames.ETERNAL_EMPIRE505:
            case MapNames.ETERNAL_EMPIRE506:
                setLocationsForEternalEmpire(isTopSpawn);
                break;
            case MapNames.EVER_DREAM:
            case MapNames.EVER_DREAM505:
            case MapNames.EVER_DREAM506:
                setLocationsForEverDream(isTopSpawn);
                break;
            case MapNames.GOLDEN_WALL:
            case MapNames.GOLDEN_WALL505:
            case MapNames.GOLDEN_WALL506:
                setLocationsForGoldenWall(isTopSpawn);
                break;
            case MapNames.ICE_AND_CHROME:
            case MapNames.ICE_AND_CHROME505:
            case MapNames.ICE_AND_CHROME506:
                setLocationsForIceAndChrome(isTopSpawn);
                break;
            case MapNames.JAGANNATHA:
            case MapNames.JAGANNATHA_AIE:
                setLocationsForJagannatha(isTopSpawn);
                break;
            case MapNames.LIGHTSHADE:
            case MapNames.LIGHTSHADE_AIE:
                setLocationsForLightshade(isTopSpawn);
                break;
            case MapNames.NIGHTSHADE:
                setLocationsForNightshade(isTopSpawn);
                break;
            case MapNames.OXIDE:
            case MapNames.OXIDE_AIE:
                setLocationsForOxide(isTopSpawn);
                break;
            case MapNames.PILLARS_OF_GOLD:
            case MapNames.PILLARS_OF_GOLD505:
            case MapNames.PILLARS_OF_GOLD506:
                setLocationsForPillarsOfGold(isTopSpawn);
                break;
            case MapNames.ROMANTICIDE:
            case MapNames.ROMANTICIDE_AIE:
                setLocationsForRomanticide(isTopSpawn);
                break;
            case MapNames.SIMULACRUM:
                setLocationsForSimulacrum(isTopSpawn);
                break;
            case MapNames.SUBMARINE:
            case MapNames.SUBMARINE505:
            case MapNames.SUBMARINE506:
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
    }

    private static void setLocationsFor2000Atmospheres(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(100.5f, 106.5f));
        muleLetterPosList.add(Point2d.of(115.5f, 89.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(123.5f, 73.5f);
            proxyBunkerPos = Point2d.of(87.5f, 57.5f);
            proxyBunkerPos2 = Point2d.of(77.5f, 83.5f);

            reaperBlockDepots.add(Point2d.of(151.0f, 134.0f));
            reaperBlockDepots.add(Point2d.of(156.0f, 129.0f));
            reaperBlockDepots.add(Point2d.of(147.0f, 138.0f));
            reaperBlock3x3s.add(Point2d.of(149.5f, 136.5f));


            myMineralPos = Point2d.of(174f, 144.5f);
            enemyMineralPos = Point2d.of(50f, 59.5f);

            WALL_2x2 = Point2d.of(153.0f, 144.0f);
            WALL_3x3 = Point2d.of(150.5f, 140.5f);
            MID_WALL_3x3 = Point2d.of(153.5f, 141.5f);
            MID_WALL_2x2 = Point2d.of(153f, 142f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(172.5f, 150.5f));
            _3x3Structures.add(Point2d.of(153.5f, 157.5f));

            BUNKER_NATURAL = Point2d.of(136.5f, 144.5f);
            FACTORIES.add(Point2d.of(159.5f, 141.5f));
            FACTORIES.add(Point2d.of(159.5f, 144.5f));

            STARPORTS.add(Point2d.of(170.5f, 153.5f));
            STARPORTS.add(Point2d.of(160.5f, 153.5f));
            STARPORTS.add(Point2d.of(165.5f, 152.5f));
            STARPORTS.add(Point2d.of(176.5f, 148.5f));
            STARPORTS.add(Point2d.of(176.5f, 144.5f));
            STARPORTS.add(Point2d.of(176.5f, 140.5f));
            STARPORTS.add(Point2d.of(172.5f, 137.5f));
            STARPORTS.add(Point2d.of(153.5f, 137.5f));
            STARPORTS.add(Point2d.of(153.5f, 134.5f));
            STARPORTS.add(Point2d.of(154.5f, 131.5f));
            STARPORTS.add(Point2d.of(159.5f, 148.5f));
            STARPORTS.add(Point2d.of(150.5f, 160.5f));
            STARPORTS.add(Point2d.of(145.5f, 161.5f));

            MACRO_OCS.add(Point2d.of(166.5f, 138.5f));
            MACRO_OCS.add(Point2d.of(160.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(172.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(167.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(162.5f, 132.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(168.0f, 154.0f));
            extraDepots.add(Point2d.of(163.0f, 155.0f));
            extraDepots.add(Point2d.of(161.0f, 156.0f));
            extraDepots.add(Point2d.of(160.0f, 151.0f));
            extraDepots.add(Point2d.of(158.0f, 153.0f));
            extraDepots.add(Point2d.of(158.0f, 151.0f));
            extraDepots.add(Point2d.of(175.0f, 151.0f));
            extraDepots.add(Point2d.of(177.0f, 138.0f));
            extraDepots.add(Point2d.of(170.0f, 130.0f));
            extraDepots.add(Point2d.of(168.0f, 130.0f));
            extraDepots.add(Point2d.of(166.0f, 130.0f));
            extraDepots.add(Point2d.of(164.0f, 129.0f));
        } else {
            proxyBarracksPos = Point2d.of(99.5f, 131.5f);
            proxyBunkerPos = Point2d.of(136.5f, 146.5f);
            proxyBunkerPos2 = Point2d.of(147.5f, 121.5f);

            reaperBlockDepots.add(Point2d.of(73.0f, 70.0f));
            reaperBlockDepots.add(Point2d.of(68.0f, 75.0f));
            reaperBlockDepots.add(Point2d.of(77.0f, 66.0f));
            reaperBlock3x3s.add(Point2d.of(74.5f, 67.5f));

            myMineralPos = Point2d.of(50f, 59.5f);
            enemyMineralPos = Point2d.of(174f, 144.5f);

            WALL_2x2 = Point2d.of(71.0f, 60.0f);
            MID_WALL_3x3 = Point2d.of(70.5f, 62.5f);
            MID_WALL_2x2 = Point2d.of(71f, 62f);
            WALL_3x3 = Point2d.of(73.5f, 63.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(70.5f, 65.5f));
            _3x3Structures.add(Point2d.of(70.5f, 46.5f));

            BUNKER_NATURAL = Point2d.of(86.5f, 58.5f);
            FACTORIES.add(Point2d.of(63.5f, 61.5f));
            FACTORIES.add(Point2d.of(63.5f, 58.5f));

            STARPORTS.add(Point2d.of(60.5f, 49.5f));
            STARPORTS.add(Point2d.of(49.5f, 52.5f));
            STARPORTS.add(Point2d.of(54.5f, 52.5f));
            STARPORTS.add(Point2d.of(64.5f, 52.5f));
            STARPORTS.add(Point2d.of(46.5f, 61.5f));
            STARPORTS.add(Point2d.of(46.5f, 56.5f));
            STARPORTS.add(Point2d.of(49.5f, 66.5f));
            STARPORTS.add(Point2d.of(66.5f, 72.5f));
            STARPORTS.add(Point2d.of(67.5f, 69.5f));
            STARPORTS.add(Point2d.of(64.5f, 67.5f));
            STARPORTS.add(Point2d.of(63.5f, 64.5f));
            STARPORTS.add(Point2d.of(74.5f, 44.5f));
            STARPORTS.add(Point2d.of(79.5f, 42.5f));

            MACRO_OCS.add(Point2d.of(57.5f, 65.5f));
            MACRO_OCS.add(Point2d.of(51.5f, 70.5f));
            MACRO_OCS.add(Point2d.of(56.5f, 70.5f));
            MACRO_OCS.add(Point2d.of(61.5f, 71.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(46.0f, 59.0f));
            extraDepots.add(Point2d.of(47.0f, 54.0f));
            extraDepots.add(Point2d.of(48.0f, 59.0f));
            extraDepots.add(Point2d.of(51.0f, 50.0f));
            extraDepots.add(Point2d.of(57.0f, 50.0f));
            extraDepots.add(Point2d.of(62.0f, 47.0f));
            extraDepots.add(Point2d.of(62.0f, 67.0f));
            extraDepots.add(Point2d.of(55.0f, 50.0f));
            extraDepots.add(Point2d.of(65.0f, 50.0f));
            extraDepots.add(Point2d.of(48.0f, 64.0f));
            extraDepots.add(Point2d.of(53.0f, 50.0f));
            extraDepots.add(Point2d.of(54.0f, 67.0f));
        }
    }



    private static void setLocationsForAcropolis(boolean isTopPos) {
        if (isTopPos) {
            reaperBlockDepots.add(Point2d.of(52.0f, 132.0f));

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
            reaperBlockDepots.add(Point2d.of(124.0f, 40.0f));

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

    private static void setLocationsForAscensionToAiur(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(100.5f, 97.5f));
        muleLetterPosList.add(Point2d.of(108.5f, 98.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(119.5f, 83.5f);
            proxyBunkerPos = Point2d.of(145.5f, 50.5f);
            //proxyBunkerPos2 unnecessary

            //no reaper jumps

            myMineralPos = Point2d.of(16.5f, 126.5f);
            enemyMineralPos = Point2d.of(155.5f, 22.5f);

            WALL_2x2 = Point2d.of(30f, 119f);
            WALL_3x3 = Point2d.of(33.5f, 116.5f);
            MID_WALL_3x3 = Point2d.of(32.5f, 119.5f);
            MID_WALL_2x2 = Point2d.of(32f, 119f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(16.5f, 126.5f));
            _3x3Structures.add(Point2d.of(16.5f, 129.5f));

            BUNKER_NATURAL = Point2d.of(31.5f, 103.5f);
            FACTORIES.add(Point2d.of(31.5f, 124.5f));
            FACTORIES.add(Point2d.of(37.5f, 124.5f));

            STARPORTS.add(Point2d.of(32.5f, 135.5f));
            STARPORTS.add(Point2d.of(27.5f, 138.5f));
            STARPORTS.add(Point2d.of(38.5f, 134.5f));
            STARPORTS.add(Point2d.of(41.5f, 136.5f));
            STARPORTS.add(Point2d.of(36.5f, 137.5f));
            STARPORTS.add(Point2d.of(16.5f, 134.5f));
            STARPORTS.add(Point2d.of(19.5f, 137.5f));
            //STARPORTS.add(Point2d.of(37.5f, 124.5f)); FACTORY2
            STARPORTS.add(Point2d.of(45.5f, 128.5f));
            STARPORTS.add(Point2d.of(44.5f, 132.5f));
            STARPORTS.add(Point2d.of(14.5f, 116.5f));
            STARPORTS.add(Point2d.of(20.5f, 116.5f));
            STARPORTS.add(Point2d.of(10.5f, 103.5f));
            STARPORTS.add(Point2d.of(16.5f, 123.5f));
            STARPORTS.add(Point2d.of(21.5f, 120.5f));

            TURRETS.add(Point2d.of(30.0f, 133.0f));
            TURRETS.add(Point2d.of(22.0f, 128.0f));
            TURRETS.add(Point2d.of(27.0f, 133.0f));

            MACRO_OCS.add(Point2d.of(26.5f, 123.5f));
            MACRO_OCS.add(Point2d.of(31.5f, 128.5f));
            MACRO_OCS.add(Point2d.of(36.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 128.5f));
            MACRO_OCS.add(Point2d.of(43.5f, 123.5f));
            MACRO_OCS.add(Point2d.of(37.5f, 119.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(32.0f, 138.0f));
            extraDepots.add(Point2d.of(34.0f, 138.0f));
            extraDepots.add(Point2d.of(36.0f, 133.0f));
            extraDepots.add(Point2d.of(34.0f, 133.0f));
            extraDepots.add(Point2d.of(32.0f, 133.0f));
            extraDepots.add(Point2d.of(19.0f, 120.0f));
            extraDepots.add(Point2d.of(17.0f, 120.0f));
            extraDepots.add(Point2d.of(15.0f, 132.0f));
            extraDepots.add(Point2d.of(17.0f, 138.0f));
            extraDepots.add(Point2d.of(25.0f, 138.0f));
            extraDepots.add(Point2d.of(17.0f, 132.0f));
            extraDepots.add(Point2d.of(27.0f, 136.0f));
            extraDepots.add(Point2d.of(35.0f, 126.0f));
            extraDepots.add(Point2d.of(42.0f, 132.0f));
            extraDepots.add(Point2d.of(21.0f, 123.0f));
        } else {
            proxyBarracksPos = Point2d.of(63.5f, 73.5f);
            proxyBunkerPos = Point2d.of(30.5f, 101.5f);
            //proxyBunkerPos2 unnecessary

            //no reaper jumps

            myMineralPos = Point2d.of(155.5f, 22.5f);
            enemyMineralPos = Point2d.of(16.5f, 126.5f);

            WALL_2x2 = Point2d.of(146.0f, 33.0f);
            MID_WALL_3x3 = Point2d.of(143.5f, 32.5f);
            MID_WALL_2x2 = Point2d.of(144f, 33f);
            WALL_3x3 = Point2d.of(142.5f, 35.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(159.5f, 27.5f));
            _3x3Structures.add(Point2d.of(159.5f, 24.5f));

            BUNKER_NATURAL = Point2d.of(144.5f, 48.5f);
            FACTORIES.add(Point2d.of(144.5f, 27.5f));
            FACTORIES.add(Point2d.of(151.5f, 31.5f));

            STARPORTS.add(Point2d.of(157.5f, 30.5f));
            STARPORTS.add(Point2d.of(150.5f, 28.5f));
            STARPORTS.add(Point2d.of(158.5f, 20.5f));
            STARPORTS.add(Point2d.of(157.5f, 16.5f));
            STARPORTS.add(Point2d.of(152.5f, 14.5f));
            STARPORTS.add(Point2d.of(146.5f, 13.5f));
            //STARPORTS.add(Point2d.of(151.5f, 31.5f)); FACTORY2
            STARPORTS.add(Point2d.of(128.5f, 21.5f));
            STARPORTS.add(Point2d.of(128.5f, 26.5f));
            STARPORTS.add(Point2d.of(131.5f, 28.5f));
            STARPORTS.add(Point2d.of(134.5f, 30.5f));
            STARPORTS.add(Point2d.of(137.5f, 32.5f));
            STARPORTS.add(Point2d.of(159.5f, 35.5f));
            STARPORTS.add(Point2d.of(154.5f, 35.5f));
            STARPORTS.add(Point2d.of(163.5f, 49.5f));

            TURRETS.add(Point2d.of(146.0f, 19.0f));
            TURRETS.add(Point2d.of(154.0f, 24.0f));
            TURRETS.add(Point2d.of(149.0f, 19.0f));

            MACRO_OCS.add(Point2d.of(144.5f, 23.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 21.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 26.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 23.5f));
            MACRO_OCS.add(Point2d.of(139.5f, 16.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 17.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(155.0f, 29.0f));
            extraDepots.add(Point2d.of(160.0f, 18.0f));
            extraDepots.add(Point2d.of(143.0f, 20.0f));
            extraDepots.add(Point2d.of(143.0f, 14.0f));
            extraDepots.add(Point2d.of(143.0f, 18.0f));
            extraDepots.add(Point2d.of(143.0f, 16.0f));
            extraDepots.add(Point2d.of(135.0f, 14.0f));
            extraDepots.add(Point2d.of(136.0f, 27.0f));
            extraDepots.add(Point2d.of(139.0f, 30.0f));
            extraDepots.add(Point2d.of(130.0f, 19.0f));
            extraDepots.add(Point2d.of(130.0f, 24.0f));
            extraDepots.add(Point2d.of(132.0f, 31.0f));
            extraDepots.add(Point2d.of(159.0f, 14.0f));
            extraDepots.add(Point2d.of(149.0f, 16.0f));
            extraDepots.add(Point2d.of(157.0f, 14.0f));
        }
    }

    private static void setLocationsForBlackburn(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(88.5f, 89.5f));
        muleLetterPosList.add(Point2d.of(88.5f, 74.5f));
        if (isTopPos) { //left spawn
            proxyBarracksPos = Point2d.of(118.5f, 81.5f);
            proxyBunkerPos = Point2d.of(141.5f, 60.5f);
            proxyBunkerPos2 = Point2d.of(111.5f, 38.5f);

            reaperBlockDepots.add(Point2d.of(57.0f, 30.0f));
            reaperBlockDepots.add(Point2d.of(61.0f, 26.0f));
            reaperBlock3x3s.add(Point2d.of(58.5f, 27.5f));


            myMineralPos = Point2d.of(29f, 30.5f);
            enemyMineralPos = Point2d.of(155f, 30.5f);

            WALL_2x2 = Point2d.of(46.0f, 43.0f);
            WALL_3x3 = Point2d.of(49.5f, 45.5f);
            MID_WALL_3x3 = Point2d.of(48.5f, 42.5f);
            MID_WALL_2x2 = Point2d.of(48f, 43f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(26.5f, 34.5f));
            _3x3Structures.add(Point2d.of(26.5f, 30.5f));

            BUNKER_NATURAL = Point2d.of(42.5f, 59.5f);
            FACTORIES.add(Point2d.of(55.5f, 40.5f));
            FACTORIES.add(Point2d.of(52.5f, 38.5f));

            STARPORTS.add(Point2d.of(26.5f, 26.5f));
            STARPORTS.add(Point2d.of(26.5f, 38.5f));
            STARPORTS.add(Point2d.of(30.5f, 22.5f));
            STARPORTS.add(Point2d.of(35.5f, 21.5f));
            STARPORTS.add(Point2d.of(40.5f, 21.5f));
            STARPORTS.add(Point2d.of(45.5f, 32.5f));
            STARPORTS.add(Point2d.of(45.5f, 35.5f));
            STARPORTS.add(Point2d.of(44.5f, 38.5f));
            STARPORTS.add(Point2d.of(39.5f, 35.5f));
            STARPORTS.add(Point2d.of(38.5f, 38.5f));
            STARPORTS.add(Point2d.of(33.5f, 35.5f));
            STARPORTS.add(Point2d.of(32.5f, 38.5f));
            STARPORTS.add(Point2d.of(28.5f, 48.5f));

            MACRO_OCS.add(Point2d.of(41.5f, 31.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 28.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 23.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 24.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 29.5f));
            MACRO_OCS.add(Point2d.of(53.5f, 34.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(43.0f, 23.0f));
            extraDepots.add(Point2d.of(33.0f, 24.0f));
            extraDepots.add(Point2d.of(43.0f, 25.0f));
            extraDepots.add(Point2d.of(56.0f, 28.0f));
            extraDepots.add(Point2d.of(28.0f, 23.0f));
            extraDepots.add(Point2d.of(56.0f, 24.0f));
            extraDepots.add(Point2d.of(50.0f, 33.0f));
            extraDepots.add(Point2d.of(56.0f, 26.0f));
            extraDepots.add(Point2d.of(29.0f, 40.0f));
            extraDepots.add(Point2d.of(35.0f, 40.0f));
            extraDepots.add(Point2d.of(50.0f, 35.0f));
            extraDepots.add(Point2d.of(41.0f, 40.0f));
        } else { //right spawn
            proxyBarracksPos = Point2d.of(64.5f, 80.5f);
            proxyBunkerPos = Point2d.of(42.5f, 60.5f);
            proxyBunkerPos2 = Point2d.of(72.5f, 38.5f);

            reaperBlockDepots.add(Point2d.of(127.0f, 30.0f));
            reaperBlockDepots.add(Point2d.of(123.0f, 26.0f));
            reaperBlock3x3s.add(Point2d.of(125.5f, 27.5f));

            myMineralPos = Point2d.of(155f, 30.5f);
            enemyMineralPos = Point2d.of(29f, 30.5f);

            WALL_2x2 = Point2d.of(138.0f, 43.0f);
            WALL_3x3 = Point2d.of(134.5f, 45.5f);
            MID_WALL_3x3 = Point2d.of(135.5f, 42.5f);
            MID_WALL_2x2 = Point2d.of(136f, 43f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(157.5f, 30.5f));
            _3x3Structures.add(Point2d.of(153.5f, 38.5f));

            BUNKER_NATURAL = Point2d.of(141.5f, 59.5f);
            FACTORIES.add(Point2d.of(141.5f, 39.5f));
            FACTORIES.add(Point2d.of(141.5f, 36.5f));

            STARPORTS.add(Point2d.of(156.5f, 26.5f));
            STARPORTS.add(Point2d.of(156.5f, 38.5f));
            STARPORTS.add(Point2d.of(151.5f, 22.5f));
            STARPORTS.add(Point2d.of(147.5f, 36.5f));
            STARPORTS.add(Point2d.of(147.5f, 39.5f));
            STARPORTS.add(Point2d.of(141.5f, 20.5f));
            STARPORTS.add(Point2d.of(146.5f, 20.5f));
            STARPORTS.add(Point2d.of(136.5f, 32.5f));
            STARPORTS.add(Point2d.of(131.5f, 33.5f));
            STARPORTS.add(Point2d.of(128.5f, 37.5f));
            STARPORTS.add(Point2d.of(129.5f, 40.5f));
            STARPORTS.add(Point2d.of(129.5f, 43.5f));
            STARPORTS.add(Point2d.of(156.5f, 50.5f));
            STARPORTS.add(Point2d.of(152.5f, 47.5f));

            MACRO_OCS.add(Point2d.of(142.5f, 31.5f));
            MACRO_OCS.add(Point2d.of(137.5f, 28.5f));
            MACRO_OCS.add(Point2d.of(137.5f, 23.5f));
            MACRO_OCS.add(Point2d.of(131.5f, 24.5f));
            MACRO_OCS.add(Point2d.of(131.5f, 29.5f));
            MACRO_OCS.add(Point2d.of(135.5f, 36.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(149.0f, 22.0f));
            extraDepots.add(Point2d.of(144.0f, 22.0f));
            extraDepots.add(Point2d.of(132.0f, 21.0f));
            extraDepots.add(Point2d.of(159.0f, 34.0f));
            extraDepots.add(Point2d.of(142.0f, 23.0f));
            extraDepots.add(Point2d.of(154.0f, 25.0f));
            extraDepots.add(Point2d.of(144.0f, 41.0f));
            extraDepots.add(Point2d.of(156.0f, 41.0f));
            extraDepots.add(Point2d.of(157.0f, 33.0f));
            extraDepots.add(Point2d.of(147.0f, 24.0f));
            extraDepots.add(Point2d.of(157.0f, 35.0f));
            extraDepots.add(Point2d.of(152.0f, 41.0f));
        }
    }



    private static void setLocationsForCatalyst(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(103.5f, 84.5f));
        muleLetterPosList.add(Point2d.of(114.5f, 84.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(109.5f, 79.5f);
            proxyBunkerPos = Point2d.of(110.5f, 36.5f);
            proxyBunkerPos2 = Point2d.of(139.5f, 59.5f);

            reaperBlock3x3s.add(Point2d.of(42.5f, 120.5f));

            myMineralPos = Point2d.of(33.5f, 140.5f);
            enemyMineralPos = Point2d.of(150.5f, 27.5f);

            WALL_2x2 = Point2d.of(55.0f, 134.0f);
            WALL_3x3 = Point2d.of(57.5f, 130.5f);
            MID_WALL_3x3 = Point2d.of(54.5f, 131.5f);
            MID_WALL_2x2 = Point2d.of(55f, 132f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(41.5f, 128.5f));
            _3x3Structures.add(Point2d.of(59.5f, 145.5f));

            BUNKER_NATURAL = Point2d.of(71.5f, 131.5f);
            FACTORIES.add(Point2d.of(51.5f, 136.5f));
            FACTORIES.add(Point2d.of(44.5f, 134.5f));

            //STARPORTS.add(Point2d.of(44.5f, 134.5f)); //FACTORY2
            STARPORTS.add(Point2d.of(27.5f, 134.5f));
            STARPORTS.add(Point2d.of(27.5f, 137.5f));
            STARPORTS.add(Point2d.of(27.5f, 140.5f));
            STARPORTS.add(Point2d.of(27.5f, 143.5f));
            STARPORTS.add(Point2d.of(28.5f, 146.5f));
            STARPORTS.add(Point2d.of(31.5f, 148.5f));
            STARPORTS.add(Point2d.of(37.5f, 148.5f));
            STARPORTS.add(Point2d.of(40.5f, 150.5f));
            STARPORTS.add(Point2d.of(46.5f, 149.5f));
            STARPORTS.add(Point2d.of(46.5f, 146.5f));
            STARPORTS.add(Point2d.of(52.5f, 146.5f));
            STARPORTS.add(Point2d.of(59.5f, 149.5f));
            STARPORTS.add(Point2d.of(65.5f, 151.5f));
            STARPORTS.add(Point2d.of(44.5f, 124.5f));

            TURRETS.add(Point2d.of(35.0f, 136.0f));
            TURRETS.add(Point2d.of(43.0f, 144.0f));
            TURRETS.add(Point2d.of(35.0f, 139.0f));

            MACRO_OCS.add(Point2d.of(40.5f, 134.5f));
            MACRO_OCS.add(Point2d.of(44.5f, 139.5f));
            MACRO_OCS.add(Point2d.of(49.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(54.5f, 142.5f));
            MACRO_OCS.add(Point2d.of(31.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(36.5f, 129.5f));
            MACRO_OCS.add(Point2d.of(34.5f, 123.5f));
            MACRO_OCS.add(Point2d.of(39.5f, 124.5f));
            MACRO_OCS.add(Point2d.of(55.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(50.5f, 126.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 128.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(54.0f, 138.0f));
            extraDepots.add(Point2d.of(47.0f, 136.0f));
            extraDepots.add(Point2d.of(49.0f, 130.0f));
            extraDepots.add(Point2d.of(51.0f, 130.0f));
            extraDepots.add(Point2d.of(49.0f, 132.0f));
            extraDepots.add(Point2d.of(32.0f, 134.0f));
            extraDepots.add(Point2d.of(34.0f, 134.0f));
            extraDepots.add(Point2d.of(36.0f, 134.0f));
            extraDepots.add(Point2d.of(33.0f, 146.0f));
            extraDepots.add(Point2d.of(38.0f, 151.0f));
            extraDepots.add(Point2d.of(51.0f, 150.0f));
            extraDepots.add(Point2d.of(34.0f, 151.0f));
            extraDepots.add(Point2d.of(36.0f, 151.0f));
            extraDepots.add(Point2d.of(46.0f, 143.0f));
            extraDepots.add(Point2d.of(38.0f, 121.0f));
        } else {
            proxyBarracksPos = Point2d.of(69.5f, 99.5f);
            proxyBunkerPos = Point2d.of(73.5f, 131.5f);
            proxyBunkerPos2 = Point2d.of(44.5f, 108.5f);

            reaperBlock3x3s.add(Point2d.of(141.5f, 47.5f));

            myMineralPos = Point2d.of(150.5f, 27.5f);
            enemyMineralPos = Point2d.of(33.5f, 140.5f);

            WALL_2x2 = Point2d.of(129.0f, 34.0f);
            MID_WALL_3x3 = Point2d.of(129.5f, 36.5f);
            MID_WALL_2x2 = Point2d.of(129f, 36f);
            WALL_3x3 = Point2d.of(126.5f, 37.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(149.5f, 36.5f));
            _3x3Structures.add(Point2d.of(148.5f, 41.5f));

            BUNKER_NATURAL = Point2d.of(111.5f, 36.5f);
            FACTORIES.add(Point2d.of(130.5f, 31.5f));
            FACTORIES.add(Point2d.of(134.5f, 26.5f));

            STARPORTS.add(Point2d.of(154.5f, 24.5f));
            STARPORTS.add(Point2d.of(154.5f, 28.5f));
            STARPORTS.add(Point2d.of(154.5f, 32.5f));
            STARPORTS.add(Point2d.of(152.5f, 35.5f));
            STARPORTS.add(Point2d.of(142.5f, 43.5f));
            STARPORTS.add(Point2d.of(128.5f, 26.5f));
            STARPORTS.add(Point2d.of(128.5f, 23.5f));
            //STARPORTS.add(Point2d.of(134.5f, 26.5f)); FACTORY2
            STARPORTS.add(Point2d.of(134.5f, 23.5f));
            STARPORTS.add(Point2d.of(140.5f, 17.5f));
            STARPORTS.add(Point2d.of(145.5f, 17.5f));
            STARPORTS.add(Point2d.of(148.5f, 19.5f));
            STARPORTS.add(Point2d.of(151.5f, 21.5f));
            STARPORTS.add(Point2d.of(122.5f, 18.5f));
            STARPORTS.add(Point2d.of(117.5f, 17.5f));

            TURRETS.add(Point2d.of(149.0f, 32.0f));
            TURRETS.add(Point2d.of(141.0f, 24.0f));
            TURRETS.add(Point2d.of(149.0f, 29.0f));

            MACRO_OCS.add(Point2d.of(144.5f, 33.5f));
            MACRO_OCS.add(Point2d.of(143.5f, 38.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 37.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 42.5f));
            MACRO_OCS.add(Point2d.of(133.5f, 42.5f));
            MACRO_OCS.add(Point2d.of(128.5f, 42.5f));
            MACRO_OCS.add(Point2d.of(152.5f, 40.5f));
            MACRO_OCS.add(Point2d.of(147.5f, 46.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 19.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(146.0f, 20.0f));
            extraDepots.add(Point2d.of(138.0f, 18.0f));
            extraDepots.add(Point2d.of(143.0f, 19.0f));
            extraDepots.add(Point2d.of(138.0f, 20.0f));
            extraDepots.add(Point2d.of(157.0f, 30.0f));
            extraDepots.add(Point2d.of(149.0f, 39.0f));
            extraDepots.add(Point2d.of(157.0f, 26.0f));
            extraDepots.add(Point2d.of(147.0f, 39.0f));
            extraDepots.add(Point2d.of(139.0f, 22.0f));
            extraDepots.add(Point2d.of(150.0f, 17.0f));
            extraDepots.add(Point2d.of(150.0f, 34.0f));
            extraDepots.add(Point2d.of(139.0f, 24.0f));
            extraDepots.add(Point2d.of(153.0f, 19.0f));
            extraDepots.add(Point2d.of(156.0f, 22.0f));
            extraDepots.add(Point2d.of(131.0f, 20.0f));
        }
    }

    private static void setLocationsForDeathAura(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(100.5f, 97.5f));
        muleLetterPosList.add(Point2d.of(108.5f, 98.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(118.5f, 83.5f);
            proxyBunkerPos = Point2d.of(129.5f, 48.5f);
            proxyBunkerPos2 = Point2d.of(136.5f, 68.5f);

            reaperBlockDepots.add(Point2d.of(49.0f, 130.0f));
            reaperBlock3x3s.add(Point2d.of(46.5f, 128.5f));
            reaperBlock3x3s.add(Point2d.of(51.5f, 131.5f));


            myMineralPos = Point2d.of(37f, 146.5f);
            enemyMineralPos = Point2d.of(155.0f, 41.5f);

            WALL_2x2 = Point2d.of(47.0f, 140.0f);
            WALL_3x3 = Point2d.of(49.5f, 136.5f);
            MID_WALL_3x3 = Point2d.of(46.5f, 137.5f);
            MID_WALL_2x2 = Point2d.of(47f, 138f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(38.5f, 132.5f));
            _3x3Structures.add(Point2d.of(26.5f, 139.5f));

            BUNKER_NATURAL = Point2d.of(62.5f, 138.5f);
            FACTORIES.add(Point2d.of(41.5f, 144.5f));
            FACTORIES.add(Point2d.of(46.5f, 152.5f));

            STARPORTS.add(Point2d.of(37.5f, 135.5f));
            STARPORTS.add(Point2d.of(40.5f, 148.5f));
            STARPORTS.add(Point2d.of(29.5f, 146.5f));
            STARPORTS.add(Point2d.of(32.5f, 148.5f));
            STARPORTS.add(Point2d.of(35.5f, 150.5f));
            STARPORTS.add(Point2d.of(26.5f, 142.5f));
            STARPORTS.add(Point2d.of(27.5f, 134.5f));
            STARPORTS.add(Point2d.of(28.5f, 130.5f));
            STARPORTS.add(Point2d.of(34.5f, 129.5f));
            //STARPORTS.add(Point2d.of(47.5f, 146.5f)); removed for FACTORY2 pathing
            //STARPORTS.add(Point2d.of(46.5f, 152.5f)); FACTORY2
            STARPORTS.add(Point2d.of(48.5f, 156.5f));
            STARPORTS.add(Point2d.of(65.5f, 155.5f));
            STARPORTS.add(Point2d.of(60.5f, 158.5f));
            STARPORTS.add(Point2d.of(54.5f, 158.5f));

            TURRETS.add(Point2d.of(38.0f, 144.0f));
            TURRETS.add(Point2d.of(33.0f, 136.0f));
            TURRETS.add(Point2d.of(33.0f, 139.0f));

            MACRO_OCS.add(Point2d.of(30.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(35.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(35.5f, 120.5f));
            MACRO_OCS.add(Point2d.of(40.5f, 122.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 127.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 133.5f));
            MACRO_OCS.add(Point2d.of(42.5f, 138.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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
        } else {
            proxyBarracksPos = Point2d.of(72.5f, 105.5f);
            proxyBunkerPos = Point2d.of(62.5f, 139.5f);
            proxyBunkerPos2 = Point2d.of(55.5f, 119.5f);

            reaperBlockDepots.add(Point2d.of(143.0f, 58.0f)); //reaperJump1
            reaperBlock3x3s.add(Point2d.of(145.5f, 59.5f));
            reaperBlock3x3s.add(Point2d.of(140.5f, 56.5f));

            myMineralPos = Point2d.of(155.0f, 41.5f);
            enemyMineralPos = Point2d.of(37f, 146.5f);

            WALL_2x2 = Point2d.of(145.0f, 48.0f);
            MID_WALL_3x3 = Point2d.of(145.5f, 50.5f);
            MID_WALL_2x2 = Point2d.of(145f, 50f);
            WALL_3x3 = Point2d.of(142.5f, 51.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(164.5f, 51.5f));
            _3x3Structures.add(Point2d.of(162.5f, 57.5f));

            BUNKER_NATURAL = Point2d.of(129.5f, 49.5f);
            FACTORIES.add(Point2d.of(148.5f, 42.5f));
            FACTORIES.add(Point2d.of(141.5f, 42.5f));

            STARPORTS.add(Point2d.of(148.5f, 39.5f));
            STARPORTS.add(Point2d.of(154.5f, 39.5f));
            STARPORTS.add(Point2d.of(153.5f, 36.5f));
            STARPORTS.add(Point2d.of(160.5f, 40.5f));
            STARPORTS.add(Point2d.of(163.5f, 45.5f));
            STARPORTS.add(Point2d.of(162.5f, 54.5f));
            STARPORTS.add(Point2d.of(156.5f, 58.5f));
            STARPORTS.add(Point2d.of(152.5f, 52.5f));
            STARPORTS.add(Point2d.of(152.5f, 55.5f));
            //STARPORTS.add(Point2d.of(141.5f, 42.5f)); FACTORY2
            STARPORTS.add(Point2d.of(143.5f, 35.5f));
            STARPORTS.add(Point2d.of(141.5f, 31.5f));
            STARPORTS.add(Point2d.of(136.5f, 28.5f));
            STARPORTS.add(Point2d.of(131.5f, 28.5f));
            STARPORTS.add(Point2d.of(125.5f, 32.5f));

            TURRETS.add(Point2d.of(153.0f, 44.0f));
            TURRETS.add(Point2d.of(159.0f, 52.0f));
            TURRETS.add(Point2d.of(159.0f, 49.0f));

            MACRO_OCS.add(Point2d.of(161.5f, 62.5f));
            MACRO_OCS.add(Point2d.of(156.5f, 62.5f));
            MACRO_OCS.add(Point2d.of(156.5f, 67.5f));
            MACRO_OCS.add(Point2d.of(151.5f, 65.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 60.5f));
            MACRO_OCS.add(Point2d.of(146.5f, 54.5f));
            MACRO_OCS.add(Point2d.of(149.5f, 48.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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
        } else {
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
        } else {
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
        muleLetterPosList.add(Point2d.of(80.5f, 83.5f));
        muleLetterPosList.add(Point2d.of(89.5f, 83.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(77.5f, 54.5f);
            proxyBunkerPos = Point2d.of(42.5f, 59.5f);
            proxyBunkerPos2 = Point2d.of(54.5f, 50.5f);

            reaperBlockDepots.add(Point2d.of(130.0f, 127.0f));
            reaperBlockDepots.add(Point2d.of(130.0f, 125.0f));
            reaperBlock3x3s.add(Point2d.of(128.5f, 129.5f));

            myMineralPos = Point2d.of(150.0f, 141.5f);
            enemyMineralPos = Point2d.of(26.0f, 30.5f);
            WALL_2x2 = Point2d.of(144f, 125f);
            MID_WALL_2x2 = Point2d.of(146f, 125f);
            MID_WALL_3x3 = Point2d.of(146.5f, 125.5f);
            WALL_3x3 = Point2d.of(147.5f, 122.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(137.5f, 135.5f));
            _3x3Structures.add(Point2d.of(153.5f, 137.5f));

            FACTORIES.add(Point2d.of(149.5f, 134.5f));
            FACTORIES.add(Point2d.of(153.5f, 109.5f));
            BUNKER_NATURAL = Point2d.of(128.5f, 117.5f);

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
            //STARPORTS.add(Point2d.of(153.5f, 111.5f)); FACTORY2
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

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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
            proxyBarracksPos = Point2d.of(96.5f, 117.5f);
            proxyBunkerPos = Point2d.of(133.5f, 112.5f);
            proxyBunkerPos2 = Point2d.of(121.5f, 121.5f);

            reaperBlockDepots.add(Point2d.of(46.0f, 45.0f));
            reaperBlockDepots.add(Point2d.of(46.0f, 47.0f));
            reaperBlock3x3s.add(Point2d.of(47.5f, 42.5f));

            myMineralPos = Point2d.of(26.0f, 30.5f);
            enemyMineralPos = Point2d.of(150.0f, 141.5f);

            WALL_2x2 = Point2d.of(32f, 47f);
            MID_WALL_2x2 = Point2d.of(30f, 47f);
            MID_WALL_3x3 = Point2d.of(29.5f, 46.5f);
            WALL_3x3 = Point2d.of(28.5f, 49.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(22.5f, 35.5f));
            _3x3Structures.add(Point2d.of(27.5f, 20.5f));

            BUNKER_NATURAL = Point2d.of(47.5f, 54.5f);
            FACTORIES.add(Point2d.of(31.5f, 36.5f));
            FACTORIES.add(Point2d.of(20.5f, 59.5f));

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
            //STARPORTS.add(Point2d.of(20.5f, 59.5f)); FACTORY2
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

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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
        muleLetterPosList.add(Point2d.of(84.5f, 122.5f));
        muleLetterPosList.add(Point2d.of(95.5f, 122.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(98.5f, 89.5f);
            proxyBunkerPos = Point2d.of(49.5f, 73.5f);
            proxyBunkerPos2 = Point2d.of(66.5f, 78.5f);

            reaperBlockDepots.add(Point2d.of(136.0f, 144.0f));
            reaperBlock3x3s.add(Point2d.of(138.5f, 144.5f));

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
            FACTORIES.add(Point2d.of(144.5f, 157.5f));
            FACTORIES.add(Point2d.of(160.5f, 154.5f));

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
            //STARPORTS.add(Point2d.of(160.5f, 154.5f)); FACTORY2

            TURRETS.add(Point2d.of(135.0f, 168.0f));
            TURRETS.add(Point2d.of(144.0f, 163.0f));
            TURRETS.add(Point2d.of(138.0f, 168.0f));

            MACRO_OCS.add(Point2d.of(134.5f, 163.5f));
            MACRO_OCS.add(Point2d.of(129.5f, 163.5f));
            MACRO_OCS.add(Point2d.of(124.5f, 163.5f));
            MACRO_OCS.add(Point2d.of(118.5f, 163.5f)); //remove if drop turret
            MACRO_OCS.add(Point2d.of(120.5f, 168.5f));
            MACRO_OCS.add(Point2d.of(125.5f, 168.5f));
            MACRO_OCS.add(Point2d.of(134.5f, 147.5f));
            MACRO_OCS.add(Point2d.of(127.5f, 158.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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
            //extraDepots.add(Point2d.of(119f, 163f)); //drop turret

        }
        else {
            proxyBarracksPos = Point2d.of(100.5f, 123.5f);
            proxyBunkerPos = Point2d.of(151.5f, 138.5f);
            proxyBunkerPos2 = Point2d.of(131.5f, 133.5f);

            reaperBlockDepots.add(Point2d.of(64.0f, 68.0f));
            reaperBlock3x3s.add(Point2d.of(61.5f, 67.5f));

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

            BUNKER_NATURAL = Point2d.of(52.5f, 74.5f);
            FACTORIES.add(Point2d.of(57.5f, 55.5f));
            FACTORIES.add(Point2d.of(62.5f, 58.5f));

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
            //STARPORTS.add(Point2d.of(62.5f, 58.5f)); FACTORY2
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

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(MID_WALL_2x2);
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
        muleLetterPosList.add(Point2d.of(100.5f, 91.5f));
        muleLetterPosList.add(Point2d.of(100.5f, 76.5f));
        if (isTopPos) { //left spawn
            proxyBarracksPos = Point2d.of(132.5f, 97.5f);
            proxyBunkerPos = Point2d.of(158.5f, 77.5f);
            //proxyBunkerPos2 unnecessary

            myMineralPos = Point2d.of(25f, 51.5f);
            enemyMineralPos = Point2d.of(183f, 51.5f);
            //REPAIR_BAY = Point2d.of(144.5f, 133f);

            WALL_2x2 = Point2d.of(41.0f, 64.0f);
            MID_WALL_2x2 = Point2d.of(41f, 62f);
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
            FACTORIES.add(Point2d.of(34.5f, 57.5f));
            FACTORIES.add(Point2d.of(33.5f, 64.5f));

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
//            STARPORTS.add(Point2d.of(30.5f, 61.5f)); replace by FACTORY2
//            STARPORTS.add(Point2d.of(31.5f, 64.5f)); replace by FACTORY2


            TURRETS.add(Point2d.of(30.0f, 46.0f));
            TURRETS.add(Point2d.of(30.0f, 55.0f));
            TURRETS.add(Point2d.of(29.0f, 50.0f));

            MACRO_OCS.add(Point2d.of(45.5f, 49.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 55.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 54.5f));
            MACRO_OCS.add(Point2d.of(26.5f, 64.5f));
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
        else { //right spawn
            proxyBarracksPos = Point2d.of(68.5f, 98.5f);
            proxyBunkerPos = Point2d.of(49.5f, 77.5f);
            //proxyBunkerPos2 unnecessary

            myMineralPos = Point2d.of(183f, 51.5f);
            enemyMineralPos = Point2d.of(25f, 51.5f);
            //REPAIR_BAY = Point2d.of(32.5f, 41f);

            WALL_2x2 = Point2d.of(167.0f, 64.0f);
            WALL_3x3 = Point2d.of(164.5f, 60.5f);
            MID_WALL_2x2 = Point2d.of(167f, 62f);
            MID_WALL_3x3 = Point2d.of(167.5f, 61.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(171.5f, 50.5f));
            _3x3Structures.add(Point2d.of(169.5f, 40.5f));

            //REAPER_JUMP1 = Point2d.of(162.0f, 87.0f);
            BUNKER_NATURAL = Point2d.of(159.5f, 78.5f);
            FACTORIES.add(Point2d.of(174.5f, 61.5f));
            FACTORIES.add(Point2d.of(175.5f, 64.5f));

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
            //STARPORTS.add(Point2d.of(175.5f, 64.5f)); FACTORY2
            STARPORTS.add(Point2d.of(179.5f, 66.5f));
            STARPORTS.add(Point2d.of(175.5f, 71.5f));

            TURRETS.add(Point2d.of(178.0f, 46.0f));
            TURRETS.add(Point2d.of(178.0f, 55.0f));
            TURRETS.add(Point2d.of(184.0f, 49.0f));

            MACRO_OCS.add(Point2d.of(161.5f, 50.5f));
            MACRO_OCS.add(Point2d.of(162.5f, 56.5f));
            MACRO_OCS.add(Point2d.of(166.5f, 50.5f));
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
        muleLetterPosList.add(Point2d.of(134.5f, 104.5f));
        muleLetterPosList.add(Point2d.of(145.5f, 107.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(107.5f, 95.5f);
            proxyBunkerPos = Point2d.of(77.5f, 96.5f);
            proxyBunkerPos2 = Point2d.of(99.5f, 81.5f);

            reaperBlockDepots.add(Point2d.of(164.0f, 165.0f));
            reaperBlockDepots.add(Point2d.of(168.0f, 161.0f));
            reaperBlockDepots.add(Point2d.of(169.0f, 156.0f));
            reaperBlock3x3s.add(Point2d.of(166.5f, 163.5f));
            reaperBlock3x3s.add(Point2d.of(168.5f, 158.5f));

            myMineralPos = Point2d.of(190.0f, 173.5f);
            enemyMineralPos = Point2d.of(66.0f, 62.5f);

            WALL_2x2 = Point2d.of(178.0f, 159.0f);
            MID_WALL_2x2 = Point2d.of(176f, 159f);
            MID_WALL_3x3 = Point2d.of(175.5f, 159.5f);
            WALL_3x3 = Point2d.of(174.5f, 156.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(175.5f, 179.5f));
            _3x3Structures.add(Point2d.of(175.5f, 182.5f));

            BUNKER_NATURAL = Point2d.of(177.5f, 138.5f);
            FACTORIES.add(Point2d.of(182.5f, 160.5f));
            FACTORIES.add(Point2d.of(170.5f, 162.5f));

            STARPORTS.add(Point2d.of(182.5f, 163.5f));
            STARPORTS.add(Point2d.of(189.5f, 165.5f));
            STARPORTS.add(Point2d.of(192.5f, 168.5f));
            STARPORTS.add(Point2d.of(192.5f, 172.5f));
            STARPORTS.add(Point2d.of(189.5f, 179.5f));
            STARPORTS.add(Point2d.of(183.5f, 181.5f));
            STARPORTS.add(Point2d.of(178.5f, 182.5f));
            //STARPORTS.add(Point2d.of(170.5f, 162.5f)); FACTORY2
            STARPORTS.add(Point2d.of(169.5f, 165.5f));
            STARPORTS.add(Point2d.of(167.5f, 168.5f));
            STARPORTS.add(Point2d.of(173.5f, 168.5f));
            STARPORTS.add(Point2d.of(175.5f, 165.5f));
            STARPORTS.add(Point2d.of(176.5f, 162.5f));
            STARPORTS.add(Point2d.of(193.5f, 153.5f));
            STARPORTS.add(Point2d.of(187.5f, 154.5f));

            TURRETS.add(Point2d.of(187.0f, 172.0f));
            TURRETS.add(Point2d.of(178.0f, 177.0f));
            TURRETS.add(Point2d.of(181.0f, 177.0f));

            MACRO_OCS.add(Point2d.of(182.5f, 167.5f));
            MACRO_OCS.add(Point2d.of(176.5f, 172.5f));
            MACRO_OCS.add(Point2d.of(170.5f, 173.5f));
            MACRO_OCS.add(Point2d.of(170.5f, 178.5f));
            MACRO_OCS.add(Point2d.of(189.5f, 161.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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
            proxyBarracksPos = Point2d.of(147.5f, 139.5f);
            proxyBunkerPos = Point2d.of(178.5f, 139.5f);
            proxyBunkerPos2 = Point2d.of(156.5f, 154.5f);

            reaperBlockDepots.add(Point2d.of(92.0f, 71.0f)); //reaperJump1
            reaperBlockDepots.add(Point2d.of(88.0f, 75.0f)); //reaperJump3
            reaperBlockDepots.add(Point2d.of(87.0f, 80.0f)); //reaperJump5
            reaperBlock3x3s.add(Point2d.of(89.5f, 72.5f));
            reaperBlock3x3s.add(Point2d.of(87.5f, 77.5f));

            myMineralPos = Point2d.of(66.0f, 62.5f);
            enemyMineralPos = Point2d.of(190f, 173.5f);

            WALL_2x2 = Point2d.of(78.0f, 77.0f);
            WALL_3x3 = Point2d.of(81.5f, 79.5f);
            MID_WALL_2x2 = Point2d.of(80f, 77f);
            MID_WALL_3x3 = Point2d.of(80.5f, 76.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(80.5f, 56.5f));
            _3x3Structures.add(Point2d.of(63.5f, 62.5f));

            BUNKER_NATURAL = Point2d.of(78.5f, 97.5f);
            FACTORIES.add(Point2d.of(74.5f, 73.5f));
            FACTORIES.add(Point2d.of(83.5f, 73.5f));

            STARPORTS.add(Point2d.of(78.5f, 59.5f));
            STARPORTS.add(Point2d.of(78.5f, 53.5f));
            STARPORTS.add(Point2d.of(67.5f, 54.5f));
            STARPORTS.add(Point2d.of(72.5f, 54.5f));
            STARPORTS.add(Point2d.of(65.5f, 57.5f));
            STARPORTS.add(Point2d.of(62.5f, 69.5f));
            STARPORTS.add(Point2d.of(67.5f, 69.5f));
            STARPORTS.add(Point2d.of(74.5f, 67.5f));
            STARPORTS.add(Point2d.of(80.5f, 67.5f));
            STARPORTS.add(Point2d.of(86.5f, 67.5f));
            STARPORTS.add(Point2d.of(89.5f, 69.5f));
            STARPORTS.add(Point2d.of(84.5f, 70.5f));
            //STARPORTS.add(Point2d.of(83.5f, 73.5f)); FACTORY2
            STARPORTS.add(Point2d.of(60.5f, 83.5f));

            TURRETS.add(Point2d.of(75.0f, 59.0f));
            TURRETS.add(Point2d.of(69.0f, 64.0f));
            TURRETS.add(Point2d.of(71.0f, 59.0f));

            MACRO_OCS.add(Point2d.of(65.5f, 73.5f));
            MACRO_OCS.add(Point2d.of(70.5f, 73.5f));
            MACRO_OCS.add(Point2d.of(79.5f, 63.5f));
            MACRO_OCS.add(Point2d.of(85.5f, 63.5f));
            MACRO_OCS.add(Point2d.of(86.5f, 58.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
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

    private static void setLocationsForJagannatha(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(63.5f, 88.5f));
        muleLetterPosList.add(Point2d.of(76.5f, 75.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(100.5f, 65.5f);
            proxyBunkerPos = Point2d.of(75.5f, 42.5f);
            proxyBunkerPos2 = Point2d.of(59.5f, 62.5f);

            reaperBlockDepots.add(Point2d.of(116.0f, 134.0f));
            reaperBlockDepots.add(Point2d.of(114.0f, 135.0f));

            myMineralPos = Point2d.of(134, 152.5f);
            enemyMineralPos = Point2d.of(34f, 33.5f);

            WALL_2x2 = Point2d.of(113.0f, 146.0f);
            WALL_3x3 = Point2d.of(110.5f, 142.5f);
            MID_WALL_3x3 = Point2d.of(113.5f, 143.5f);
            MID_WALL_2x2 = Point2d.of(113f, 144f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(126.5f, 137.5f));
            _3x3Structures.add(Point2d.of(132.5f, 137.5f));

            BUNKER_NATURAL = Point2d.of(93.5f, 144.5f);
            FACTORIES.add(Point2d.of(114.5f, 149.5f));
            FACTORIES.add(Point2d.of(118.5f, 141.5f));

            STARPORTS.add(Point2d.of(115.5f, 160.5f));
            STARPORTS.add(Point2d.of(122.5f, 161.5f));
            STARPORTS.add(Point2d.of(128.5f, 160.5f));
            STARPORTS.add(Point2d.of(133.5f, 160.5f));
            STARPORTS.add(Point2d.of(114.5f, 156.5f));
            STARPORTS.add(Point2d.of(113.5f, 152.5f));
            STARPORTS.add(Point2d.of(120.5f, 156.5f));
            STARPORTS.add(Point2d.of(136.5f, 157.5f));
            STARPORTS.add(Point2d.of(135.5f, 154.5f));
            STARPORTS.add(Point2d.of(137.5f, 149.5f));
            STARPORTS.add(Point2d.of(135.5f, 145.5f));
            STARPORTS.add(Point2d.of(119.5f, 138.5f));
            STARPORTS.add(Point2d.of(116.5f, 136.5f));
            STARPORTS.add(Point2d.of(111.5f, 139.5f));

            TURRETS.add(Point2d.of(131.0f, 148.0f));
            TURRETS.add(Point2d.of(126.0f, 156.0f));
            TURRETS.add(Point2d.of(131.0f, 151.0f));

            MACRO_OCS.add(Point2d.of(126.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(131.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(136.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(121.5f, 151.5f));
            MACRO_OCS.add(Point2d.of(121.5f, 145.5f));
            MACRO_OCS.add(Point2d.of(126.5f, 146.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(113.0f, 160.0f));
            extraDepots.add(Point2d.of(120.0f, 161.0f));
            extraDepots.add(Point2d.of(125.0f, 163.0f));
            extraDepots.add(Point2d.of(134.0f, 163.0f));
            extraDepots.add(Point2d.of(118.0f, 158.0f));
            extraDepots.add(Point2d.of(136.0f, 152.0f));
            extraDepots.add(Point2d.of(132.0f, 163.0f));
            extraDepots.add(Point2d.of(134.0f, 158.0f));
            extraDepots.add(Point2d.of(133.0f, 146.0f));
            extraDepots.add(Point2d.of(138.0f, 147.0f));
            extraDepots.add(Point2d.of(138.0f, 152.0f));
            extraDepots.add(Point2d.of(126.0f, 159.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(67.5f, 119.5f);
            proxyBunkerPos = Point2d.of(92.5f, 143.5f);
            proxyBunkerPos2 = Point2d.of(108.5f, 123.5f);

            reaperBlockDepots.add(Point2d.of(52.0f, 52.0f));
            reaperBlockDepots.add(Point2d.of(54.0f, 51.0f));

            myMineralPos = Point2d.of(34f, 33.5f);
            enemyMineralPos = Point2d.of(134, 152.5f);

            WALL_2x2 = Point2d.of(55.0f, 40.0f);
            MID_WALL_3x3 = Point2d.of(54.5f, 42.5f);
            MID_WALL_2x2 = Point2d.of(55f, 42f);
            WALL_3x3 = Point2d.of(57.5f, 43.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(46.5f, 39.5f));
            _3x3Structures.add(Point2d.of(47.5f, 26.5f));

            BUNKER_NATURAL = Point2d.of(73.5f, 40.5f);
            FACTORIES.add(Point2d.of(49.5f, 38.5f));
            FACTORIES.add(Point2d.of(53.5f, 33.5f));

            STARPORTS.add(Point2d.of(44.5f, 24.5f));
            STARPORTS.add(Point2d.of(38.5f, 24.5f));
            STARPORTS.add(Point2d.of(32.5f, 26.5f));
            STARPORTS.add(Point2d.of(46.5f, 30.5f));
            STARPORTS.add(Point2d.of(46.5f, 33.5f));
            STARPORTS.add(Point2d.of(29.5f, 29.5f));
            STARPORTS.add(Point2d.of(30.5f, 32.5f));
            STARPORTS.add(Point2d.of(29.5f, 36.5f));
            STARPORTS.add(Point2d.of(31.5f, 40.5f));
            STARPORTS.add(Point2d.of(46.5f, 36.5f));
            STARPORTS.add(Point2d.of(46.5f, 43.5f));
            STARPORTS.add(Point2d.of(40.5f, 48.5f));
            STARPORTS.add(Point2d.of(34.5f, 48.5f));
            STARPORTS.add(Point2d.of(61.5f, 24.5f));

            TURRETS.add(Point2d.of(37.0f, 38.0f));
            TURRETS.add(Point2d.of(42.0f, 30.0f));
            TURRETS.add(Point2d.of(37.0f, 35.0f));

            MACRO_OCS.add(Point2d.of(52.5f, 27.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 44.5f));
            MACRO_OCS.add(Point2d.of(36.5f, 44.5f));
            MACRO_OCS.add(Point2d.of(31.5f, 44.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 47.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 47.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 39.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(53.0f, 24.0f));
            extraDepots.add(Point2d.of(56.0f, 27.0f));
            extraDepots.add(Point2d.of(52.0f, 31.0f));
            extraDepots.add(Point2d.of(54.0f, 31.0f));
            extraDepots.add(Point2d.of(51.0f, 33.0f));
            extraDepots.add(Point2d.of(51.0f, 35.0f));
            extraDepots.add(Point2d.of(43.0f, 22.0f));
            extraDepots.add(Point2d.of(35.0f, 22.0f));
            extraDepots.add(Point2d.of(30.0f, 27.0f));
            extraDepots.add(Point2d.of(32.0f, 38.0f));
            extraDepots.add(Point2d.of(33.0f, 24.0f));
            extraDepots.add(Point2d.of(38.0f, 41.0f));
            extraDepots.add(Point2d.of(36.0f, 41.0f));
            extraDepots.add(Point2d.of(35.0f, 24.0f));
            extraDepots.add(Point2d.of(41.0f, 26.0f));
        }
    }

    private static void setLocationsForLightshade(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(89.5f, 93.5f));
        muleLetterPosList.add(Point2d.of(98.5f, 101.5f));

        if (isTopPos) {
            proxyBarracksPos = Point2d.of(112.5f, 85.5f);
            proxyBunkerPos = Point2d.of(136.5f, 66.5f);
            proxyBunkerPos2 = Point2d.of(117.5f, 52.5f);

            reaperBlockDepots.add(Point2d.of(55.0f, 120.0f));
            reaperBlockDepots.add(Point2d.of(52.0f, 117.0f));
            reaperBlock3x3s.add(Point2d.of(52.5f, 119.5f));

            myMineralPos = Point2d.of(33f, 132.5f);
            enemyMineralPos = Point2d.of(151f, 31.5f);

            WALL_2x2 = Point2d.of(44.0f, 117.0f);
            WALL_3x3 = Point2d.of(47.5f, 114.5f);
            MID_WALL_3x3 = Point2d.of(46.5f, 117.5f);
            MID_WALL_2x2 = Point2d.of(46f, 117f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(31.5f, 134.5f));
            _3x3Structures.add(Point2d.of(30.5f, 129.5f));

            BUNKER_NATURAL = Point2d.of(45.5f, 99.5f);
            FACTORIES.add(Point2d.of(41.5f, 123.5f));
            FACTORIES.add(Point2d.of(46.5f, 122.5f));

            STARPORTS.add(Point2d.of(30.5f, 126.5f));
            STARPORTS.add(Point2d.of(29.5f, 122.5f));
            STARPORTS.add(Point2d.of(30.5f, 119.5f));
            STARPORTS.add(Point2d.of(36.5f, 124.5f));
            STARPORTS.add(Point2d.of(35.5f, 121.5f));
            STARPORTS.add(Point2d.of(36.5f, 118.5f));
            STARPORTS.add(Point2d.of(32.5f, 138.5f));
            STARPORTS.add(Point2d.of(35.5f, 140.5f));
            STARPORTS.add(Point2d.of(40.5f, 142.5f));
            STARPORTS.add(Point2d.of(50.5f, 141.5f));
            STARPORTS.add(Point2d.of(57.5f, 127.5f));
            STARPORTS.add(Point2d.of(55.5f, 123.5f));
            STARPORTS.add(Point2d.of(39.5f, 126.5f));
            STARPORTS.add(Point2d.of(44.5f, 125.5f));
            STARPORTS.add(Point2d.of(28.5f, 108.5f));

            TURRETS.add(Point2d.of(45.0f, 136.0f));
            TURRETS.add(Point2d.of(36.0f, 131.0f));
            TURRETS.add(Point2d.of(42.0f, 136.0f));

            MACRO_OCS.add(Point2d.of(56.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(56.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(51.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(50.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(51.5f, 127.5f));
            MACRO_OCS.add(Point2d.of(45.5f, 131.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(38.0f, 142.0f));
            extraDepots.add(Point2d.of(43.0f, 144.0f));
            extraDepots.add(Point2d.of(28.0f, 129.0f));
            extraDepots.add(Point2d.of(30.0f, 132.0f));
            extraDepots.add(Point2d.of(32.0f, 117.0f));
            extraDepots.add(Point2d.of(40.0f, 140.0f));
            extraDepots.add(Point2d.of(41.0f, 118.0f));
            extraDepots.add(Point2d.of(34.0f, 116.0f));
            extraDepots.add(Point2d.of(42.0f, 140.0f));
            extraDepots.add(Point2d.of(45.0f, 141.0f));
            extraDepots.add(Point2d.of(47.0f, 140.0f));
            extraDepots.add(Point2d.of(47.0f, 138.0f));
            extraDepots.add(Point2d.of(47.0f, 142.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(68.5f, 76.5f);
            proxyBunkerPos = Point2d.of(46.5f, 96.5f);
            proxyBunkerPos2 = Point2d.of(66.5f, 112.5f);

            reaperBlockDepots.add(Point2d.of(129.0f, 44.0f));
            reaperBlockDepots.add(Point2d.of(132.0f, 47.0f));
            reaperBlock3x3s.add(Point2d.of(131.5f, 44.5f));

            myMineralPos = Point2d.of(151f, 31.5f);
            enemyMineralPos = Point2d.of(33f, 132.5f);

            WALL_2x2 = Point2d.of(140.0f, 47.0f);
            MID_WALL_3x3 = Point2d.of(137.5f, 46.5f);
            MID_WALL_2x2 = Point2d.of(138f, 47f);
            WALL_3x3 = Point2d.of(136.5f, 49.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(153.5f, 31.5f));
            _3x3Structures.add(Point2d.of(148.5f, 45.5f));

            BUNKER_NATURAL = Point2d.of(138.5f, 65.5f);
            FACTORIES.add(Point2d.of(142.5f, 45.5f));
            FACTORIES.add(Point2d.of(137.5f, 41.5f));

            STARPORTS.add(Point2d.of(153.5f, 35.5f));
            STARPORTS.add(Point2d.of(153.5f, 41.5f));
            STARPORTS.add(Point2d.of(150.5f, 38.5f));
            STARPORTS.add(Point2d.of(148.5f, 42.5f));
            STARPORTS.add(Point2d.of(151.5f, 44.5f));
            STARPORTS.add(Point2d.of(149.5f, 25.5f));
            STARPORTS.add(Point2d.of(146.5f, 23.5f));
            STARPORTS.add(Point2d.of(141.5f, 22.5f));
            STARPORTS.add(Point2d.of(136.5f, 23.5f));
            STARPORTS.add(Point2d.of(136.5f, 36.5f));
            STARPORTS.add(Point2d.of(131.5f, 37.5f));
            STARPORTS.add(Point2d.of(126.5f, 36.5f));
            STARPORTS.add(Point2d.of(134.5f, 39.5f));
            STARPORTS.add(Point2d.of(129.5f, 40.5f));
            STARPORTS.add(Point2d.of(153.5f, 55.5f));

            TURRETS.add(Point2d.of(139.0f, 28.0f));
            TURRETS.add(Point2d.of(148.0f, 33.0f));
            TURRETS.add(Point2d.of(142.0f, 28.0f));

            MACRO_OCS.add(Point2d.of(143.5f, 37.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 33.5f));
            MACRO_OCS.add(Point2d.of(126.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(126.5f, 27.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 28.5f));
            MACRO_OCS.add(Point2d.of(131.5f, 23.5f));
            MACRO_OCS.add(Point2d.of(138.5f, 32.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(152.0f, 47.0f));
            extraDepots.add(Point2d.of(148.0f, 40.0f));
            extraDepots.add(Point2d.of(154.0f, 29.0f));
            extraDepots.add(Point2d.of(150.0f, 48.0f));
            extraDepots.add(Point2d.of(152.0f, 29.0f));
            extraDepots.add(Point2d.of(141.0f, 20.0f));
            extraDepots.add(Point2d.of(137.0f, 26.0f));
            extraDepots.add(Point2d.of(137.0f, 28.0f));
            extraDepots.add(Point2d.of(143.0f, 20.0f));
            extraDepots.add(Point2d.of(152.0f, 27.0f));
            extraDepots.add(Point2d.of(146.0f, 43.0f));
            extraDepots.add(Point2d.of(144.0f, 24.0f));
            extraDepots.add(Point2d.of(142.0f, 25.0f));
        }
    }

    private static void setLocationsForNightshade(boolean isTopPos) {
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(122.5f, 91.5f);
            proxyBunkerPos = Point2d.of(140.5f, 59.5f);
            proxyBunkerPos2 = Point2d.of(131.5f, 53.5f);

            reaperBlockDepots.add(Point2d.of(52.0f, 126.0f));
            reaperBlockDepots.add(Point2d.of(51.0f, 124.0f));
            reaperBlock3x3s.add(Point2d.of(53.5f, 128.5f));

            myMineralPos = Point2d.of(34f, 139.5f);
            enemyMineralPos = Point2d.of(158f, 32.5f);
            WALL_2x2 = Point2d.of(42f, 123f);
            MID_WALL_2x2 = Point2d.of(40f, 123f);
            MID_WALL_3x3 = Point2d.of(39.5f, 123.5f);
            WALL_3x3 = Point2d.of(38.5f, 120.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(32.5f, 125.5f));
            _3x3Structures.add(Point2d.of(49.5f, 146.5f));

            BUNKER_NATURAL = Point2d.of(52.5f, 113.5f);
            FACTORIES.add(Point2d.of(44.5f, 124.5f));

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

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(MID_WALL_2x2); //wall2x2
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
            proxyBarracksPos = Point2d.of(65.5f, 80.5f);
            proxyBunkerPos = Point2d.of(51.5f, 112.5f);
            proxyBunkerPos2 = Point2d.of(60.5f, 118.5f);

            reaperBlockDepots.add(Point2d.of(140.0f, 46.0f));
            reaperBlockDepots.add(Point2d.of(141.0f, 48.0f));
            reaperBlock3x3s.add(Point2d.of(137.5f, 43.5f));

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

            BUNKER_NATURAL = Point2d.of(138.5f, 58.5f);
            FACTORIES.add(Point2d.of(156.5f, 44.5f));

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

            extraDepots.add(WALL_2x2); //wall2x2
            extraDepots.add(MID_WALL_2x2);
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

    private static void setLocationsForOxide(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(65.5f, 98.5f));
        muleLetterPosList.add(Point2d.of(77.5f, 98.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(92.5f, 91.5f);
            proxyBunkerPos = Point2d.of(75.5f, 59.5f);
            proxyBunkerPos2 = Point2d.of(57.5f, 80.5f);

            reaperBlockDepots.add(Point2d.of(135.0f, 136.0f));
            reaperBlockDepots.add(Point2d.of(133.0f, 136.0f));
            reaperBlock3x3s.add(Point2d.of(137.5f, 134.5f));

            myMineralPos = Point2d.of(148f, 154.5f);
            enemyMineralPos = Point2d.of(44f, 49.5f);

            WALL_2x2 = Point2d.of(133.0f, 144.0f);
            WALL_3x3 = Point2d.of(130.5f, 140.5f);
            MID_WALL_3x3 = Point2d.of(133.5f, 141.5f);
            MID_WALL_2x2 = Point2d.of(133f, 142f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(133.5f, 138.5f));
            _3x3Structures.add(Point2d.of(130.5f, 157.5f));

            BUNKER_NATURAL = Point2d.of(117.5f, 143.5f);
            FACTORIES.add(Point2d.of(134.5f, 146.5f));
            FACTORIES.add(Point2d.of(141.5f, 142.5f));

            STARPORTS.add(Point2d.of(151.5f, 158.5f));
            STARPORTS.add(Point2d.of(148.5f, 156.5f));
            STARPORTS.add(Point2d.of(143.5f, 157.5f));
            STARPORTS.add(Point2d.of(137.5f, 157.5f));
            STARPORTS.add(Point2d.of(137.5f, 154.5f));
            STARPORTS.add(Point2d.of(136.5f, 150.5f));
            STARPORTS.add(Point2d.of(157.5f, 147.5f));
            STARPORTS.add(Point2d.of(156.5f, 150.5f));
            STARPORTS.add(Point2d.of(155.5f, 153.5f));
            STARPORTS.add(Point2d.of(154.5f, 141.5f));
            STARPORTS.add(Point2d.of(138.5f, 140.5f));
            STARPORTS.add(Point2d.of(137.5f, 137.5f));
            STARPORTS.add(Point2d.of(144.5f, 138.5f));
            STARPORTS.add(Point2d.of(143.5f, 135.5f));

            TURRETS.add(Point2d.of(152.0f, 144.0f));
            TURRETS.add(Point2d.of(146.0f, 152.0f));
            TURRETS.add(Point2d.of(152.0f, 147.0f));

            MACRO_OCS.add(Point2d.of(147.5f, 142.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(150.5f, 132.5f));
            MACRO_OCS.add(Point2d.of(144.5f, 131.5f));
            MACRO_OCS.add(Point2d.of(155.5f, 137.5f));
            MACRO_OCS.add(Point2d.of(142.5f, 147.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(143.0f, 152.0f));
            extraDepots.add(Point2d.of(141.0f, 152.0f));
            extraDepots.add(Point2d.of(143.0f, 160.0f));
            extraDepots.add(Point2d.of(134.0f, 153.0f));
            extraDepots.add(Point2d.of(146.0f, 155.0f));
            extraDepots.add(Point2d.of(141.0f, 160.0f));
            extraDepots.add(Point2d.of(156.0f, 157.0f));
            extraDepots.add(Point2d.of(160.0f, 153.0f));
            extraDepots.add(Point2d.of(154.0f, 156.0f));
            extraDepots.add(Point2d.of(158.0f, 145.0f));
            extraDepots.add(Point2d.of(158.0f, 143.0f));
            extraDepots.add(Point2d.of(153.0f, 154.0f));
            extraDepots.add(Point2d.of(144.0f, 144.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(98.5f, 114.5f);
            proxyBunkerPos = Point2d.of(116.5f, 144.5f);
            proxyBunkerPos2 = Point2d.of(134.5f, 123.5f);

            reaperBlockDepots.add(Point2d.of(57.0f, 68.0f));
            reaperBlockDepots.add(Point2d.of(59.0f, 68.0f));
            reaperBlock3x3s.add(Point2d.of(54.5f, 69.5f));

            myMineralPos = Point2d.of(44f, 49.5f);
            enemyMineralPos = Point2d.of(148f, 154.5f);

            WALL_2x2 = Point2d.of(59.0f, 60.0f);
            MID_WALL_3x3 = Point2d.of(58.5f, 62.5f);
            MID_WALL_2x2 = Point2d.of(59f, 62f);
            WALL_3x3 = Point2d.of(61.5f, 63.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(51.5f, 69.5f));
            _3x3Structures.add(Point2d.of(61.5f, 46.5f));

            BUNKER_NATURAL = Point2d.of(74.5f, 60.5f);
            FACTORIES.add(Point2d.of(55.5f, 57.5f));
            FACTORIES.add(Point2d.of(48.5f, 57.5f));

            STARPORTS.add(Point2d.of(48.5f, 45.5f));
            STARPORTS.add(Point2d.of(37.5f, 46.5f));
            STARPORTS.add(Point2d.of(43.5f, 46.5f));
            STARPORTS.add(Point2d.of(55.5f, 51.5f));
            STARPORTS.add(Point2d.of(49.5f, 52.5f));
            STARPORTS.add(Point2d.of(51.5f, 47.5f));
            STARPORTS.add(Point2d.of(36.5f, 50.5f));
            STARPORTS.add(Point2d.of(32.5f, 56.5f));
            STARPORTS.add(Point2d.of(32.5f, 53.5f));
            STARPORTS.add(Point2d.of(52.5f, 65.5f));
            STARPORTS.add(Point2d.of(49.5f, 63.5f));
            STARPORTS.add(Point2d.of(35.5f, 63.5f));
            STARPORTS.add(Point2d.of(35.5f, 66.5f));
            //STARPORTS.add(Point2d.of(49.5f, 60.5f)); //removed to make space for 2nd factory tanks

            TURRETS.add(Point2d.of(40.0f, 60.0f));
            TURRETS.add(Point2d.of(40.0f, 57.0f));
            TURRETS.add(Point2d.of(46.0f, 52.0f));

            MACRO_OCS.add(Point2d.of(44.5f, 61.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 66.5f));
            MACRO_OCS.add(Point2d.of(46.5f, 67.5f));
            MACRO_OCS.add(Point2d.of(41.5f, 71.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 72.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(56.0f, 47.0f));
            extraDepots.add(Point2d.of(56.0f, 49.0f));
            extraDepots.add(Point2d.of(56.0f, 54.0f));
            extraDepots.add(Point2d.of(54.0f, 54.0f));
            extraDepots.add(Point2d.of(51.0f, 50.0f));
            extraDepots.add(Point2d.of(46.0f, 48.0f));
            extraDepots.add(Point2d.of(41.0f, 44.0f));
            extraDepots.add(Point2d.of(53.0f, 50.0f));
            extraDepots.add(Point2d.of(42.0f, 49.0f));
            extraDepots.add(Point2d.of(32.0f, 51.0f));
            extraDepots.add(Point2d.of(34.0f, 51.0f));
            extraDepots.add(Point2d.of(35.0f, 58.0f));
            extraDepots.add(Point2d.of(35.0f, 60.0f));
            extraDepots.add(Point2d.of(40.0f, 48.0f));
        }
    }

    private static void setLocationsForPillarsOfGold(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(75.5f, 86.5f));
        muleLetterPosList.add(Point2d.of(86.5f, 74.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(79.5f, 62.5f);
            proxyBunkerPos = Point2d.of(61.5f, 37.5f);
            proxyBunkerPos2 = Point2d.of(53.5f, 59.5f);

            reaperBlockDepots.add(Point2d.of(123.0f, 122.0f));
            reaperBlockDepots.add(Point2d.of(120.0f, 125.0f));
            reaperBlock3x3s.add(Point2d.of(122.5f, 124.5f));

            myMineralPos = Point2d.of(145.0f, 132.5f);
            enemyMineralPos = Point2d.of(23.0f,37.5f);

            WALL_2x2 = Point2d.of(123.0f, 134.0f);
            WALL_3x3 = Point2d.of(120.5f, 130.5f);
            MID_WALL_3x3 = Point2d.of(123.5f, 131.5f);
            MID_WALL_2x2 = Point2d.of(123f, 132f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(147.5f, 134.5f));
            _3x3Structures.add(Point2d.of(147.5f, 131.5f));

            BUNKER_NATURAL = Point2d.of(105.5f, 135.5f);
            FACTORIES.add(Point2d.of(124.5f, 136.5f));
            FACTORIES.add(Point2d.of(119.5f, 147.5f));

            STARPORTS.add(Point2d.of(130.5f, 147.5f));
            STARPORTS.add(Point2d.of(131.5f, 143.5f));
            STARPORTS.add(Point2d.of(137.5f, 146.5f));
            STARPORTS.add(Point2d.of(137.5f, 143.5f));
            STARPORTS.add(Point2d.of(143.5f, 142.5f));
            STARPORTS.add(Point2d.of(145.5f, 139.5f));
            STARPORTS.add(Point2d.of(145.5f, 127.5f));
            STARPORTS.add(Point2d.of(142.5f, 125.5f));
            STARPORTS.add(Point2d.of(139.5f, 123.5f));
            STARPORTS.add(Point2d.of(134.5f, 124.5f));
            STARPORTS.add(Point2d.of(134.5f, 128.5f));
            STARPORTS.add(Point2d.of(136.5f, 121.5f));
            STARPORTS.add(Point2d.of(131.5f, 122.5f));
            STARPORTS.add(Point2d.of(131.5f, 119.5f));
            STARPORTS.add(Point2d.of(123.5f, 128.5f));
            //STARPORTS.add(Point2d.of(119.5f, 147.5f)); FACTORY2

            TURRETS.add(Point2d.of(140.0f, 129.0f));
            TURRETS.add(Point2d.of(140.0f, 138.0f));
            TURRETS.add(Point2d.of(142.0f, 135.0f));

            MACRO_OCS.add(Point2d.of(132.5f, 134.5f));
            MACRO_OCS.add(Point2d.of(132.5f, 139.5f));
            MACRO_OCS.add(Point2d.of(127.5f, 143.5f));
            MACRO_OCS.add(Point2d.of(129.5f, 128.5f));
            MACRO_OCS.add(Point2d.of(126.5f, 123.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(128.0f, 147.0f));
            extraDepots.add(Point2d.of(135.0f, 147.0f));
            extraDepots.add(Point2d.of(142.0f, 145.0f));
            extraDepots.add(Point2d.of(134.0f, 145.0f));
            extraDepots.add(Point2d.of(143.0f, 140.0f));
            extraDepots.add(Point2d.of(148.0f, 137.0f));
            extraDepots.add(Point2d.of(129.0f, 120.0f));
            extraDepots.add(Point2d.of(132.0f, 125.0f));
            extraDepots.add(Point2d.of(146.0f, 137.0f));
            extraDepots.add(Point2d.of(130.0f, 125.0f));
            extraDepots.add(Point2d.of(136.0f, 141.0f));
            extraDepots.add(Point2d.of(136.0f, 137.0f));
            extraDepots.add(Point2d.of(136.0f, 139.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(86.5f, 109.5f);
            proxyBunkerPos = Point2d.of(106.5f, 134.5f);
            proxyBunkerPos2 = Point2d.of(114.5f, 112.5f);

            reaperBlockDepots.add(Point2d.of(45.0f, 50.0f));
            reaperBlockDepots.add(Point2d.of(48.0f, 47.0f));
            reaperBlock3x3s.add(Point2d.of(45.5f, 47.5f));

            myMineralPos = Point2d.of(23.0f, 37.5f);
            enemyMineralPos = Point2d.of(145.0f, 132.5f);

            WALL_2x2 = Point2d.of(45f, 38f);
            MID_WALL_3x3 = Point2d.of(44.5f, 40.5f);
            MID_WALL_2x2 = Point2d.of(45f, 40f);
            WALL_3x3 = Point2d.of(47.5f, 41.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(20.5f, 40.5f));
            _3x3Structures.add(Point2d.of(20.5f, 37.5f));

            BUNKER_NATURAL = Point2d.of(62.5f, 36.5f);
            FACTORIES.add(Point2d.of(41.5f, 35.5f));
            FACTORIES.add(Point2d.of(42.5f, 45.5f));

            STARPORTS.add(Point2d.of(20.5f, 33.5f));
            STARPORTS.add(Point2d.of(27.5f, 26.5f));
            STARPORTS.add(Point2d.of(23.5f, 30.5f));
            STARPORTS.add(Point2d.of(20.5f, 44.5f));
            STARPORTS.add(Point2d.of(23.5f, 46.5f));
            STARPORTS.add(Point2d.of(26.5f, 48.5f));
            STARPORTS.add(Point2d.of(31.5f, 44.5f));
            STARPORTS.add(Point2d.of(31.5f, 47.5f));
            STARPORTS.add(Point2d.of(34.5f, 49.5f));
            STARPORTS.add(Point2d.of(34.5f, 52.5f));
            STARPORTS.add(Point2d.of(39.5f, 49.5f));
            //STARPORTS.add(Point2d.of(42.5f, 45.5f)); FACTORY2
            STARPORTS.add(Point2d.of(47.5f, 24.5f));
            STARPORTS.add(Point2d.of(53.5f, 20.5f));
            STARPORTS.add(Point2d.of(58.5f, 20.5f));
            STARPORTS.add(Point2d.of(29.5f, 50.5f));

            TURRETS.add(Point2d.of(28.0f, 43.0f));
            TURRETS.add(Point2d.of(28.0f, 34.0f));
            TURRETS.add(Point2d.of(26.0f, 37.0f));

            MACRO_OCS.add(Point2d.of(35.5f, 37.5f));
            MACRO_OCS.add(Point2d.of(35.5f, 32.5f));
            MACRO_OCS.add(Point2d.of(34.5f, 25.5f));
            MACRO_OCS.add(Point2d.of(39.5f, 27.5f));
            MACRO_OCS.add(Point2d.of(37.5f, 44.5f));


            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(39.0f, 24.0f));
            extraDepots.add(Point2d.of(31.0f, 24.0f));
            extraDepots.add(Point2d.of(36.0f, 29.0f));
            extraDepots.add(Point2d.of(25.0f, 28.0f));
            extraDepots.add(Point2d.of(30.0f, 28.0f));
            extraDepots.add(Point2d.of(34.0f, 29.0f));
            extraDepots.add(Point2d.of(32.0f, 29.0f));
            extraDepots.add(Point2d.of(32.0f, 31.0f));
            extraDepots.add(Point2d.of(32.0f, 33.0f));
            extraDepots.add(Point2d.of(32.0f, 35.0f));
            extraDepots.add(Point2d.of(42.0f, 31.0f));
            extraDepots.add(Point2d.of(40.0f, 31.0f));
            extraDepots.add(Point2d.of(42.0f, 33.0f));
            extraDepots.add(Point2d.of(40.0f, 33.0f));
            extraDepots.add(Point2d.of(42.0f, 43.0f));
        }
    }

    private static void setLocationsForRomanticide(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(90.5f, 79.5f));
        muleLetterPosList.add(Point2d.of(102.5f, 81.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(110.5f, 87.5f);
            proxyBunkerPos = Point2d.of(144.5f, 65.5f);
            proxyBunkerPos2 = Point2d.of(131.5f, 52.5f);

            reaperBlockDepots.add(Point2d.of(55.0f, 124.0f));
            reaperBlockDepots.add(Point2d.of(58.0f, 129.0f));
            reaperBlock3x3s.add(Point2d.of(52.5f, 122.5f));
            reaperBlock3x3s.add(Point2d.of(56.5f, 126.5f));

            myMineralPos = Point2d.of(34f, 136.5f);
            enemyMineralPos = Point2d.of(166f, 35.5f);

            WALL_2x2 = Point2d.of(43.0f, 116.0f);
            WALL_3x3 = Point2d.of(45.5f, 119.5f);
            MID_WALL_3x3 = Point2d.of(42.5f, 118.5f);
            MID_WALL_2x2 = Point2d.of(43f, 118f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(30.5f, 138.5f));

            BUNKER_NATURAL = Point2d.of(56.5f, 108.5f);
            FACTORIES.add(Point2d.of(38.5f, 114.5f));
            FACTORIES.add(Point2d.of(40.5f, 121.5f));

            STARPORTS.add(Point2d.of(28.5f, 131.5f));
            STARPORTS.add(Point2d.of(30.5f, 135.5f));
            STARPORTS.add(Point2d.of(31.5f, 127.5f));
            STARPORTS.add(Point2d.of(33.5f, 124.5f));
            STARPORTS.add(Point2d.of(32.5f, 121.5f));
            STARPORTS.add(Point2d.of(31.5f, 118.5f));
            STARPORTS.add(Point2d.of(32.5f, 115.5f));
            STARPORTS.add(Point2d.of(34.5f, 142.5f));
            STARPORTS.add(Point2d.of(37.5f, 144.5f));
            STARPORTS.add(Point2d.of(42.5f, 145.5f));
            STARPORTS.add(Point2d.of(47.5f, 145.5f));
            STARPORTS.add(Point2d.of(40.5f, 130.5f));
            STARPORTS.add(Point2d.of(40.5f, 127.5f));
            STARPORTS.add(Point2d.of(40.5f, 124.5f));
            //STARPORTS.add(Point2d.of(40.5f, 121.5f));

            TURRETS.add(Point2d.of(46.0f, 140.0f));
            TURRETS.add(Point2d.of(37.0f, 135.0f));
            TURRETS.add(Point2d.of(43.0f, 140.0f));

            MACRO_OCS.add(Point2d.of(46.5f, 135.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 130.5f));
            MACRO_OCS.add(Point2d.of(47.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(53.5f, 130.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 135.5f));
            MACRO_OCS.add(Point2d.of(52.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(57.5f, 141.5f));
            MACRO_OCS.add(Point2d.of(57.5f, 136.5f));
            MACRO_OCS.add(Point2d.of(62.5f, 139.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(36.0f, 119.0f));
            extraDepots.add(Point2d.of(31.0f, 133.0f));
            extraDepots.add(Point2d.of(34.0f, 113.0f));
            extraDepots.add(Point2d.of(32.0f, 141.0f));
            extraDepots.add(Point2d.of(33.0f, 130.0f));
            extraDepots.add(Point2d.of(36.0f, 113.0f));
            extraDepots.add(Point2d.of(33.0f, 138.0f));
            extraDepots.add(Point2d.of(35.0f, 130.0f));
            extraDepots.add(Point2d.of(36.0f, 126.0f));
            extraDepots.add(Point2d.of(36.0f, 128.0f));
            extraDepots.add(Point2d.of(36.0f, 117.0f));
            extraDepots.add(Point2d.of(40.0f, 146.0f));
            extraDepots.add(Point2d.of(43.0f, 148.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(90.5f, 84.5f);
            proxyBunkerPos = Point2d.of(55.5f, 106.5f);
            proxyBunkerPos2 = Point2d.of(70.5f, 120.5f);

            reaperBlockDepots.add(Point2d.of(145.0f, 48.0f));
            reaperBlockDepots.add(Point2d.of(142.0f, 43.0f));
            reaperBlock3x3s.add(Point2d.of(147.5f, 49.5f));
            reaperBlock3x3s.add(Point2d.of(143.5f, 45.5f));

            myMineralPos = Point2d.of(166f, 35.5f);
            enemyMineralPos = Point2d.of(34f, 136.5f);

            WALL_2x2 = Point2d.of(157.0f, 56.0f);
            MID_WALL_3x3 = Point2d.of(157.5f, 53.5f);
            MID_WALL_2x2 = Point2d.of(157f, 54f);
            WALL_3x3 = Point2d.of(154.5f, 52.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(164.5f, 52.5f));

            BUNKER_NATURAL = Point2d.of(143.5f, 63.5f);
            FACTORIES.add(Point2d.of(161.5f, 57.5f));
            FACTORIES.add(Point2d.of(167.5f, 55.5f));

            STARPORTS.add(Point2d.of(168.5f, 38.5f));
            STARPORTS.add(Point2d.of(168.5f, 42.5f));
            STARPORTS.add(Point2d.of(167.5f, 33.5f));
            STARPORTS.add(Point2d.of(167.5f, 51.5f));
            STARPORTS.add(Point2d.of(164.5f, 48.5f));
            STARPORTS.add(Point2d.of(166.5f, 45.5f));
            STARPORTS.add(Point2d.of(161.5f, 28.5f));
            STARPORTS.add(Point2d.of(158.5f, 26.5f));
            STARPORTS.add(Point2d.of(153.5f, 24.5f));
            STARPORTS.add(Point2d.of(151.5f, 27.5f));
            STARPORTS.add(Point2d.of(157.5f, 41.5f));
            STARPORTS.add(Point2d.of(157.5f, 44.5f));
            STARPORTS.add(Point2d.of(157.5f, 47.5f));
            STARPORTS.add(Point2d.of(157.5f, 50.5f));

            TURRETS.add(Point2d.of(154.0f, 32.0f));
            TURRETS.add(Point2d.of(163.0f, 37.0f));
            TURRETS.add(Point2d.of(157.0f, 32.0f));

            MACRO_OCS.add(Point2d.of(153.5f, 36.5f));
            MACRO_OCS.add(Point2d.of(152.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(152.5f, 46.5f));
            MACRO_OCS.add(Point2d.of(147.5f, 36.5f));
            MACRO_OCS.add(Point2d.of(146.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(142.5f, 35.5f));
            MACRO_OCS.add(Point2d.of(142.5f, 30.5f));
            MACRO_OCS.add(Point2d.of(147.5f, 30.5f));
            MACRO_OCS.add(Point2d.of(137.5f, 32.5f));


            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(166.0f, 43.0f));
            extraDepots.add(Point2d.of(164.0f, 46.0f));
            extraDepots.add(Point2d.of(164.0f, 44.0f));
            extraDepots.add(Point2d.of(164.0f, 42.0f));
            extraDepots.add(Point2d.of(171.0f, 40.0f));
            extraDepots.add(Point2d.of(170.0f, 53.0f));
            extraDepots.add(Point2d.of(164.0f, 59.0f));
            extraDepots.add(Point2d.of(167.0f, 58.0f));
            extraDepots.add(Point2d.of(170.0f, 35.0f));
            extraDepots.add(Point2d.of(168.0f, 31.0f));
            extraDepots.add(Point2d.of(166.0f, 31.0f));
            extraDepots.add(Point2d.of(168.0f, 36.0f));
            extraDepots.add(Point2d.of(163.0f, 26.0f));
        }
    }

    private static void setLocationsForSimulacrum(boolean isTopPos) {
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(129.5f, 87.5f);
            proxyBunkerPos = Point2d.of(158.5f, 77.5f);

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
            FACTORIES.add(Point2d.of(54.5f, 132.5f));

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
            proxyBarracksPos = Point2d.of(84.5f, 96.5f);
            proxyBunkerPos = Point2d.of(57.5f, 106.5f);

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
            FACTORIES.add(Point2d.of(151.5f, 52.5f));

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
        muleLetterPosList.add(Point2d.of(51.5f, 55.5f));
        muleLetterPosList.add(Point2d.of(63.5f, 55.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(105.5f, 78.5f);
            proxyBunkerPos = Point2d.of(124.5f, 61.5f);
            proxyBunkerPos2 = Point2d.of(106.5f, 45.5f);

            WALL_2x2 = Point2d.of(40.0f, 117.0f);
            MID_WALL_2x2 = Point2d.of(42.0f, 117.0f);
            MID_WALL_3x3 = Point2d.of(42.5f, 117.5f);
            WALL_3x3 = Point2d.of(43.5f, 114.5f);

            reaperBlockDepots.add(WALL_2x2);
            reaperBlockDepots.add(MID_WALL_2x2);
            reaperBlockDepots.add(Point2d.of(33.0f, 90.0f));
            reaperBlock3x3s.add(Point2d.of(48.5f, 124.5f));
            reaperBlock3x3s.add(Point2d.of(46.5f, 121.5f));
            reaperBlock3x3s.add(Point2d.of(44.5f, 118.5f));

            myMineralPos = Point2d.of(25.0f, 128.5f);
            enemyMineralPos = Point2d.of(143.0f,35.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(22.5f, 127.5f));
            _3x3Structures.add(Point2d.of(26.5f, 134.5f));

            BUNKER_NATURAL = Point2d.of(44.5f, 104.5f);
            FACTORIES.add(Point2d.of(41.5f, 121.5f));
            FACTORIES.add(Point2d.of(32.5f, 118.5f)); //TODO: test if it gets stuck

            STARPORTS.add(Point2d.of(20.5f, 122.5f));
            STARPORTS.add(Point2d.of(23.5f, 118.5f));
            STARPORTS.add(Point2d.of(26.5f, 120.5f));
            STARPORTS.add(Point2d.of(20.5f, 131.5f));
            STARPORTS.add(Point2d.of(20.5f, 134.5f));
            STARPORTS.add(Point2d.of(23.5f, 137.5f));
            STARPORTS.add(Point2d.of(29.5f, 137.5f));
            STARPORTS.add(Point2d.of(35.5f, 137.5f));
            STARPORTS.add(Point2d.of(40.5f, 136.5f));
            STARPORTS.add(Point2d.of(43.5f, 138.5f));
            STARPORTS.add(Point2d.of(47.5f, 132.5f));
            //STARPORTS.add(Point2d.of(32.5f, 118.5f)); FACTORY2
            STARPORTS.add(Point2d.of(22.5f, 111.5f));
            STARPORTS.add(Point2d.of(29.5f, 114.5f));
            STARPORTS.add(Point2d.of(21.5f, 107.5f));
            //STARPORTS.add(Point2d.of(44.5f, 118.5f)); //to add after 2nd bunker is salvaged


            TURRETS.add(Point2d.of(37.0f, 132.0f));
            TURRETS.add(Point2d.of(28.0f, 127.0f));
            TURRETS.add(Point2d.of(34.0f, 132.0f));

            MACRO_OCS.add(Point2d.of(32.5f, 122.5f));
            MACRO_OCS.add(Point2d.of(37.5f, 127.5f));
            MACRO_OCS.add(Point2d.of(43.5f, 125.5f));
            MACRO_OCS.add(Point2d.of(43.5f, 131.5f));
            MACRO_OCS.add(Point2d.of(48.5f, 128.5f));


            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(25.0f, 116.0f));
            extraDepots.add(Point2d.of(21.0f, 120.0f));
            extraDepots.add(Point2d.of(28.0f, 118.0f));
            extraDepots.add(Point2d.of(22.0f, 125.0f));
            extraDepots.add(Point2d.of(30.0f, 118.0f));
            extraDepots.add(Point2d.of(28.0f, 123.0f));
            extraDepots.add(Point2d.of(25.0f, 140.0f));
            extraDepots.add(Point2d.of(35.0f, 140.0f));
            extraDepots.add(Point2d.of(30.0f, 135.0f));
            extraDepots.add(Point2d.of(33.0f, 140.0f));
            extraDepots.add(Point2d.of(45.0f, 136.0f));
            extraDepots.add(Point2d.of(34.0f, 135.0f));
            extraDepots.add(Point2d.of(36.0f, 124.0f));
            extraDepots.add(Point2d.of(36.0f, 122.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(62.5f, 85.5f);
            proxyBunkerPos = Point2d.of(43.5f, 102.5f);
            proxyBunkerPos2 = Point2d.of(61.5f, 118.5f);

            WALL_2x2 = Point2d.of(128.0f, 47.0f);
            WALL_3x3 = Point2d.of(124.5f, 49.5f);
            MID_WALL_2x2 = Point2d.of(126.0f, 47.0f);
            MID_WALL_3x3 = Point2d.of(125.5f, 46.5f);

            reaperBlockDepots.add(WALL_2x2);
            reaperBlockDepots.add(MID_WALL_2x2);
            reaperBlockDepots.add(Point2d.of(135.0f, 74.0f));
            reaperBlock3x3s.add(Point2d.of(119.5f, 39.5f));
            reaperBlock3x3s.add(Point2d.of(121.5f, 42.5f));
            reaperBlock3x3s.add(Point2d.of(123.5f, 45.5f));

            myMineralPos = Point2d.of(143.0f,35.5f);
            enemyMineralPos = Point2d.of(25.0f, 128.5f);

            _3x3Structures.add(MID_WALL_3x3);
            _3x3Structures.add(WALL_3x3);
            _3x3Structures.add(Point2d.of(131.5f, 41.5f));
            _3x3Structures.add(Point2d.of(131.5f, 44.5f));

            BUNKER_NATURAL = Point2d.of(123.5f, 59.5f);
            FACTORIES.add(Point2d.of(124.5f, 42.5f));
            FACTORIES.add(Point2d.of(136.5f, 49.5f));

            STARPORTS.add(Point2d.of(140.5f, 42.5f));
            STARPORTS.add(Point2d.of(144.5f, 33.5f));
            STARPORTS.add(Point2d.of(145.5f, 30.5f));
            STARPORTS.add(Point2d.of(142.5f, 28.5f));
            STARPORTS.add(Point2d.of(137.5f, 26.5f));
            STARPORTS.add(Point2d.of(131.5f, 26.5f));
            STARPORTS.add(Point2d.of(122.5f, 26.5f));
            STARPORTS.add(Point2d.of(125.5f, 28.5f));
            STARPORTS.add(Point2d.of(145.5f, 41.5f));
            STARPORTS.add(Point2d.of(143.5f, 44.5f));
            STARPORTS.add(Point2d.of(135.5f, 45.5f));
            //STARPORTS.add(Point2d.of(136.5f, 49.5f)); //FACTORY2
            STARPORTS.add(Point2d.of(143.5f, 52.5f));
            STARPORTS.add(Point2d.of(144.5f, 56.5f));
            //STARPORTS.add(Point2d.of(123.5f, 45.5f)); //FOR BUNKER SWAP

            TURRETS.add(Point2d.of(131.0f, 32.0f));
            TURRETS.add(Point2d.of(140.0f, 37.0f));
            TURRETS.add(Point2d.of(134.0f, 32.0f));

            MACRO_OCS.add(Point2d.of(135.5f, 41.5f));
            MACRO_OCS.add(Point2d.of(130.5f, 36.5f));
            MACRO_OCS.add(Point2d.of(124.5f, 38.5f));
            MACRO_OCS.add(Point2d.of(119.5f, 35.5f));
            MACRO_OCS.add(Point2d.of(124.5f, 32.5f));

            extraDepots.add(WALL_2x2);
            extraDepots.add(MID_WALL_2x2);
            extraDepots.add(Point2d.of(147.0f, 28.0f));
            extraDepots.add(Point2d.of(146.0f, 39.0f));
            extraDepots.add(Point2d.of(146.0f, 37.0f));
            extraDepots.add(Point2d.of(144.0f, 37.0f));
            extraDepots.add(Point2d.of(148.0f, 43.0f));
            extraDepots.add(Point2d.of(145.0f, 26.0f));
            extraDepots.add(Point2d.of(144.0f, 47.0f));
            extraDepots.add(Point2d.of(143.0f, 24.0f));
            extraDepots.add(Point2d.of(142.0f, 47.0f));
            extraDepots.add(Point2d.of(143.0f, 26.0f));
            extraDepots.add(Point2d.of(141.0f, 45.0f));
            extraDepots.add(Point2d.of(140.0f, 28.0f));
            extraDepots.add(Point2d.of(140.0f, 40.0f));
            extraDepots.add(Point2d.of(138.0f, 29.0f));
        }

    }

    private static void setLocationsForThunderBird(boolean isTopPos) {
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(129.5f, 82.5f);
            proxyBunkerPos = Point2d.of(141.5f, 51.5f);

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
            FACTORIES.add(Point2d.of(28.5f, 125.5f));

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


            extraDepots.add(Point2d.of(54.0f, 123.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
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
            proxyBarracksPos = Point2d.of(61.5f, 74.5f);
            proxyBunkerPos = Point2d.of(50.5f, 104.5f);

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
            FACTORIES.add(Point2d.of(160.5f, 30.5f));

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

            extraDepots.add(Point2d.of(139.0f, 34.0f)); //reaperJump1
            extraDepots.add(WALL_2x2); //wall2x2
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
        } else {
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
        } else {
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
        } else {
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
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(71.5f, 87.5f);
            proxyBunkerPos = Point2d.of(66.5f, 50.5f);

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

            BUNKER_NATURAL = Point2d.of(129.5f, 112.5f);
            //REAPER_JUMP1 = Point2d.of(144.0f, 121.0f);
            REAPER_JUMP2 = Point2d.of(126.5f, 144.5f);
            //REAPER_JUMP3 = Point2d.of(126.0f, 147.0f);
            FACTORIES.add(Point2d.of(144.5f, 134.5f));

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
            extraDepots.add(Point2d.of(133.0f, 148.0f)); //turret
        }
        else {
            proxyBarracksPos = Point2d.of(119.5f, 85.5f);
            proxyBunkerPos = Point2d.of(125.5f, 121.5f);

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
            BUNKER_NATURAL = Point2d.of(61.5f, 58.5f);
            FACTORIES.add(Point2d.of(49.5f, 36.5f));

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


    public static void setBaseLocations() {
        switch (MAP) {
            case MapNames._2000_ATMOSPHERES_AIE:
                baseLocations.add(Point2d.of(166.5f, 143.5f));
                baseLocations.add(Point2d.of(144.5f, 152.5f));
                baseLocations.add(Point2d.of(146.5f, 123.5f));
                baseLocations.add(Point2d.of(169.5f, 98.5f));
                baseLocations.add(Point2d.of(111.5f, 155.5f));
                baseLocations.add(Point2d.of(84.5f, 156.5f));
                baseLocations.add(Point2d.of(53.5f, 154.5f));
                baseLocations.add(Point2d.of(66.5f, 131.5f));
                baseLocations.add(Point2d.of(157.5f, 72.5f));
                baseLocations.add(Point2d.of(170.5f, 49.5f));
                baseLocations.add(Point2d.of(139.5f, 47.5f));
                baseLocations.add(Point2d.of(112.5f, 48.5f));
                baseLocations.add(Point2d.of(54.5f, 105.5f));
                baseLocations.add(Point2d.of(77.5f, 80.5f));
                baseLocations.add(Point2d.of(79.5f, 51.5f));
                baseLocations.add(Point2d.of(57.5f, 60.5f));
                break;

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

            case MapNames.ASCENSION_TO_AIUR:
                baseLocations.add(Point2d.of(26.5f, 128.5f));
                baseLocations.add(Point2d.of(21.5f, 106.5f));
                baseLocations.add(Point2d.of(54.5f, 111.5f));
                baseLocations.add(Point2d.of(23.5f, 73.5f));
                baseLocations.add(Point2d.of(47.5f, 77.5f));
                baseLocations.add(Point2d.of(27.5f, 41.5f));
                baseLocations.add(Point2d.of(85.5f, 111.5f));
                baseLocations.add(Point2d.of(107.5f, 129.5f));
                baseLocations.add(Point2d.of(68.5f, 22.5f));
                baseLocations.add(Point2d.of(90.5f, 40.5f));
                baseLocations.add(Point2d.of(148.5f, 110.5f));
                baseLocations.add(Point2d.of(128.5f, 74.5f));
                baseLocations.add(Point2d.of(152.5f, 78.5f));
                baseLocations.add(Point2d.of(121.5f, 40.5f));
                baseLocations.add(Point2d.of(154.5f, 45.5f));
                baseLocations.add(Point2d.of(149.5f, 23.5f));
                break;

            case MapNames.BLACKBURN_AIE:
                baseLocations.add(Point2d.of(36.5f, 31.5f));
                baseLocations.add(Point2d.of(36.5f, 54.5f));
                baseLocations.add(Point2d.of(67.5f, 54.5f));
                baseLocations.add(Point2d.of(39.5f, 80.5f));
                baseLocations.add(Point2d.of(36.5f, 115.5f));
                baseLocations.add(Point2d.of(57.5f, 99.5f));
                baseLocations.add(Point2d.of(91.5f, 121.5f));
                //baseLocations.add(Point2d.of(92.5f, 32.5f)); TODO: handle island base
                baseLocations.add(Point2d.of(126.5f, 99.5f));
                baseLocations.add(Point2d.of(147.5f, 115.5f));
                baseLocations.add(Point2d.of(144.5f, 80.5f));
                baseLocations.add(Point2d.of(116.5f, 54.5f));
                baseLocations.add(Point2d.of(147.5f, 54.5f));
                baseLocations.add(Point2d.of(147.5f, 31.5f));
                break;

            case MapNames.CATALYST:
                baseLocations.add(Point2d.of(39.5f, 139.5f));
                baseLocations.add(Point2d.of(68.5f, 140.5f));
                baseLocations.add(Point2d.of(48.5f, 106.5f));
                baseLocations.add(Point2d.of(100.5f, 139.5f));
                baseLocations.add(Point2d.of(98.5f, 113.5f));
                baseLocations.add(Point2d.of(140.5f, 143.5f));
                baseLocations.add(Point2d.of(53.5f, 66.5f));
                baseLocations.add(Point2d.of(130.5f, 101.5f));
                baseLocations.add(Point2d.of(43.5f, 24.5f));
                baseLocations.add(Point2d.of(85.5f, 54.5f));
                baseLocations.add(Point2d.of(83.5f, 28.5f));
                baseLocations.add(Point2d.of(135.5f, 61.5f));
                baseLocations.add(Point2d.of(115.5f, 27.5f));
                baseLocations.add(Point2d.of(144.5f, 28.5f));
                break;

            case MapNames.DEATH_AURA:
            case MapNames.DEATH_AURA505:
            case MapNames.DEATH_AURA506:
                baseLocations.add(Point2d.of(37.5f, 139.5f));
                baseLocations.add(Point2d.of(57.5f, 148.5f));
                baseLocations.add(Point2d.of(54.5f, 118.5f));
                baseLocations.add(Point2d.of(38.5f, 85.5f));
                baseLocations.add(Point2d.of(84.5f, 149.5f));
                baseLocations.add(Point2d.of(116.5f, 150.5f));
                baseLocations.add(Point2d.of(126.5f, 120.5f));
                baseLocations.add(Point2d.of(65.5f, 67.5f));
                baseLocations.add(Point2d.of(75.5f, 37.5f));
                baseLocations.add(Point2d.of(107.5f, 38.5f));
                baseLocations.add(Point2d.of(153.5f, 102.5f));
                baseLocations.add(Point2d.of(137.5f, 69.5f));
                baseLocations.add(Point2d.of(134.5f, 39.5f));
                baseLocations.add(Point2d.of(154.5f, 48.5f));
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
            case MapNames.ETERNAL_EMPIRE505:
            case MapNames.ETERNAL_EMPIRE506:
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
            case MapNames.EVER_DREAM505:
            case MapNames.EVER_DREAM506:
                baseLocations.add(Point2d.of(139.5f, 163.5f));
                baseLocations.add(Point2d.of(154.5f, 147.5f));
                baseLocations.add(Point2d.of(118.5f, 145.5f));
                baseLocations.add(Point2d.of(94.5f, 161.5f));
                baseLocations.add(Point2d.of(153.5f, 113.5f));
                baseLocations.add(Point2d.of(156.5f, 84.5f));
                baseLocations.add(Point2d.of(131.5f, 114.5f));
                baseLocations.add(Point2d.of(154.5f, 51.5f));
                baseLocations.add(Point2d.of(45.5f, 160.5f));
                baseLocations.add(Point2d.of(68.5f, 97.5f));
                baseLocations.add(Point2d.of(43.5f, 127.5f));
                baseLocations.add(Point2d.of(46.5f, 98.5f));
                baseLocations.add(Point2d.of(105.5f, 50.5f));
                baseLocations.add(Point2d.of(81.5f, 66.5f));
                baseLocations.add(Point2d.of(45.5f, 64.5f));
                baseLocations.add(Point2d.of(60.5f, 48.5f));
                break;

            case MapNames.GOLDEN_WALL:
            case MapNames.GOLDEN_WALL505:
            case MapNames.GOLDEN_WALL506:
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

            case MapNames.ICE_AND_CHROME:
            case MapNames.ICE_AND_CHROME505:
            case MapNames.ICE_AND_CHROME506:
                baseLocations.add(Point2d.of(182.5f, 172.5f));
                baseLocations.add(Point2d.of(186.5f, 145.5f));
                baseLocations.add(Point2d.of(157.5f, 152.5f));
                baseLocations.add(Point2d.of(144.5f, 174.5f));
                baseLocations.add(Point2d.of(182.5f, 116.5f));
                baseLocations.add(Point2d.of(185.5f, 88.5f));
                baseLocations.add(Point2d.of(110.5f, 175.5f));
                baseLocations.add(Point2d.of(124.5f, 152.5f));
                baseLocations.add(Point2d.of(131.5f, 83.5f));
                baseLocations.add(Point2d.of(145.5f, 60.5f));
                baseLocations.add(Point2d.of(70.5f, 147.5f));
                baseLocations.add(Point2d.of(73.5f, 119.5f));
                baseLocations.add(Point2d.of(111.5f, 61.5f));
                baseLocations.add(Point2d.of(98.5f, 83.5f));
                baseLocations.add(Point2d.of(69.5f, 90.5f));
                baseLocations.add(Point2d.of(73.5f, 63.5f));
                break;

            case MapNames.JAGANNATHA:
            case MapNames.JAGANNATHA_AIE:
                baseLocations.add(Point2d.of(126.5f, 151.5f));
                baseLocations.add(Point2d.of(98.5f, 151.5f));
                baseLocations.add(Point2d.of(104.5f, 125.5f));
                baseLocations.add(Point2d.of(129.5f, 109.5f));
                baseLocations.add(Point2d.of(66.5f, 152.5f));
                baseLocations.add(Point2d.of(37.5f, 152.5f));
                baseLocations.add(Point2d.of(37.5f, 119.5f));
                baseLocations.add(Point2d.of(130.5f, 66.5f));
                baseLocations.add(Point2d.of(130.5f, 33.5f));
                baseLocations.add(Point2d.of(101.5f, 33.5f));
                baseLocations.add(Point2d.of(38.5f, 76.5f));
                baseLocations.add(Point2d.of(63.5f, 60.5f));
                baseLocations.add(Point2d.of(69.5f, 34.5f));
                baseLocations.add(Point2d.of(41.5f, 34.5f));
                break;

            case MapNames.LIGHTSHADE:
            case MapNames.LIGHTSHADE_AIE:
                baseLocations.add(Point2d.of(40.5f, 131.5f));
                baseLocations.add(Point2d.of(38.5f, 102.5f));
                baseLocations.add(Point2d.of(64.5f, 110.5f));
                baseLocations.add(Point2d.of(79.5f, 136.5f));
                baseLocations.add(Point2d.of(36.5f, 69.5f));
                baseLocations.add(Point2d.of(35.5f, 38.5f));
                baseLocations.add(Point2d.of(59.5f, 43.5f));
                baseLocations.add(Point2d.of(124.5f, 120.5f));
                baseLocations.add(Point2d.of(148.5f, 125.5f));
                baseLocations.add(Point2d.of(147.5f, 94.5f));
                baseLocations.add(Point2d.of(104.5f, 27.5f));
                baseLocations.add(Point2d.of(119.5f, 53.5f));
                baseLocations.add(Point2d.of(145.5f, 61.5f));
                baseLocations.add(Point2d.of(143.5f, 32.5f));
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

            case MapNames.OXIDE:
            case MapNames.OXIDE_AIE:
                baseLocations.add(Point2d.of(147.5f, 147.5f));
                baseLocations.add(Point2d.of(122.5f, 151.5f));
                baseLocations.add(Point2d.of(131.5f, 123.5f));
                baseLocations.add(Point2d.of(150.5f, 102.5f));
                baseLocations.add(Point2d.of(89.5f, 150.5f));
                baseLocations.add(Point2d.of(59.5f, 151.5f));
                baseLocations.add(Point2d.of(132.5f, 52.5f));
                baseLocations.add(Point2d.of(102.5f, 53.5f));
                baseLocations.add(Point2d.of(41.5f, 101.5f));
                baseLocations.add(Point2d.of(60.5f, 80.5f));
                baseLocations.add(Point2d.of(69.5f, 52.5f));
                baseLocations.add(Point2d.of(44.5f, 56.5f));
                break;

            case MapNames.PILLARS_OF_GOLD:
            case MapNames.PILLARS_OF_GOLD505:
            case MapNames.PILLARS_OF_GOLD506:
                baseLocations.add(Point2d.of(137.5f, 133.5f));
                baseLocations.add(Point2d.of(110.5f, 142.5f));
                baseLocations.add(Point2d.of(116.5f, 112.5f));
                baseLocations.add(Point2d.of(136.5f, 95.5f));
                baseLocations.add(Point2d.of(81.5f, 141.5f));
                baseLocations.add(Point2d.of(36.5f, 139.5f));
                baseLocations.add(Point2d.of(51.5f, 122.5f));
                baseLocations.add(Point2d.of(116.5f, 49.5f));
                baseLocations.add(Point2d.of(131.5f, 32.5f));
                baseLocations.add(Point2d.of(86.5f, 30.5f));
                baseLocations.add(Point2d.of(31.5f, 76.5f));
                baseLocations.add(Point2d.of(51.5f, 59.5f));
                baseLocations.add(Point2d.of(57.5f, 29.5f));
                baseLocations.add(Point2d.of(30.5f, 38.5f));
                break;

            case MapNames.ROMANTICIDE:
            case MapNames.ROMANTICIDE_AIE:
                baseLocations.add(Point2d.of(41.5f, 135.5f));
                baseLocations.add(Point2d.of(46.5f, 103.5f));
                baseLocations.add(Point2d.of(68.5f, 126.5f));
                baseLocations.add(Point2d.of(99.5f, 138.5f));
                baseLocations.add(Point2d.of(100.5f, 114.5f));
                baseLocations.add(Point2d.of(61.5f, 86.5f));
                baseLocations.add(Point2d.of(38.5f, 65.5f));
                baseLocations.add(Point2d.of(151.5f, 137.5f));
                baseLocations.add(Point2d.of(48.5f, 34.5f));
                baseLocations.add(Point2d.of(161.5f, 106.5f));
                baseLocations.add(Point2d.of(138.5f, 85.5f));
                baseLocations.add(Point2d.of(99.5f, 57.5f));
                baseLocations.add(Point2d.of(100.5f, 33.5f));
                baseLocations.add(Point2d.of(131.5f, 45.5f));
                baseLocations.add(Point2d.of(153.5f, 68.5f));
                baseLocations.add(Point2d.of(158.5f, 36.5f));
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

            case MapNames.SUBMARINE:
            case MapNames.SUBMARINE505:
            case MapNames.SUBMARINE506:
                baseLocations.add(Point2d.of(32.5f, 127.5f));
                baseLocations.add(Point2d.of(33.5f, 104.5f));
                baseLocations.add(Point2d.of(62.5f, 122.5f));
                baseLocations.add(Point2d.of(97.5f, 129.5f));
                baseLocations.add(Point2d.of(36.5f, 79.5f));
                baseLocations.add(Point2d.of(33.5f, 49.5f));
                baseLocations.add(Point2d.of(134.5f, 114.5f));
                baseLocations.add(Point2d.of(131.5f, 84.5f));
                baseLocations.add(Point2d.of(70.5f, 34.5f));
                baseLocations.add(Point2d.of(105.5f, 41.5f));
                baseLocations.add(Point2d.of(134.5f, 59.5f));
                baseLocations.add(Point2d.of(135.5f, 36.5f));
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
                baseLocations.add(Point2d.of(149.5f, 63.5f));
                baseLocations.add(Point2d.of(111.5f, 146.5f));
                baseLocations.add(Point2d.of(104.5f, 121.5f));
                baseLocations.add(Point2d.of(150.5f, 27.5f));
                baseLocations.add(Point2d.of(71.5f, 134.5f));
                baseLocations.add(Point2d.of(120.5f, 37.5f));
                baseLocations.add(Point2d.of(41.5f, 144.5f));
                baseLocations.add(Point2d.of(42.5f, 108.5f));
                baseLocations.add(Point2d.of(80.5f, 25.5f));
                baseLocations.add(Point2d.of(87.5f, 50.5f));
                baseLocations.add(Point2d.of(42.5f, 76.5f));
                baseLocations.add(Point2d.of(57.5f, 54.5f));
                baseLocations.add(Point2d.of(49.5f, 32.5f));
                break;
        }

        //reverse list for bottom spawn
        if (!isTopSpawn) {
            Collections.reverse(baseLocations);
        }
//        if (MAP.equals(MapNames.GOLDEN_WALL)) { //expand to gold base first
//            baseLocations.add(1, baseLocations.remove(2));
//        }
    }

    public static void setClockBaseLists() {
        Map<Double, Point2d> basesByAngle = new TreeMap<>();
        float midX = (MAX_X - MIN_X)/2f + MIN_X;
        float midY = (MAX_Y - MIN_Y)/2f + MIN_Y;
        Point2d homeBasePos = baseLocations.get(0);
        double homeBaseAngle = Math.toDegrees(Math.atan2(homeBasePos.getX()-midX, homeBasePos.getY()-midY));
        Point2d enemyBasePos = baseLocations.get(baseLocations.size() - 1);
        double enemyBaseAngle = Math.toDegrees(Math.atan2(enemyBasePos.getX()-midX, enemyBasePos.getY()-midY)) - homeBaseAngle;

        for (Point2d basePos : baseLocations) {
            double angle = Math.toDegrees(Math.atan2(basePos.getX()-midX, basePos.getY()-midY)) - homeBaseAngle;
            if (enemyBaseAngle < 0 && angle < enemyBaseAngle) angle += 360;
            if (enemyBaseAngle > 0 && angle > enemyBaseAngle) angle -= 360;
            basesByAngle.put(angle, basePos);
        }

        basesByAngle.forEach((angle, basePos) -> { //TODO: do the full loop, not just to enemy main
            if (angle <= 0) {
                counterClockBasePositions.add(0, basePos);
            }
            if (angle >= 0) {
                clockBasePositions.add(basePos);
            }
        });
        if (enemyBaseAngle < 0) {
            clockBasePositions.add(enemyBasePos);
        }
        else {
            counterClockBasePositions.add(enemyBasePos);
        }
    }

    public static Point2d getMainBaseMidPoint(boolean isEnemyMain) {
        boolean[][] pointInBase = (isEnemyMain) ? InfluenceMaps.pointInEnemyMainBase : InfluenceMaps.pointInMainBase;
        int xMin = 0; //(int) SCREEN_BOTTOM_LEFT.getX();
        int xMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getX());
        int yMin = 0; //(int) SCREEN_BOTTOM_LEFT.getY();
        int yMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getY());
        int xBaseLeft = Integer.MAX_VALUE;
        int xBaseRight = 0;
        int yBaseTop = 0;
        int yBaseBottom = Integer.MAX_VALUE;

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                if (pointInBase[x][y]) {
                    xBaseLeft = Math.min(xBaseLeft, x);
                    xBaseRight = Math.max(xBaseRight, x);
                    yBaseTop = Math.max(yBaseTop, y);
                    yBaseBottom = Math.min(yBaseBottom, y);
                }
            }
        }
        float avgX = (xBaseLeft + xBaseRight) / 2f;
        float avgY = (yBaseTop + yBaseBottom) / 2f;
        return Point2d.of(avgX/2f, avgY/2f);
    }

    public static Point2d getFactoryPos() {
        //normal spot
        if (!FACTORIES.isEmpty()) {
            return FACTORIES.remove(0);
        }

        //any starport spot if starport already in the factory position
        Strategy.DO_DEFENSIVE_TANKS = false; //unreliable position for tank pathing so don't make tanks
        Strategy.DO_OFFENSIVE_TANKS = false; //unreliable position for tank pathing so don't make tanks
        Strategy.DO_USE_CYCLONES = false; //unreliable position for cyclone pathing so don't make cyclones
        return STARPORTS.remove(0);
    }
}
