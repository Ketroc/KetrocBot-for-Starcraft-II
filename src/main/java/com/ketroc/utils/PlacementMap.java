package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;

import java.util.Arrays;

public class PlacementMap {
    public static boolean[][] placementMap = new boolean[400][400];
    public static boolean[][] ccPlacementMap = new boolean[400][400];
    public static final Point2d[] _3x3_WITH_ADDON_SHAPE = new Point2d[] {
            Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1),
            Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0), Point2d.of(3, 0),
            Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1), Point2d.of(3, -1),
            Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2), Point2d.of(5, -2),
            Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3)};
    public static final Point2d[] _3x3_SHAPE = new Point2d[] {
            Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1),
            Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0),
            Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1),
            Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2),
            Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3)};
    public static final Point2d[] _5x5_SHAPE = new Point2d[] {
            Point2d.of(-2, 2), Point2d.of(-1, 2), Point2d.of(0, 2), Point2d.of(1, 2), Point2d.of(2, 2),
            Point2d.of(-3, 1), Point2d.of(-2, 1), Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1), Point2d.of(2, 1),
            Point2d.of(-4, 0), Point2d.of(-3, 0), Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0),
            Point2d.of(-4, -1), Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1),
            Point2d.of(-4, -2), Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2),
            Point2d.of(-3, -3), Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3),
            Point2d.of(-2, -4), Point2d.of(-1, -4), Point2d.of(0, -4), Point2d.of(1, -4), Point2d.of(2, -4)};
    public static final Point2d[] GAS_CC_BLOCK_SHAPE = new Point2d[] {
            Point2d.of(-3, 4), Point2d.of(-2, 4), Point2d.of(-1, 4), Point2d.of(0, 4), Point2d.of(1, 4), Point2d.of(2, 4), Point2d.of(3, 4),
            Point2d.of(-4, 3), Point2d.of(-3, 3), Point2d.of(-2, 3), Point2d.of(-1, 3), Point2d.of(0, 3), Point2d.of(1, 3), Point2d.of(2, 3), Point2d.of(3, 3), Point2d.of(4, 3),
            Point2d.of(-4, 2), Point2d.of(-3, 2), Point2d.of(-2, 2), Point2d.of(-1, 2), Point2d.of(0, 2), Point2d.of(1, 2), Point2d.of(2, 2), Point2d.of(3, 2), Point2d.of(4, 2),
            Point2d.of(-4, 1), Point2d.of(-3, 1), Point2d.of(-2, 1), Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1), Point2d.of(2, 1), Point2d.of(3, 1), Point2d.of(4, 1),
            Point2d.of(-4, 0), Point2d.of(-3, 0), Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0), Point2d.of(3, 0), Point2d.of(4, 0),
            Point2d.of(-4, -1), Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1), Point2d.of(3, -1), Point2d.of(4, -1),
            Point2d.of(-4, -2), Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2),
            Point2d.of(-4, -3), Point2d.of(-3, -3), Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3),
            Point2d.of(-3, -4), Point2d.of(-2, -4), Point2d.of(-1, -4), Point2d.of(0, -4), Point2d.of(1, -4), Point2d.of(2, -4), Point2d.of(3, -4),
    };
    public static final Point2d[] MINERAL_CC_BLOCK_SHAPE = new Point2d[] {
            Point2d.of(-2.5f, 3), Point2d.of(-1.5f, 3), Point2d.of(-0.5f, 3), Point2d.of(0.5f, 3), Point2d.of(1.5f, 3), Point2d.of(2.5f, 3),
            Point2d.of(-3.5f, 2), Point2d.of(-2.5f, 2), Point2d.of(-1.5f, 2), Point2d.of(-0.5f, 2), Point2d.of(0.5f, 2), Point2d.of(1.5f, 2), Point2d.of(2.5f, 2), Point2d.of(3.5f, 2),
            Point2d.of(-3.5f, 1), Point2d.of(-2.5f, 1), Point2d.of(-1.5f, 1), Point2d.of(-0.5f, 1), Point2d.of(0.5f, 1), Point2d.of(1.5f, 1), Point2d.of(2.5f, 1), Point2d.of(3.5f, 1),
            Point2d.of(-3.5f, 0), Point2d.of(-2.5f, 0), Point2d.of(-1.5f, 0), Point2d.of(-0.5f, 0), Point2d.of(0.5f, 0), Point2d.of(1.5f, 0), Point2d.of(2.5f, 0), Point2d.of(3.5f, 0),
            Point2d.of(-3.5f, -1), Point2d.of(-2.5f, -1), Point2d.of(-1.5f, -1), Point2d.of(-0.5f, -1), Point2d.of(0.5f, -1), Point2d.of(1.5f, -1), Point2d.of(2.5f, -1), Point2d.of(3.5f, -1),
            Point2d.of(-3.5f, -2), Point2d.of(-2.5f, -2), Point2d.of(-1.5f, -2), Point2d.of(-0.5f, -2), Point2d.of(0.5f, -2), Point2d.of(1.5f, -2), Point2d.of(2.5f, -2), Point2d.of(3.5f, -2),
            Point2d.of(-2.5f, -3), Point2d.of(-1.5f, -3), Point2d.of(-0.5f, -3), Point2d.of(0.5f, -3), Point2d.of(1.5f, -3), Point2d.of(2.5f, -3)
    };
    public static int buildColumn;


    public static void onGameStart() {
        //LocationConstants.FACTORIES.clear();//TODO: delete
        initializeMap(false);
//TODO:bring back - this is code to find main base positions
//        setColumn();
//        populateMainBase3x3WithAddonPos(buildColumn, true);
//        if (LocationConstants.MAP.equals(MapNames.ICE_AND_CHROME506)) {
//            replaceFactoriesWithCommandCenters();
//        }
//        populateMainBase3x3Pos(buildColumn, true);
//        if (LocationConstants.MAP.equals(MapNames.ICE_AND_CHROME506)) {
//            topUp3x3List();
//            populateDepotPos();
//        }

        //visualizePlacementMap();
        //create2CellColumns();
    }

    private static void populateDepotPos() {
        int depotNum = 0;
        for (int x=(int)(LocationConstants.MAX_X - 0.5f); x>LocationConstants.MIN_X; x--) {
            for (int y=(int)(LocationConstants.MIN_Y + 0.5f); y<LocationConstants.MAX_Y; y++) {
                Point2d pos = Point2d.of(x, y);
                if ((x+2) % 7 == buildColumn && InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, pos)) {
                    if (canFit2x2(pos) && (pos.distance(Bot.OBS.getStartLocation().toPoint2d()) > 6.5f || !isNodeNearby(pos))) {
                        visualize2x2(pos);
                        DebugHelper.drawText(++depotNum + "", pos, Color.TEAL, 14);
                        makeUnavailable(Units.TERRAN_SUPPLY_DEPOT, pos);
                        LocationConstants.extraDepots.add(pos);
                    }
                }
            }
        }
    }

    private static boolean isNodeNearby(Point2d pos) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, u ->
                (UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()) || UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType())) &&
                UnitUtils.getDistance(u.unit(), pos) < 5).size() > 0;
    }

    private static void topUp3x3List() {
        while (LocationConstants._3x3Structures.size() < 4) {
            Point2d lastFactoryPos = LocationConstants.FACTORIES.remove(LocationConstants.FACTORIES.size() - 1);
            LocationConstants._3x3Structures.add(lastFactoryPos);
            LocationConstants.extraDepots.add(getAddOnPos(lastFactoryPos));
        }
    }

    public static void initializeMap(boolean isColumnSet) {
        for (float x=LocationConstants.MIN_X + 0.5f; x<LocationConstants.MAX_X; x++) {
            for (float y=LocationConstants.MIN_Y + 0.5f; y<LocationConstants.MAX_Y; y++) {
                placementMap[(int)x][(int)y] = Bot.OBS.isPlacable(Point2d.of(x, y));
                ccPlacementMap[(int)x][(int)y] = Bot.OBS.isPlacable(Point2d.of(x, y));
            }
        }

        //remove all mineral patches from the placement grid
        Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()) ||
                        UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType()))
                .forEach(node -> makeUnAvailableResourceNode(node));

        //remove all xel naga towers from the placement grid
        Bot.OBS.getUnits(Alliance.NEUTRAL, u -> u.unit().getType() == Units.NEUTRAL_XELNAGA_TOWER)
                .forEach(xelTower -> makeUnavailable(xelTower.unit()));

        //remove all destructible neutral units from the placement grid TODO: fix shapes of rocks
        Bot.OBS.getUnits(Alliance.NEUTRAL, u -> u.unit().getType() instanceof Units && UnitUtils.isDestructible(u.unit()))
                .forEach(destructible -> makeUnavailable(destructible.unit()));

        //remove start CC from to placement grid
        makeUnavailable5x5(Bot.OBS.getStartLocation().toPoint2d());
//        if (isColumnSet) {
//            DebugHelper.drawBox(Bot.OBS.getStartLocation().toPoint2d(), Color.YELLOW, 2.5f);
//        }

//        //remove ramp wall
//        makeUnavailable2x2(LocationConstants.WALL_2x2);
//        makeUnavailable3x3(LocationConstants.WALL_3x3);
//        makeUnavailable3x3(LocationConstants.MID_WALL_3x3);
//        DebugHelper.drawBox(LocationConstants.WALL_2x2, Color.YELLOW, 1f);
//        DebugHelper.drawBox(LocationConstants.WALL_3x3, Color.YELLOW, 1.5f);
//        DebugHelper.drawBox(LocationConstants.MID_WALL_3x3, Color.YELLOW, 1.5f);
//
//        //make space around ramp entrance
//        makeUnavailable3x3(LocationConstants.MID_WALL_3x3.add(-2, -2));
//        makeUnavailable3x3(LocationConstants.MID_WALL_3x3.add(0, -2));;
//        //DebugHelper.drawBox(LocationConstants.MID_WALL_3x3, Color.GRAY, 3.5f);
//
//
//        //remove reaper jump wall
//        if (LocationConstants.opponentRace == Race.TERRAN && Strategy.gamePlan != GamePlan.MARINE_RUSH) {
//            LocationConstants.reaperBlockDepots.forEach(p -> {
//                makeUnavailable2x2(p);
//                if (isColumnSet) {
//                    DebugHelper.drawBox(p, Color.YELLOW, 1f);
//                }
//            });
//            LocationConstants.reaperBlock3x3s.forEach(p -> {
//                makeUnavailable3x3(p);
//                if (isColumnSet) {
//                    DebugHelper.drawBox(p, Color.YELLOW, 1.5f);
//                }
//            });
//        }
    }

    public static void visualizePlacementMap() {
        for (float x=LocationConstants.MIN_X + 0.5f; x<LocationConstants.MAX_X; x++) {
            for (float y=LocationConstants.MIN_Y + 0.5f; y<LocationConstants.MAX_Y; y++) {
                if (placementMap[(int)x][(int)y]) {
                    DebugHelper.drawBox(Point2d.of(x, y), Color.TEAL, 0.4f);
                    //DebugHelper.drawText((int)x + "," + (int)y, Point2d.of(x-0.4f, y), Color.TEAL, 8);
                }
            }
        }
    }

    public static void visualizeCcPlacementMap() {
        for (float x=LocationConstants.MIN_X + 0.5f; x<LocationConstants.MAX_X; x++) {
            for (float y=LocationConstants.MIN_Y + 0.5f; y<LocationConstants.MAX_Y; y++) {
                if (ccPlacementMap[(int)x][(int)y]) {
                    DebugHelper.drawBox(Point2d.of(x, y), Color.TEAL, 0.4f);
                }
            }
        }
    }

    public static boolean isPlaceable(Point2d pos) {
        return isPlaceable(pos, false);
    }

    public static boolean isPlaceable(Point2d pos, boolean isCC) {
        return placementMap[(int)pos.getX()][(int)pos.getY()] &&
                (!isCC || ccPlacementMap[(int)pos.getX()][(int)pos.getY()]);
    }

    public static void change2x1(Point2d pos, boolean doMakeAvailable) {
        int x = (int) pos.getX();
        int y = (int) pos.getY();
        placementMap[x-1][y] = doMakeAvailable;
        placementMap[x][y] = doMakeAvailable;
    }

    public static void change1x1(Point2d pos, boolean doMakeAvailable) {
        int x = (int)pos.getX();
        int y = (int)pos.getY();
        placementMap[x][y] = doMakeAvailable;
    }

    private static void change2x2(Point2d pos, boolean doMakeAvailable) {
        int x = (int)pos.getX();
        int y = (int)pos.getY();
        placementMap[x-1][y] = doMakeAvailable;
        placementMap[x][y] = doMakeAvailable;
        placementMap[x-1][y-1] = doMakeAvailable;
        placementMap[x][y-1] = doMakeAvailable;
    }

    private static void change3x3(Point2d pos, boolean doMakeAvailable) {
        int x = (int)pos.getX();
        int y = (int)pos.getY();
        change1x1(pos, doMakeAvailable);
        placementMap[x-1][y-1] = doMakeAvailable;
        placementMap[x-1][y] = doMakeAvailable;
        placementMap[x-1][y+1] = doMakeAvailable;
        placementMap[x][y-1] = doMakeAvailable;
        placementMap[x][y+1] = doMakeAvailable;
        placementMap[x+1][y-1] = doMakeAvailable;
        placementMap[x+1][y] = doMakeAvailable;
        placementMap[x+1][y+1] = doMakeAvailable;
    }

    private static void change4x4(Point2d pos, boolean doMakeAvailable) { //x-2 -> x+1, y-2 -> y+1
        int x = (int)pos.getX();
        int y = (int)pos.getY();
        change2x2(pos, doMakeAvailable);

        placementMap[x-2][y+1] = doMakeAvailable;
        placementMap[x-1][y+1] = doMakeAvailable;
        placementMap[x][y+1] = doMakeAvailable;
        placementMap[x+1][y+1] = doMakeAvailable;

        placementMap[x-2][y] = doMakeAvailable;
        placementMap[x+1][y] = doMakeAvailable;

        placementMap[x-2][y-1] = doMakeAvailable;
        placementMap[x+1][y-1] = doMakeAvailable;

        placementMap[x-2][y-2] = doMakeAvailable;
        placementMap[x-1][y-2] = doMakeAvailable;
        placementMap[x][y-2] = doMakeAvailable;
        placementMap[x+1][y-2] = doMakeAvailable;
    }

    private static void change5x5(Point2d pos, boolean doMakeAvailable) {
        int x = (int) pos.getX();
        int y = (int) pos.getY();
        change3x3(pos, doMakeAvailable);
        placementMap[x-2][y-2] = doMakeAvailable;
        placementMap[x-2][y-1] = doMakeAvailable;
        placementMap[x-2][y] = doMakeAvailable;
        placementMap[x-2][y+1] = doMakeAvailable;
        placementMap[x-2][y+2] = doMakeAvailable;

        placementMap[x+2][y-2] = doMakeAvailable;
        placementMap[x+2][y-1] = doMakeAvailable;
        placementMap[x+2][y] = doMakeAvailable;
        placementMap[x+2][y+1] = doMakeAvailable;
        placementMap[x+2][y+2] = doMakeAvailable;

        placementMap[x-1][y-2] = doMakeAvailable;
        placementMap[x][y-2] = doMakeAvailable;
        placementMap[x+1][y-2] = doMakeAvailable;

        placementMap[x-1][y+2] = doMakeAvailable;
        placementMap[x][y+2] = doMakeAvailable;
        placementMap[x+1][y+2] = doMakeAvailable;
    }

    private static void change6x6(Point2d pos, boolean doMakeAvailable) { //x-3 -> x+2, y-4 -> y+2
        int x = (int) pos.getX();
        int y = (int) pos.getY();
        change4x4(pos, doMakeAvailable);

        placementMap[x-3][y+2] = doMakeAvailable;
        placementMap[x-2][y+2] = doMakeAvailable;
        placementMap[x-1][y+2] = doMakeAvailable;
        placementMap[x][y+2] = doMakeAvailable;
        placementMap[x+1][y+2] = doMakeAvailable;
        placementMap[x+2][y+2] = doMakeAvailable;

        placementMap[x-3][y+1] = doMakeAvailable;
        placementMap[x+2][y+1] = doMakeAvailable;

        placementMap[x-3][y] = doMakeAvailable;
        placementMap[x+2][y] = doMakeAvailable;

        placementMap[x-3][y-1] = doMakeAvailable;
        placementMap[x+2][y-1] = doMakeAvailable;

        placementMap[x-3][y-2] = doMakeAvailable;
        placementMap[x+2][y-2] = doMakeAvailable;

        placementMap[x-3][y-3] = doMakeAvailable;
        placementMap[x-2][y-3] = doMakeAvailable;
        placementMap[x-1][y-3] = doMakeAvailable;
        placementMap[x][y-3] = doMakeAvailable;
        placementMap[x+1][y-3] = doMakeAvailable;
        placementMap[x+2][y-3] = doMakeAvailable;
    }


    public static void makeAvailable2x1(Point2d pos) {
        change2x1(pos, true);
    }

    public static void makeUnavailable2x1(Point2d pos) {
        change2x1(pos, false);
    }

    public static void makeAvailable1x1(Point2d pos) {
        change1x1(pos, true);
    }

    public static void makeUnavailable1x1(Point2d pos) {
        change1x1(pos, false);
    }

    public static void makeAvailable2x2(Point2d pos) {
        change2x2(pos, true);
    }

    public static void makeUnavailable2x2(Point2d pos) {
        change2x2(pos, false);
    }

    public static void makeAvailable3x3(Point2d pos) {
        change3x3(pos, true);
    }

    public static void makeUnavailable3x3(Point2d pos) {
        change3x3(pos, false);
    }

    public static void makeAvailable4x4(Point2d pos) {
        change4x4(pos, true);
    }

    public static void makeUnavailable4x4(Point2d pos) {
        change4x4(pos, false);
    }

    public static void makeAvailable5x5(Point2d pos) {
        change5x5(pos, true);
    }

    public static void makeUnavailable5x5(Point2d pos) {
        change5x5(pos, false);
    }

    public static void makeAvailable6x6(Point2d pos) {
        change6x6(pos, true);
    }

    public static void makeUnavailable6x6(Point2d pos) {
        change6x6(pos, false);
    }

    public static void makeUnavailable(Unit structure) {
        switch (UnitUtils.getSize(structure)) {
            case _1x1:
                makeUnavailable1x1(structure.getPosition().toPoint2d());
                break;
            case _2x2:
                makeUnavailable2x2(structure.getPosition().toPoint2d());
                break;
            case _3x3:
                makeUnavailable3x3(structure.getPosition().toPoint2d());
                if (structure.getType() == Units.TERRAN_FACTORY || structure.getType() == Units.TERRAN_STARPORT) {
                    makeUnavailable2x2(getAddOnPos(structure.getPosition().toPoint2d()));
                }
                break;
            case _4x4:
                makeUnavailable4x4(structure.getPosition().toPoint2d());
                break;
            case _5x5:
                makeUnavailable5x5(structure.getPosition().toPoint2d());
                break;
            case _6x6:
                makeUnavailable6x6(structure.getPosition().toPoint2d());
                break;
        }
    }

    public static void makeUnavailable(Units structureType, Point2d pos) {
        switch (UnitUtils.getSize(structureType)) {
            case _1x1:
                makeUnavailable1x1(pos);
                break;
            case _2x2:
                makeUnavailable2x2(pos);
                break;
            case _3x3:
                makeUnavailable3x3(pos);
                if (structureType == Units.TERRAN_FACTORY || structureType == Units.TERRAN_STARPORT) {
                    makeUnavailable2x2(getAddOnPos(pos));
                }
                break;
            case _4x4:
                makeUnavailable4x4(pos);
                break;
            case _5x5:
                makeUnavailable5x5(pos);
                break;
            case _6x6:
                makeUnavailable6x6(pos);
                break;
        }
    }

    private static Point2d getAddOnPos(Point2d factoryPos) {
        return factoryPos.add(Point2d.of(2.5f, -0.5f));
    }

    public static void makeAvailable(Unit structure) {
        switch (UnitUtils.getSize(structure)) {
            case _1x1:
                makeAvailable1x1(structure.getPosition().toPoint2d());
                break;
            case _2x2:
                makeAvailable2x2(structure.getPosition().toPoint2d());
                break;
            case _3x3:
                makeAvailable3x3(structure.getPosition().toPoint2d());
                break;
            case _4x4:
                makeAvailable4x4(structure.getPosition().toPoint2d());
                break;
            case _5x5:
                makeAvailable5x5(structure.getPosition().toPoint2d());
                break;
            case _6x6:
                makeAvailable6x6(structure.getPosition().toPoint2d());
                break;
        }
    }

    public static void makeAvailable(Units structureType, Point2d pos) {
        switch (UnitUtils.getSize(structureType)) {
            case _1x1:
                makeAvailable1x1(pos);
                break;
            case _2x2:
                makeAvailable2x2(pos);
                break;
            case _3x3:
                makeAvailable3x3(pos);
                break;
            case _5x5:
                makeAvailable5x5(pos);
                break;
        }
    }

    /* shape:
        ..000....
        ..00000..
        ..00000..
        000000000
        .0000000.

     */
    public static boolean canFitPathingShape(Point2d placementPos, Point2d[] shapePoints) {
        boolean isCC = Arrays.equals(shapePoints, _5x5_SHAPE);
        for (Point2d vector : shapePoints) {
            if (!isPlaceable(placementPos.add(vector), isCC)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canFit3x3WithAddOn(Point2d _3x3Pos) {
        return canFit3x3(_3x3Pos.add(Point2d.of(-2, 0))) &&
                canFit3x3(_3x3Pos) &&
                canFit2x2(getAddOnPos(_3x3Pos));
    }

    public static boolean canFit1x1(Point2d pos) {
        return canFit1x1((int)pos.getX(), (int)pos.getY());
    }

    public static boolean canFit2x2(Point2d pos) {
        return canFit2x2((int)pos.getX(), (int)pos.getY());
    }

    public static boolean canFit3x3(Point2d pos) {
        return canFit3x3((int)pos.getX(), (int)pos.getY());
    }

    public static boolean canFit5x5(Point2d pos) {
        return canFit5x5((int)pos.getX(), (int)pos.getY());
    }

    public static boolean canFit1x1(int x, int y) {
        return placementMap[x][y];
    }

    public static boolean canFit2x2(int x, int y) {
        return placementMap[x-1][y] && placementMap[x][y] &&
                placementMap[x-1][y-1] && placementMap[x][y-1];
    }

    public static boolean canFit3x3(int x, int y) {
        return placementMap[x-1][y+1] && placementMap[x][y+1] && placementMap[x+1][y+1] &&
                placementMap[x-1][y] && canFit1x1(x, y) && placementMap[x][y] &&
                placementMap[x-1][y-1] && placementMap[x][y-1] && placementMap[x+1][y-1];
    }

    public static boolean canFit5x5(int x, int y) {
        return canFit3x3(x, y) &&
                placementMap[x-2][y+2] && placementMap[x-1][y+2] && placementMap[x][y+2] && placementMap[x+1][y+2] && placementMap[x+2][y+2] &&
                placementMap[x-2][y+1] && placementMap[x+2][y+1] &&
                placementMap[x-2][y] && placementMap[x+2][y] &&
                placementMap[x-2][y-1] && placementMap[x+2][y-1] &&
                placementMap[x-2][y-2] && placementMap[x-1][y-2] && placementMap[x][y-2] && placementMap[x+1][y-2] && placementMap[x+2][y-2];
    }

    //TODO: optimize for each base
    public static void create2CellColumns() {
        for (int x=(int)(LocationConstants.MIN_X + 0.5f); x<LocationConstants.MAX_X; x++) {
            for (int y=(int)(LocationConstants.MIN_Y + 0.5f); y<LocationConstants.MAX_Y; y++) {
                int columnSpacer = x % 7;
                if (columnSpacer == 0 || columnSpacer == 1) {
                    placementMap[x][y] = false;
                }
            }
        }
    }

    public static void visualize3x3WithAddOn(Point2d placementPos) {
        DebugHelper.drawBox(placementPos, Color.RED, 1.5f);
        DebugHelper.drawBox(getAddOnPos(placementPos), Color.RED, 1f);
    }

    public static void visualize3x3(Point2d placementPos) {
        DebugHelper.drawBox(placementPos, Color.BLUE, 1.45f);
    }

    public static void visualize5x5(Point2d placementPos) {
        DebugHelper.drawBox(placementPos, Color.BLUE, 2.45f);
    }

    public static void visualize2x2(Point2d placementPos) {
        DebugHelper.drawBox(placementPos, Color.TEAL, 0.95f);
    }

    public static int populateMainBase3x3WithAddonPos(int columnIndex, boolean isColumnSet) {
        int num3x3WithAddons = 0;
        for (int x=(int)(LocationConstants.MIN_X + 0.5f); x<LocationConstants.MAX_X; x++) {
            for (int y=(int)(LocationConstants.MAX_Y - 0.5f); y>LocationConstants.MIN_Y; y--) {
                Point2d pos = Point2d.of(x + 0.5f, y + 0.5f);
                if (x % 7 == columnIndex && InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, pos)) {
                    if (canFitPathingShape(pos, _3x3_WITH_ADDON_SHAPE)) {
                        num3x3WithAddons++;
                        makeUnavailable(Units.TERRAN_FACTORY, pos);
                        if (isColumnSet) {
                            DebugHelper.drawText(num3x3WithAddons + "", pos, Color.RED, 14);
                            if (LocationConstants.MAP.equals(MapNames.ICE_AND_CHROME506)) {
                                LocationConstants.FACTORIES.add(pos);
                            }
                        }
                    }
                }
            }
        }
        return num3x3WithAddons;
    }

    private static void replaceFactoriesWithCommandCenters() {
        for (int i=0; i<LocationConstants.FACTORIES.size(); i++) {
            Point2d factoryPos = LocationConstants.FACTORIES.get(i);
            makeAvailable(Units.TERRAN_FACTORY, factoryPos);
            Point2d ccPos = factoryPos.add(1, -1);
            if (canFitPathingShape(ccPos, _5x5_SHAPE)) {
                LocationConstants.MACRO_OCS.add(ccPos);
                LocationConstants.FACTORIES.remove(i--);
                visualize5x5(ccPos);
                makeUnavailable(Units.TERRAN_COMMAND_CENTER, ccPos);
            } else {
                visualize3x3WithAddOn(factoryPos);
                makeUnavailable(Units.TERRAN_FACTORY, factoryPos);
            }
        }
    }

    public static int populateMainBase3x3Pos(int columnIndex, boolean doVisualize) {
        int num3x3s = 0;
        for (int x=(int)(LocationConstants.MIN_X + 0.5f); x<LocationConstants.MAX_X; x++) {
            for (int y=(int)(LocationConstants.MAX_Y - 0.5f); y>LocationConstants.MIN_Y; y--) {
                Point2d pos = Point2d.of(x + 0.5f, y + 0.5f);
                if ((x % 7 == columnIndex || (x-1) % 7 == columnIndex || (x-2) % 7 == columnIndex) && InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, pos)) {
                    if (canFitPathingShape(pos, _3x3_SHAPE)) {
                        if (doVisualize) {
                            visualize3x3(pos);
                        }
                        if (LocationConstants.MAP.equals(MapNames.ICE_AND_CHROME506)) {
                            LocationConstants._3x3Structures.add(pos);
                        }
                        num3x3s++;
                        makeUnavailable(Units.TERRAN_ARMORY, pos);
                    }
                }
            }
        }
        return num3x3s;
    }

    public static void setColumn() {
        int bestColumnNumFactories = 0;
        for (int i=0; i<7; i++) {
            initializeMap(false);
            int numFactories = populateMainBase3x3WithAddonPos(i, false);
            if (numFactories > bestColumnNumFactories) {
                bestColumnNumFactories = numFactories;
                buildColumn = i;
            }
        }
        initializeMap(true);
    }

    public static void makeUnAvailableResourceNode(UnitInPool resource) {
        Point2d resourcePos = resource.unit().getPosition().toPoint2d();
        boolean isMineralPatch = UnitUtils.MINERAL_NODE_TYPE.contains(resource.unit().getType());
        Point2d[] ccBlockingShape = isMineralPatch ? MINERAL_CC_BLOCK_SHAPE : GAS_CC_BLOCK_SHAPE;

        //remove resource from placementGrid
        if (isMineralPatch) {
            makeUnavailable2x1(resourcePos);
        }
        else {
            makeUnavailable3x3(resourcePos);
        }

        //remove resource from ccPlacementGrid
        Arrays.stream(ccBlockingShape).forEach(shapeCoord -> {
            Point2d p = resourcePos.add(shapeCoord);
            ccPlacementMap[(int)p.getX()][(int)p.getY()] = false;
        });
    }

    public static void removeResourceNodeCCBlockers(UnitInPool resource) {
        Point2d resourcePos = resource.unit().getPosition().toPoint2d();
        Point2d[] resourceBlockPos = UnitUtils.GAS_GEYSER_TYPE.contains(resource.unit().getType()) ?
                GAS_CC_BLOCK_SHAPE :
                MINERAL_CC_BLOCK_SHAPE;
        Arrays.stream(resourceBlockPos).forEach(shapeCoord -> {
            Point2d p = resourcePos.add(shapeCoord);
            ccPlacementMap[(int)p.getX()][(int)p.getY()] = false;
        });
    }

}
