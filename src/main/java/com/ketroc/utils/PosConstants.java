package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.*;

import java.util.*;
import java.util.stream.Collectors;

public class PosConstants {
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
    public static Point2d myRampPos;
    public static Point2d enemyRampPos;


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
    public static List<Point2d> _3x3AddonPosList = new ArrayList<>();
    public static List<Point2d> TURRETS = new ArrayList<>();
    public static List<Point2d> MACRO_OCS = new ArrayList<>();
    public static Point2d proxyBarracksPos;
    public static Point2d proxyBunkerPos;
    public static Point2d proxyBunkerPos2;

    public static Base nextEnemyBase;
    public static List<Point2d> baseLocations = new ArrayList<>();
    public static List<Point2d> clockBasePositions = new ArrayList<>();
    public static List<Point2d> counterClockBasePositions = new ArrayList<>();

    public static Race opponentRace;

    public static void onGameStart() {
        if (MAP.contains("Golden Wall") || MAP.contains("Blackburn") || MAP.contains("Stargazers")) { //isTopSpawn == the left spawn for this map
            isTopSpawn = isMySpawnLeft();
        }
        else {
            isTopSpawn = isMySpawnTop();
        }
        setStructureLocations();
        setBaseLocations();
        setClockBaseLists();
        createBaseList();
        setEnemyTypes();
        mapMainAndNatBases();
        mainBaseMidPos = getMainBaseMidPoint(false);
        enemyMainBaseMidPos = getMainBaseMidPoint(true);

        //set probe rush mineral node
        enemyMineralTriangle = new TriangleOfNodes(enemyMineralPos);
        myMineralTriangle = new TriangleOfNodes(myMineralPos);
    }

    public static void onStep() {
        nextEnemyBase = UnitUtils.getNextEnemyBase();
        if (nextEnemyBase == null) {
            Chat.chatNeverRepeat("Finish Him!");
            MannerMule.doTrollMule = true;
        }
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
        float minProductionDistanceFromEnemy = (float)PosConstants.baseLocations.get(2).distance(enemyNatPos) - 10;

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                Point2d thisPos = Point2d.of(x/2f, y/2f);
                float thisZ = Bot.OBS.terrainHeight(thisPos);
                if (thisPos.distance(homePos) < 30 && Math.abs(thisZ - homeZ) < 1.2f) {
                    if (isPathable(thisPos)) {
                        InfluenceMaps.pointInMainBase[x][y] = true;
                        if (Math.abs(thisZ - rampZ) < 0.2f && thisPos.distance(natPos) < 15) {
                            myRampPos = Point2d.of(x / 2, y / 2);
                        }
                        continue;
                    }
                } else if (thisPos.distance(natPos) < 50 &&
                        thisPos.distance(enemyNatPos) > minProductionDistanceFromEnemy &&
                        isPathable(thisPos)){
                    InfluenceMaps.pointIn2ndProductionArea[x][y] = true;
                }
                if (thisPos.distance(natPos) < 13 && Math.abs(thisZ - natZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInNat[x][y] = true;
                    if (thisPos.distance(PosConstants.BUNKER_NATURAL) > 8) {
                        InfluenceMaps.pointInNatExcludingBunkerRange[x][y] = true;
                    }
                    continue;
                }
                if (thisPos.distance(enemyPos) < 30 && Math.abs(thisZ - enemyZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInEnemyMainBase[x][y] = true;
                    if (Math.abs(thisZ - rampZ) < 0.2f && thisPos.distance(enemyNatPos) < 15) {
                        enemyRampPos = Point2d.of(x/2, y/2);
                    }
                    continue;
                }
                if (thisPos.distance(enemyNatPos) < 16 && Math.abs(thisZ - enemyNatZ) < 1.2f && isPathable(thisPos)) {
                    InfluenceMaps.pointInEnemyNat[x][y] = true;
                    continue;
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

    private static void createBaseList() {
        UnitInPool mainCC = Bot.OBS.getUnits(Alliance.SELF, structure -> UnitUtils.COMMAND_STRUCTURE_TYPE.contains(structure.unit().getType())).get(0);
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
            case MapNames.BERLINGRAD_AIE:
                setLocationsForBerlingrad(isTopSpawn);
                break;
            case MapNames.CATALYST:
                setLocationsForCatalyst(isTopSpawn);
                break;
            case MapNames.CURIOUS_MINDS_AIE:
                setLocationsForCuriousMinds(isTopSpawn);
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
            case MapNames.FLAT48:
                setLocationsForFlat48(isTopSpawn);
                break;
            case MapNames.GLITTERING_ASHES_AIE:
                setLocationsForGlitteringAshes(isTopSpawn);
                break;
            case MapNames.GOLDEN_WALL:
            case MapNames.GOLDEN_WALL505:
            case MapNames.GOLDEN_WALL506:
                setLocationsForGoldenWall(isTopSpawn);
                break;
            case MapNames.HARDWIRE_AIE:
                setLocationsForHardwire(isTopSpawn);
                break;
            case MapNames.INSIDE_AND_OUT_AIE:
                setLocationsForInsideOrOut(isTopSpawn);
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
            case MapNames.MOONDANCE_AIE:
                setLocationsForMoondance(isTopSpawn);
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
            case MapNames.STARGAZERS_AIE:
                setLocationsForStargazers(isTopSpawn);
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
            case MapNames.WATERFALL_AIE:
                setLocationsForWaterfall(isTopSpawn);
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
        muleLetterPosList.add(Point2d.of(98.5f, 116.5f));
        muleLetterPosList.add(Point2d.of(105.5f, 116.5f));
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

            BUNKER_NATURAL = Point2d.of(136.5f, 144.5f);
        } else {
            proxyBarracksPos = Point2d.of(96.5f, 131.5f);
            proxyBunkerPos = Point2d.of(136.5f, 146.5f);
            proxyBunkerPos2 = Point2d.of(147.5f, 121.5f);

            reaperBlockDepots.add(Point2d.of(73.0f, 70.0f));
            reaperBlockDepots.add(Point2d.of(68.0f, 75.0f));
            reaperBlockDepots.add(Point2d.of(77.0f, 66.0f));
            reaperBlock3x3s.add(Point2d.of(74.5f, 67.5f));

            myMineralPos = Point2d.of(50f, 59.5f);
            enemyMineralPos = Point2d.of(174f, 144.5f);

            BUNKER_NATURAL = Point2d.of(86.5f, 58.5f);
        }
    }



    private static void setLocationsForAcropolis(boolean isTopPos) {
        if (isTopPos) {
            reaperBlockDepots.add(Point2d.of(52.0f, 132.0f));

            myMineralPos = Point2d.of(33.0f, 145.5f);
            enemyMineralPos = Point2d.of(143f, 26.5f);

            BUNKER_NATURAL = Point2d.of(36.5f, 105.5f);
        }
        else {
            reaperBlockDepots.add(Point2d.of(124.0f, 40.0f));

            myMineralPos = Point2d.of(143f, 26.5f);
            enemyMineralPos = Point2d.of(33.0f, 145.5f);

            BUNKER_NATURAL = Point2d.of(139.5f, 67.5f);
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

            BUNKER_NATURAL = Point2d.of(31.5f, 103.5f);
        } else {
            proxyBarracksPos = Point2d.of(63.5f, 73.5f);
            proxyBunkerPos = Point2d.of(30.5f, 101.5f);
            //proxyBunkerPos2 unnecessary

            //no reaper jumps

            myMineralPos = Point2d.of(155.5f, 22.5f);
            enemyMineralPos = Point2d.of(16.5f, 126.5f);

            BUNKER_NATURAL = Point2d.of(144.5f, 48.5f);
        }
    }

    private static void setLocationsForBlackburn(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(86f, 77f));
        muleLetterPosList.add(Point2d.of(94f, 77f));
        if (isTopPos) { //left spawn
            proxyBarracksPos = Point2d.of(118.5f, 81.5f);
            proxyBunkerPos = Point2d.of(141.5f, 60.5f);
            proxyBunkerPos2 = Point2d.of(111.5f, 38.5f);

            reaperBlockDepots.add(Point2d.of(57.0f, 30.0f));
            reaperBlockDepots.add(Point2d.of(61.0f, 26.0f));
            reaperBlock3x3s.add(Point2d.of(58.5f, 27.5f));

            myMineralPos = Point2d.of(29f, 30.5f);
            enemyMineralPos = Point2d.of(155f, 30.5f);

            BUNKER_NATURAL = Point2d.of(42.5f, 59.5f);
        } else { //right spawn
            proxyBarracksPos = Point2d.of(64.5f, 80.5f);
            proxyBunkerPos = Point2d.of(42.5f, 60.5f);
            proxyBunkerPos2 = Point2d.of(72.5f, 38.5f);

            reaperBlockDepots.add(Point2d.of(127.0f, 30.0f));
            reaperBlockDepots.add(Point2d.of(123.0f, 26.0f));
            reaperBlock3x3s.add(Point2d.of(125.5f, 27.5f));

            myMineralPos = Point2d.of(155f, 30.5f);
            enemyMineralPos = Point2d.of(29f, 30.5f);

            BUNKER_NATURAL = Point2d.of(141.5f, 59.5f);
        }
    }

    private static void setLocationsForBerlingrad(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(71.5f, 69.5f));
        muleLetterPosList.add(Point2d.of(79.5f, 69.5f));
        if (isTopPos) {
            myMineralPos = Point2d.of(25.5f, 132.5f);
            enemyMineralPos = Point2d.of(126.5f, 23.5f);

            proxyBarracksPos = Point2d.of(103.5f, 84.5f);
            proxyBunkerPos = Point2d.of(115.5f, 54.5f);
            proxyBunkerPos2 = Point2d.of(92.5f, 40.5f);

            reaperBlockDepots.add(Point2d.of(51.0f, 120.0f));
            reaperBlockDepots.add(Point2d.of(51.0f, 125.0f));
            reaperBlockDepots.add(Point2d.of(53.0f, 126.0f));
            reaperBlockDepots.add(Point2d.of(26.0f, 93.0f));
            reaperBlock3x3s.add(Point2d.of(49.5f, 122.5f));

            BUNKER_NATURAL = Point2d.of(36.5f, 101.5f);
        } else {
            myMineralPos = Point2d.of(126.5f, 23.5f);
            enemyMineralPos = Point2d.of(25.5f, 132.5f);

            proxyBarracksPos = Point2d.of(49.5f, 73.5f);
            proxyBunkerPos = Point2d.of(36.5f, 101.5f);
            proxyBunkerPos2 = Point2d.of(59.5f, 115.5f);

            reaperBlockDepots.add(Point2d.of(101.0f, 36.0f));
            reaperBlockDepots.add(Point2d.of(101.0f, 31.0f));
            reaperBlockDepots.add(Point2d.of(99.0f, 30.0f));
            reaperBlockDepots.add(Point2d.of(126.0f, 63.0f));
            reaperBlock3x3s.add(Point2d.of(102.5f, 33.5f));

            BUNKER_NATURAL = Point2d.of(115.5f, 54.5f);
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

            BUNKER_NATURAL = Point2d.of(71.5f, 131.5f);

        } else {
            proxyBarracksPos = Point2d.of(69.5f, 99.5f);
            proxyBunkerPos = Point2d.of(73.5f, 131.5f);
            proxyBunkerPos2 = Point2d.of(44.5f, 108.5f);

            reaperBlock3x3s.add(Point2d.of(141.5f, 47.5f));

            myMineralPos = Point2d.of(150.5f, 27.5f);
            enemyMineralPos = Point2d.of(33.5f, 140.5f);

            BUNKER_NATURAL = Point2d.of(111.5f, 36.5f);
        }
    }

    private static void setLocationsForCuriousMinds(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(99.5f, 103.5f));
        muleLetterPosList.add(Point2d.of(107.5f, 103.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(86.5f, 61.5f);
            proxyBunkerPos = Point2d.of(121.5f, 56.5f);
            proxyBunkerPos2 = Point2d.of(98.5f, 44.5f);

            reaperBlock3x3s.add(Point2d.of(42.5f, 106.5f));
            reaperBlockDepots.add(Point2d.of(45.0f, 108.0f));
            reaperBlockDepots.add(Point2d.of(43.0f, 102.0f));
            reaperBlockDepots.add(Point2d.of(42.0f, 104.0f));

            myMineralPos = Point2d.of(20.5f, 116.5f);
            enemyMineralPos = Point2d.of(131.5f, 23.5f);

            BUNKER_NATURAL = Point2d.of(30.5f, 83.5f);

        } else {
            proxyBarracksPos = Point2d.of(58.5f, 76.5f);
            proxyBunkerPos = Point2d.of(30.5f, 83.5f);
            proxyBunkerPos2 = Point2d.of(53.5f, 95.5f);

            reaperBlock3x3s.add(Point2d.of(109.5f, 33.5f));
            reaperBlockDepots.add(Point2d.of(107.0f, 32.0f));
            reaperBlockDepots.add(Point2d.of(110.0f, 36.0f));
            reaperBlockDepots.add(Point2d.of(109.0f, 38.0f));

            myMineralPos = Point2d.of(131.5f, 23.5f);
            enemyMineralPos = Point2d.of(20.5f, 116.5f);

            BUNKER_NATURAL = Point2d.of(121.5f, 56.5f);
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

            BUNKER_NATURAL = Point2d.of(62.5f, 138.5f);
        }
        else {
            proxyBarracksPos = Point2d.of(72.5f, 105.5f);
            proxyBunkerPos = Point2d.of(62.5f, 139.5f);
            proxyBunkerPos2 = Point2d.of(55.5f, 119.5f);

            reaperBlockDepots.add(Point2d.of(143.0f, 58.0f)); //reaperJump1
            reaperBlock3x3s.add(Point2d.of(145.5f, 59.5f));
            reaperBlock3x3s.add(Point2d.of(140.5f, 56.5f));

            myMineralPos = Point2d.of(155.0f, 41.5f);
            enemyMineralPos = Point2d.of(37f, 146.5f);

            BUNKER_NATURAL = Point2d.of(129.5f, 49.5f);
        }
    }

    private static void setLocationsForDiscoBloodBath(boolean isTopPos) {
        if (isTopPos) {
            myMineralPos = Point2d.of(39.0f, 108.5f);
            enemyMineralPos = Point2d.of(161.0f, 71.5f);

            reaperBlock3x3s.add(Point2d.of(56.5f, 122.5f));
            reaperBlockDepots.add(Point2d.of(59.0f, 124.0f));

            BUNKER_NATURAL = Point2d.of(56.5f, 139.5f);
        }
        else {
            myMineralPos = Point2d.of(161.0f, 71.5f);
            enemyMineralPos = Point2d.of(39.0f, 108.5f);

            reaperBlockDepots.add(Point2d.of(143.0f, 58.0f));
            reaperBlock3x3s.add(Point2d.of(142.5f, 55.5f));

            BUNKER_NATURAL = Point2d.of(143.5f, 40.5f);
        }
    }

    private static void setLocationsForEphemeron(boolean isTopPos) {
        if (isTopPos) {
            myMineralPos = Point2d.of(22.0f, 139.5f);
            enemyMineralPos = Point2d.of(138.0f, 20.5f);

            BUNKER_NATURAL = Point2d.of(38.5f, 111.5f);

            reaperBlockDepots.add(Point2d.of(46.0f, 125.0f));
            reaperBlock3x3s.add(Point2d.of(43.5f, 123.5f));
            reaperBlockDepots.add(Point2d.of(42.0f, 121.0f));
        }
        else {
            myMineralPos = Point2d.of(138.0f, 20.5f);
            enemyMineralPos = Point2d.of(22.0f, 139.5f);

            reaperBlockDepots.add(Point2d.of(114.0f, 35.0f));
            reaperBlock3x3s.add(Point2d.of(116.5f, 36.5f));
            reaperBlockDepots.add(Point2d.of(118.0f, 39.0f));

            BUNKER_NATURAL = Point2d.of(121.5f, 48.5f);
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

            BUNKER_NATURAL = Point2d.of(128.5f, 117.5f);
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

            BUNKER_NATURAL = Point2d.of(47.5f, 54.5f);
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

            BUNKER_NATURAL = Point2d.of(147.5f, 137.5f);
        }
        else {
            proxyBarracksPos = Point2d.of(100.5f, 123.5f);
            proxyBunkerPos = Point2d.of(151.5f, 138.5f);
            proxyBunkerPos2 = Point2d.of(131.5f, 133.5f);

            reaperBlockDepots.add(Point2d.of(64.0f, 68.0f));
            reaperBlock3x3s.add(Point2d.of(61.5f, 67.5f));

            myMineralPos = Point2d.of(53f, 47.5f);
            enemyMineralPos = Point2d.of(147.0f, 164.5f);

            BUNKER_NATURAL = Point2d.of(52.5f, 74.5f);
        }
    }

    private static void setLocationsForGlitteringAshes(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(71.5f, 110.5f));
        muleLetterPosList.add(Point2d.of(78.5f, 110.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(112.5f, 70.5f);
            proxyBunkerPos = Point2d.of(77.5f, 56.5f);
            proxyBunkerPos2 = Point2d.of(62.5f, 76.5f);

            reaperBlock3x3s.add(Point2d.of(163.5f, 132.5f));
            reaperBlockDepots.add(Point2d.of(161.0f, 134.0f));
            reaperBlockDepots.add(Point2d.of(160.0f, 136.0f));
            reaperBlockDepots.add(Point2d.of(158.0f, 137.0f));

            myMineralPos = Point2d.of(175f, 150.5f);
            enemyMineralPos = Point2d.of(41f, 53.5f);
            BUNKER_NATURAL = Point2d.of(138.5f, 147.5f);
        }
        else {
            proxyBarracksPos = Point2d.of(102.5f, 132.5f);
            proxyBunkerPos = Point2d.of(138.5f, 147.5f);
            proxyBunkerPos2 = Point2d.of(153.5f, 127.5f);

            reaperBlock3x3s.add(Point2d.of(52.5f, 71.5f));
            reaperBlockDepots.add(Point2d.of(55.0f, 70.0f));
            reaperBlockDepots.add(Point2d.of(56.0f, 68.0f));
            reaperBlockDepots.add(Point2d.of(58.0f, 67.0f));

            myMineralPos = Point2d.of(41f, 53.5f);
            enemyMineralPos = Point2d.of(175f, 150.5f);

            BUNKER_NATURAL = Point2d.of(77.5f, 56.5f);
        }
    }

    private static void setLocationsForFlat48(boolean isTopPos) {
        if (isTopPos) {
            extraDepots.add(Point2d.of(23.0f, 51.0f));
            _3x3AddonPosList.add(Point2d.of(23.0f, 52.0f));
        } else {
            extraDepots.add(Point2d.of(23.0f, 51.0f));
            _3x3AddonPosList.add(Point2d.of(23.0f, 51.0f));
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

            BUNKER_NATURAL = Point2d.of(48.5f, 78.5f);

            reaperBlockDepots.add(Point2d.of(46.0f, 87.0f));
            reaperBlock3x3s.add(Point2d.of(128.5f, 129.5f));
            reaperBlockDepots.add(Point2d.of(130.0f, 125.0f));
        }
        else { //right spawn
            proxyBarracksPos = Point2d.of(68.5f, 98.5f);
            proxyBunkerPos = Point2d.of(49.5f, 77.5f);
            //proxyBunkerPos2 unnecessary

            myMineralPos = Point2d.of(183f, 51.5f);
            enemyMineralPos = Point2d.of(25f, 51.5f);

            reaperBlockDepots.add(Point2d.of(162.0f, 87.0f));

            BUNKER_NATURAL = Point2d.of(159.5f, 78.5f);
        }
    }

    private static void setLocationsForHardwire(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(103.5f, 94.5f));
        muleLetterPosList.add(Point2d.of(110.5f, 94.5f));
        if (isTopPos) {
            myMineralPos = Point2d.of(165f, 158.5f);
            enemyMineralPos = Point2d.of(51f, 57.5f);

            proxyBarracksPos = Point2d.of(119.5f, 85.5f);
            proxyBunkerPos = Point2d.of(87.5f, 55.5f);
            proxyBunkerPos2 = Point2d.of(72.5f, 81.5f);

            reaperBlock3x3s.add(Point2d.of(156.5f, 139.5f));
            reaperBlockDepots.add(Point2d.of(158.0f, 137.0f));
            reaperBlockDepots.add(Point2d.of(154.0f, 141.0f));

            BUNKER_NATURAL = Point2d.of(127.5f, 160.5f);
        } else {
            myMineralPos = Point2d.of(51f, 57.5f);
            enemyMineralPos = Point2d.of(165f, 158.5f);

            proxyBarracksPos = Point2d.of(95.5f, 131.5f);
            proxyBunkerPos = Point2d.of(128.5f, 160.5f);
            proxyBunkerPos2 = Point2d.of(142.5f, 134.5f);

            reaperBlockDepots.add(Point2d.of(59.0f, 78.0f));
            reaperBlock3x3s.add(Point2d.of(59.5f, 75.5f));
            reaperBlockDepots.add(Point2d.of(62.0f, 75.0f));

            BUNKER_NATURAL = Point2d.of(86.5f, 54.5f);
        }
    }

    private static void setLocationsForInsideOrOut(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(64.5f, 88.5f));
        muleLetterPosList.add(Point2d.of(72.5f, 88.5f));
        if (isTopPos) {
            myMineralPos = Point2d.of(134f, 130.5f);
            enemyMineralPos = Point2d.of(26f, 29.5f);

            proxyBarracksPos = Point2d.of(64.5f, 34.5f);
            proxyBunkerPos = Point2d.of(91.5f, 62.5f);
            proxyBunkerPos2 = Point2d.of(52.5f, 58.5f);

            BUNKER_NATURAL = Point2d.of(98.5f, 122.5f);
        } else {
            myMineralPos = Point2d.of(26f, 29.5f);
            enemyMineralPos = Point2d.of(134f, 130.5f);

            proxyBarracksPos = Point2d.of(67.5f, 97.5f);
            proxyBunkerPos = Point2d.of(95.5f, 125.5f);
            proxyBunkerPos2 = Point2d.of(107.5f, 101.5f);

            BUNKER_NATURAL = Point2d.of(61.5f, 37.5f);
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

            BUNKER_NATURAL = Point2d.of(177.5f, 138.5f);
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

            BUNKER_NATURAL = Point2d.of(78.5f, 97.5f);
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

            BUNKER_NATURAL = Point2d.of(93.5f, 144.5f);
        }
        else {
            proxyBarracksPos = Point2d.of(67.5f, 119.5f);
            proxyBunkerPos = Point2d.of(92.5f, 143.5f);
            proxyBunkerPos2 = Point2d.of(108.5f, 123.5f);

            reaperBlockDepots.add(Point2d.of(52.0f, 52.0f));
            reaperBlockDepots.add(Point2d.of(54.0f, 51.0f));

            myMineralPos = Point2d.of(34f, 33.5f);
            enemyMineralPos = Point2d.of(134, 152.5f);

            BUNKER_NATURAL = Point2d.of(73.5f, 40.5f);
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

            BUNKER_NATURAL = Point2d.of(45.5f, 99.5f);

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

            BUNKER_NATURAL = Point2d.of(138.5f, 65.5f);
        }
    }

    private static void setLocationsForMoondance(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(99.5f, 101.5f));
        muleLetterPosList.add(Point2d.of(107.5f, 102.5f));
        if (isTopPos) {
            myMineralPos = Point2d.of(73f, 166.5f);
            enemyMineralPos = Point2d.of(119f, 37.5f);

            proxyBarracksPos = Point2d.of(93.5f, 87.5f);
            proxyBunkerPos = Point2d.of(129.5f, 73.5f);
            proxyBunkerPos2 = Point2d.of(99.5f, 58.5f);

            reaperBlock3x3s.add(Point2d.of(84.5f, 152.5f));
            reaperBlock3x3s.add(Point2d.of(89.5f, 155.5f));
            reaperBlockDepots.add(Point2d.of(87.0f, 154.0f));

            BUNKER_NATURAL = Point2d.of(63.5f, 129.5f);
        } else {
            myMineralPos = Point2d.of(119f, 37.5f);
            enemyMineralPos = Point2d.of(73f, 166.5f);

            proxyBarracksPos = Point2d.of(98.5f, 116.5f);
            proxyBunkerPos = Point2d.of(62.5f, 130.5f);
            proxyBunkerPos2 = Point2d.of(92.5f, 145.5f);

            reaperBlockDepots.add(Point2d.of(105.0f, 50.0f));
            reaperBlock3x3s.add(Point2d.of(107.5f, 51.5f));
            reaperBlock3x3s.add(Point2d.of(102.5f, 48.5f));

            BUNKER_NATURAL = Point2d.of(128.5f, 74.5f);
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

            BUNKER_NATURAL = Point2d.of(52.5f, 113.5f);
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

            BUNKER_NATURAL = Point2d.of(138.5f, 58.5f);
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

            BUNKER_NATURAL = Point2d.of(117.5f, 143.5f);
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

            BUNKER_NATURAL = Point2d.of(74.5f, 60.5f);
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

            BUNKER_NATURAL = Point2d.of(105.5f, 135.5f);
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

            BUNKER_NATURAL = Point2d.of(62.5f, 36.5f);
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

            BUNKER_NATURAL = Point2d.of(56.5f, 108.5f);
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

            BUNKER_NATURAL = Point2d.of(143.5f, 63.5f);
        }
    }

    private static void setLocationsForSimulacrum(boolean isTopPos) {
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(129.5f, 87.5f);
            proxyBunkerPos = Point2d.of(158.5f, 77.5f);

            myMineralPos = Point2d.of(47f, 140.5f);
            enemyMineralPos = Point2d.of(169f, 43.5f);

            BUNKER_NATURAL = Point2d.of(58.5f, 105.5f);

            reaperBlockDepots.add(Point2d.of(79.0f, 134.0f));
            reaperBlock3x3s.add(Point2d.of(76.5f, 132.5f));
            reaperBlockDepots.add(Point2d.of(80.0f, 136.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(84.5f, 96.5f);
            proxyBunkerPos = Point2d.of(57.5f, 106.5f);

            myMineralPos = Point2d.of(169f, 43.5f);
            enemyMineralPos = Point2d.of(47f, 140.5f);

            reaperBlock3x3s.add(Point2d.of(139.5f, 51.5f));
            reaperBlockDepots.add(Point2d.of(137.0f, 50.0f));
            reaperBlockDepots.add(Point2d.of(136.0f, 48.0f));

            BUNKER_NATURAL = Point2d.of(157.5f, 78.5f);
        }
    }

    private static void setLocationsForStargazers(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(93.5f, 57.5f));
        muleLetterPosList.add(Point2d.of(101.5f, 57.5f));
        if (isTopPos) {
            myMineralPos = Point2d.of(38.5f, 131f);
            enemyMineralPos = Point2d.of(161.5f, 131f);

            proxyBarracksPos = Point2d.of(100.5f, 88.5f);
            proxyBunkerPos = Point2d.of(151.5f, 92.5f);
            proxyBunkerPos2 = Point2d.of(135.5f, 102.5f);

            reaperBlock3x3s.add(Point2d.of(53.5f, 110.5f));
            reaperBlockDepots.add(Point2d.of(55.0f, 113.0f));
            reaperBlockDepots.add(Point2d.of(57.0f, 114.0f));

            BUNKER_NATURAL = Point2d.of(48.5f, 93.5f);
        }
        else {
            myMineralPos = Point2d.of(161.5f, 131f);
            enemyMineralPos = Point2d.of(38.5f, 131f);

            proxyBarracksPos = Point2d.of(97.5f, 88.5f);
            proxyBunkerPos = Point2d.of(48.5f, 92.5f);
            proxyBunkerPos2 = Point2d.of(65.5f, 102.5f);

            reaperBlockDepots.add(Point2d.of(145.0f, 113.0f));
            reaperBlockDepots.add(Point2d.of(143.0f, 114.0f));
            reaperBlock3x3s.add(Point2d.of(146.5f, 110.5f));

            BUNKER_NATURAL = Point2d.of(152.5f, 93.5f);
        }
    }

    private static void setLocationsForSubmarine(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(51.5f, 55.5f));
        muleLetterPosList.add(Point2d.of(63.5f, 55.5f));
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(105.5f, 78.5f);
            proxyBunkerPos = Point2d.of(124.5f, 61.5f);
            proxyBunkerPos2 = Point2d.of(106.5f, 45.5f);

            reaperBlockDepots.add(WALL_2x2);
            reaperBlockDepots.add(MID_WALL_2x2);
            reaperBlockDepots.add(Point2d.of(33.0f, 90.0f));
            reaperBlock3x3s.add(Point2d.of(48.5f, 124.5f));
            reaperBlock3x3s.add(Point2d.of(46.5f, 121.5f));
            reaperBlock3x3s.add(Point2d.of(44.5f, 118.5f));

            myMineralPos = Point2d.of(25.0f, 128.5f);
            enemyMineralPos = Point2d.of(143.0f,35.5f);

            BUNKER_NATURAL = Point2d.of(44.5f, 104.5f);
        }
        else {
            proxyBarracksPos = Point2d.of(62.5f, 85.5f);
            proxyBunkerPos = Point2d.of(43.5f, 102.5f);
            proxyBunkerPos2 = Point2d.of(61.5f, 118.5f);

            reaperBlockDepots.add(WALL_2x2);
            reaperBlockDepots.add(MID_WALL_2x2);
            reaperBlockDepots.add(Point2d.of(135.0f, 74.0f));
            reaperBlock3x3s.add(Point2d.of(119.5f, 39.5f));
            reaperBlock3x3s.add(Point2d.of(121.5f, 42.5f));
            reaperBlock3x3s.add(Point2d.of(123.5f, 45.5f));

            myMineralPos = Point2d.of(143.0f,35.5f);
            enemyMineralPos = Point2d.of(25.0f, 128.5f);

            BUNKER_NATURAL = Point2d.of(123.5f, 59.5f);
        }
    }

    private static void setLocationsForThunderBird(boolean isTopPos) {
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(129.5f, 82.5f);
            proxyBunkerPos = Point2d.of(141.5f, 51.5f);

            myMineralPos = Point2d.of(38.0f, 140.5f);
            enemyMineralPos = Point2d.of(154.0f, 15.5f);

            BUNKER_NATURAL = Point2d.of(52.5f, 106.5f);

            reaperBlockDepots.add(Point2d.of(54.0f, 123.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(61.5f, 74.5f);
            proxyBunkerPos = Point2d.of(50.5f, 104.5f);

            myMineralPos = Point2d.of(154.0f, 15.5f);
            enemyMineralPos = Point2d.of(38.0f, 140.5f);

            BUNKER_NATURAL = Point2d.of(140.5f, 49.5f);

            reaperBlockDepots.add(Point2d.of(139.0f, 34.0f));
        }
    }

    private static void setLocationsForTriton(boolean isTopPos) {
        if (isTopPos) {
            myMineralPos = Point2d.of(48f, 158.5f);
            enemyMineralPos = Point2d.of(168f, 45.5f);

            reaperBlockDepots.add(Point2d.of(69f, 144f));
            reaperBlock3x3s.add(Point2d.of(71.5f, 144.5f));

            BUNKER_NATURAL = Point2d.of(87.5f, 152.5f);
        }
        else {
            myMineralPos = Point2d.of(168f, 45.5f);
            enemyMineralPos = Point2d.of(48f, 158.5f);

            reaperBlock3x3s.add(Point2d.of(145.5f, 59.5f));
            reaperBlockDepots.add(Point2d.of(143f, 60f));

            BUNKER_NATURAL = Point2d.of(128.5f, 51.5f);
        }
    }

    private static void setLocationsForWintersGate(boolean isTopPos) {
        if (isTopPos) {
            myMineralPos = Point2d.of(47f, 141.5f);
            enemyMineralPos = Point2d.of(145f, 22.5f);

            BUNKER_NATURAL = Point2d.of(49.5f, 103.5f);

            reaperBlock3x3s.add(Point2d.of(65.5f, 124.5f));
            reaperBlockDepots.add(Point2d.of(68.0f, 125.0f));
        }
        else {
            myMineralPos = Point2d.of(145f, 22.5f);
            enemyMineralPos = Point2d.of(47f, 141.5f);

            BUNKER_NATURAL = Point2d.of(141.5f, 60.5f);

            reaperBlock3x3s.add(Point2d.of(126.5f, 39.5f));
            reaperBlockDepots.add(Point2d.of(124.0f, 39.0f));
        }
    }

    private static void setLocationsForWaterfall(boolean isTopPos) {
        muleLetterPosList.add(Point2d.of(72.5f, 58.5f));
        muleLetterPosList.add(Point2d.of(84.5f, 58.5f));
        if (isTopPos) {
            myMineralPos = Point2d.of(121f, 128.5f);
            enemyMineralPos = Point2d.of(23f, 27.5f);

            proxyBarracksPos = Point2d.of(85.5f, 63.5f);
            proxyBunkerPos = Point2d.of(58.5f, 31.5f);
            proxyBunkerPos2 = Point2d.of(44.5f, 58.5f);

            reaperBlock3x3s.add(Point2d.of(100.5f, 111.5f));
            reaperBlock3x3s.add(Point2d.of(105.5f, 108.5f));
            reaperBlockDepots.add(Point2d.of(98.0f, 113.0f));
            reaperBlockDepots.add(Point2d.of(103.0f, 110.0f));

            BUNKER_NATURAL = Point2d.of(85.5f, 123.5f);
        }
        else {
            myMineralPos = Point2d.of(23f, 27.5f);
            enemyMineralPos = Point2d.of(121f, 128.5f);

            proxyBarracksPos = Point2d.of(56.5f, 93.5f);
            proxyBunkerPos = Point2d.of(85.5f, 124.5f);
            proxyBunkerPos2 = Point2d.of(99.5f, 98.5f);

            reaperBlockDepots.add(Point2d.of(46.0f, 43.0f));
            reaperBlockDepots.add(Point2d.of(41.0f, 46.0f));
            reaperBlock3x3s.add(Point2d.of(43.5f, 44.5f));
            reaperBlock3x3s.add(Point2d.of(38.5f, 47.5f));

            BUNKER_NATURAL = Point2d.of(58.5f, 32.5f);
        }
    }

    private static void setLocationsForWorldOfSleepers(boolean isTopPos) {
        if (isTopPos) {
            myMineralPos = Point2d.of(150f, 148.5f);
            enemyMineralPos = Point2d.of(34f, 19.5f);

            BUNKER_NATURAL = Point2d.of(137.5f, 106.5f);

            reaperBlock3x3s.add(Point2d.of(136.0f, 131.0f));
            reaperBlockDepots.add(Point2d.of(135.5f, 133.5f));
        }
        else {
            myMineralPos = Point2d.of(34f, 19.5f);
            enemyMineralPos = Point2d.of(150f, 148.5f);

            BUNKER_NATURAL = Point2d.of(46.5f, 61.5f);

            reaperBlock3x3s.add(Point2d.of(48.5f, 34.5f));
            reaperBlockDepots.add(Point2d.of(48.0f, 37.0f));
        }
    }

    private static void setLocationsForZen(boolean isTopPos) {
        if (isTopPos) {
            proxyBarracksPos = Point2d.of(71.5f, 87.5f);
            proxyBunkerPos = Point2d.of(66.5f, 50.5f);

            myMineralPos = Point2d.of(150f, 140.5f);
            enemyMineralPos = Point2d.of(42f, 31.5f);

            BUNKER_NATURAL = Point2d.of(129.5f, 112.5f);

            reaperBlock3x3s.add(Point2d.of(126.5f, 144.5f));
            reaperBlockDepots.add(Point2d.of(144.0f, 121.0f));
            reaperBlockDepots.add(Point2d.of(126.0f, 147.0f));
        }
        else {
            proxyBarracksPos = Point2d.of(119.5f, 85.5f);
            proxyBunkerPos = Point2d.of(125.5f, 121.5f);

            myMineralPos = Point2d.of(42f, 31.5f);
            enemyMineralPos = Point2d.of(150f, 140.5f);

            reaperBlock3x3s.add(Point2d.of(65.5f, 24.5f));
            reaperBlockDepots.add(Point2d.of(48.0f, 51.0f));
            reaperBlockDepots.add(Point2d.of(66.0f, 27.0f));

            BUNKER_NATURAL = Point2d.of(61.5f, 58.5f);
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

            case MapNames.BERLINGRAD_AIE:
                baseLocations.add(Point2d.of(31.5f, 131.5f));
                baseLocations.add(Point2d.of(28.5f, 107.5f));
                baseLocations.add(Point2d.of(60.5f, 112.5f));
                baseLocations.add(Point2d.of(83.5f, 132.5f));
                baseLocations.add(Point2d.of(25.5f, 77.5f));
                baseLocations.add(Point2d.of(25.5f, 47.5f));
                baseLocations.add(Point2d.of(33.5f, 21.5f));
                baseLocations.add(Point2d.of(118.5f, 134.5f));
                baseLocations.add(Point2d.of(126.5f, 108.5f));
                baseLocations.add(Point2d.of(126.5f, 78.5f));
                baseLocations.add(Point2d.of(68.5f, 23.5f));
                baseLocations.add(Point2d.of(91.5f, 43.5f));
                baseLocations.add(Point2d.of(123.5f, 48.5f));
                baseLocations.add(Point2d.of(120.5f, 24.5f));
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

            case MapNames.CURIOUS_MINDS_AIE:
                baseLocations.add(Point2d.of(26.5f, 115.5f));
                baseLocations.add(Point2d.of(23.5f, 88.5f));
                baseLocations.add(Point2d.of(50.5f, 91.5f));
                baseLocations.add(Point2d.of(66.5f, 115.5f));
                baseLocations.add(Point2d.of(23.5f, 55.5f));
                baseLocations.add(Point2d.of(24.5f, 20.5f));
                baseLocations.add(Point2d.of(127.5f, 119.5f));
                baseLocations.add(Point2d.of(128.5f, 84.5f));
                baseLocations.add(Point2d.of(85.5f, 24.5f));
                baseLocations.add(Point2d.of(101.5f, 48.5f));
                baseLocations.add(Point2d.of(128.5f, 51.5f));
                baseLocations.add(Point2d.of(125.5f, 24.5f));
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

            case MapNames.GLITTERING_ASHES_AIE:
                baseLocations.add(Point2d.of(167.5f, 149.5f));
                baseLocations.add(Point2d.of(140.5f, 156.5f));
                baseLocations.add(Point2d.of(150.5f, 125.5f));
                baseLocations.add(Point2d.of(171.5f, 102.5f));
                baseLocations.add(Point2d.of(123.5f, 122.5f));
                baseLocations.add(Point2d.of(107.5f, 156.5f));
                baseLocations.add(Point2d.of(64.5f, 159.5f));
                baseLocations.add(Point2d.of(152.5f, 80.5f));
                baseLocations.add(Point2d.of(173.5f, 74.5f));
                baseLocations.add(Point2d.of(42.5f, 129.5f));
                baseLocations.add(Point2d.of(63.5f, 123.5f));
                baseLocations.add(Point2d.of(151.5f, 44.5f));
                baseLocations.add(Point2d.of(108.5f, 47.5f));
                baseLocations.add(Point2d.of(92.5f, 81.5f));
                baseLocations.add(Point2d.of(44.5f, 101.5f));
                baseLocations.add(Point2d.of(65.5f, 78.5f));
                baseLocations.add(Point2d.of(75.5f, 47.5f));
                baseLocations.add(Point2d.of(48.5f, 54.5f));
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

            case MapNames.HARDWIRE_AIE:
                baseLocations.add(Point2d.of(157.5f, 157.5f));
                baseLocations.add(Point2d.of(135.5f, 167.5f));
                baseLocations.add(Point2d.of(140.5f, 134.5f));
                baseLocations.add(Point2d.of(156.5f, 108.5f));
                baseLocations.add(Point2d.of(106.5f, 168.5f));
                baseLocations.add(Point2d.of(67.5f, 164.5f));
                baseLocations.add(Point2d.of(91.5f, 148.5f));
                baseLocations.add(Point2d.of(61.5f, 137.5f));
                baseLocations.add(Point2d.of(154.5f, 78.5f));
                baseLocations.add(Point2d.of(124.5f, 67.5f));
                baseLocations.add(Point2d.of(148.5f, 51.5f));
                baseLocations.add(Point2d.of(109.5f, 47.5f));
                baseLocations.add(Point2d.of(59.5f, 107.5f));
                baseLocations.add(Point2d.of(75.5f, 81.5f));
                baseLocations.add(Point2d.of(80.5f, 48.5f));
                baseLocations.add(Point2d.of(58.5f, 58.5f));
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

            case MapNames.INSIDE_AND_OUT_AIE:
                baseLocations.add(Point2d.of(126.5f, 129.5f));
                baseLocations.add(Point2d.of(100.5f, 132.5f));
                baseLocations.add(Point2d.of(111.5f, 104.5f));
                baseLocations.add(Point2d.of(129.5f, 77.5f));
                baseLocations.add(Point2d.of(70.5f, 132.5f));
                baseLocations.add(Point2d.of(34.5f, 131.5f));
                baseLocations.add(Point2d.of(125.5f, 28.5f));
                baseLocations.add(Point2d.of(89.5f, 27.5f));
                baseLocations.add(Point2d.of(30.5f, 82.5f));
                baseLocations.add(Point2d.of(48.5f, 55.5f));
                baseLocations.add(Point2d.of(59.5f, 27.5f));
                baseLocations.add(Point2d.of(33.5f, 30.5f));
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

            case MapNames.MOONDANCE_AIE:
                baseLocations.add(Point2d.of(72.5f, 160.5f));
                baseLocations.add(Point2d.of(57.5f, 138.5f));
                baseLocations.add(Point2d.of(87.5f, 141.5f));
                baseLocations.add(Point2d.of(110.5f, 161.5f));
                baseLocations.add(Point2d.of(48.5f, 109.5f));
                baseLocations.add(Point2d.of(45.5f, 160.5f));
                baseLocations.add(Point2d.of(143.5f, 160.5f));
                baseLocations.add(Point2d.of(43.5f, 76.5f));
                baseLocations.add(Point2d.of(67.5f, 86.5f));
                baseLocations.add(Point2d.of(124.5f, 117.5f));
                baseLocations.add(Point2d.of(148.5f, 127.5f));
                baseLocations.add(Point2d.of(48.5f, 43.5f));
                baseLocations.add(Point2d.of(146.5f, 43.5f));
                baseLocations.add(Point2d.of(143.5f, 94.5f));
                baseLocations.add(Point2d.of(81.5f, 42.5f));
                baseLocations.add(Point2d.of(104.5f, 62.5f));
                baseLocations.add(Point2d.of(134.5f, 65.5f));
                baseLocations.add(Point2d.of(119.5f, 43.5f));
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

            case MapNames.STARGAZERS_AIE:
                baseLocations.add(Point2d.of(39.5f, 123.5f));
                baseLocations.add(Point2d.of(40.5f, 96.5f));
                //baseLocations.add(Point2d.of(74.5f, 129.5f));
                baseLocations.add(Point2d.of(65.5f, 102.5f));
                baseLocations.add(Point2d.of(41.5f, 70.5f));
                baseLocations.add(Point2d.of(49.5f, 41.5f));
                baseLocations.add(Point2d.of(100.5f, 34.5f));
                baseLocations.add(Point2d.of(150.5f, 41.5f));
                baseLocations.add(Point2d.of(158.5f, 70.5f));
                baseLocations.add(Point2d.of(134.5f, 102.5f));
                //baseLocations.add(Point2d.of(125.5f, 129.5f));
                baseLocations.add(Point2d.of(159.5f, 96.5f));
                baseLocations.add(Point2d.of(160.5f, 123.5f));
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

            case MapNames.WATERFALL_AIE:
                baseLocations.add(Point2d.of(113.5f, 127.5f));
                baseLocations.add(Point2d.of(90.5f, 131.5f));
                baseLocations.add(Point2d.of(96.5f, 99.5f));
                baseLocations.add(Point2d.of(114.5f, 80.5f));
                baseLocations.add(Point2d.of(60.5f, 132.5f));
                baseLocations.add(Point2d.of(33.5f, 117.5f));
                baseLocations.add(Point2d.of(44.5f, 92.5f));
                baseLocations.add(Point2d.of(99.5f, 63.5f));
                baseLocations.add(Point2d.of(110.5f, 38.5f));
                baseLocations.add(Point2d.of(83.5f, 23.5f));
                baseLocations.add(Point2d.of(29.5f, 75.5f));
                baseLocations.add(Point2d.of(47.5f, 56.5f));
                baseLocations.add(Point2d.of(53.5f, 24.5f));
                baseLocations.add(Point2d.of(30.5f, 28.5f));
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

    public static boolean isMySpawnTop() {
        return Bot.OBS.getStartLocation().getY() > PosConstants.SCREEN_TOP_RIGHT.getY()/2;
    }

    public static boolean isMySpawnLeft() {
        return Bot.OBS.getStartLocation().getX() < PosConstants.SCREEN_TOP_RIGHT.getX()/2;
    }

    public static Point2d getBackCorner() {
        return Point2d.of(
                isMySpawnLeft() ? PosConstants.SCREEN_BOTTOM_LEFT.getX() : PosConstants.SCREEN_TOP_RIGHT.getX(),
                isMySpawnTop() ? PosConstants.SCREEN_TOP_RIGHT.getY() : PosConstants.SCREEN_BOTTOM_LEFT.getY()
        );
    }
}
