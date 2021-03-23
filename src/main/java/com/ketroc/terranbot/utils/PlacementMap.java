package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.TestingBot;
import com.ketroc.terranbot.managers.StructureSize;

public class PlacementMap {
    public static boolean[][] placementMap = new boolean[400][400];
    public static final Point2d[] factoryAndPathingShape = new Point2d[] {
            Point2d.of(-3, 1), Point2d.of(-2, 1), Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1),
            Point2d.of(-3, -0), Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0), Point2d.of(3, 0),
            Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1), Point2d.of(3, -1),
            Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2), Point2d.of(5, -2),
            Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3)};

    public static int buildColumn;


    public static void onGameStart() {
        initializeMap();
        //create2CellColumns();
    }

    public static void initializeMap() {
        for (float x=LocationConstants.MIN_X + 0.5f; x<LocationConstants.MAX_X; x++) {
            for (float y=LocationConstants.MIN_Y + 0.5f; y<LocationConstants.MAX_Y; y++) {
                placementMap[(int)x][(int)y] = Bot.OBS.isPlacable(Point2d.of(x, y));
            }
        }

        //TODO: DELETE below - for testing
        //add all mineral patches to the placement grid
        Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()))
                .forEach(mineral -> makeUnavailable2x1(mineral.unit().getPosition().toPoint2d()));

        //add all vespene geysers to the placement grid
        Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType()))
                .forEach(mineral -> makeUnavailable3x3(mineral.unit().getPosition().toPoint2d()));

        //add start CC to placement grid
        makeUnavailable5x5(Bot.OBS.getStartLocation().toPoint2d());

    }

    public static void visualizePlacementMap() {
        for (float x=LocationConstants.MIN_X + 0.5f; x<LocationConstants.MAX_X; x++) {
            for (float y=LocationConstants.MIN_Y + 0.5f; y<LocationConstants.MAX_Y; y++) {
                if (placementMap[(int)x][(int)y]) {
                    DebugHelper.drawBox(Point2d.of(x, y), Color.TEAL, 0.4f);
                }
            }
        }
    }

    public static boolean isPlaceable(Point2d pos) {
        return placementMap[(int)pos.getX()][(int)pos.getY()];
    }

    public static void change2x1(Point2d pos, boolean doMakeAvailable) {
        placementMap[(int)pos.getX() - 1][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY()] = doMakeAvailable;
    }

    public static void change1x1(Point2d pos, boolean doMakeAvailable) {
        placementMap[(int)pos.getX()][(int)pos.getY()] = doMakeAvailable;
    }

    private static void change2x2(Point2d pos, boolean doMakeAvailable) {
        placementMap[(int)pos.getX() - 1][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX() - 1][(int)pos.getY()-1] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY()-1] = doMakeAvailable;

    }

    private static void change3x3(Point2d pos, boolean doMakeAvailable) {
        change1x1(pos, doMakeAvailable);
        placementMap[(int)pos.getX() - 1][(int)pos.getY() - 1] = doMakeAvailable;
        placementMap[(int)pos.getX() - 1][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX() - 1][(int)pos.getY() + 1] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY() - 1] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY() + 1] = doMakeAvailable;
        placementMap[(int)pos.getX() + 1][(int)pos.getY() - 1] = doMakeAvailable;
        placementMap[(int)pos.getX() + 1][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX() + 1][(int)pos.getY() + 1] = doMakeAvailable;
    }

    private static void change5x5(Point2d pos, boolean doMakeAvailable) {
        change3x3(pos, doMakeAvailable);
        placementMap[(int)pos.getX() - 2][(int)pos.getY() - 2] = doMakeAvailable;
        placementMap[(int)pos.getX() - 2][(int)pos.getY() - 1] = doMakeAvailable;
        placementMap[(int)pos.getX() - 2][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX() - 2][(int)pos.getY() + 1] = doMakeAvailable;
        placementMap[(int)pos.getX() - 2][(int)pos.getY() + 2] = doMakeAvailable;

        placementMap[(int)pos.getX() + 2][(int)pos.getY() - 2] = doMakeAvailable;
        placementMap[(int)pos.getX() + 2][(int)pos.getY() - 1] = doMakeAvailable;
        placementMap[(int)pos.getX() + 2][(int)pos.getY()] = doMakeAvailable;
        placementMap[(int)pos.getX() + 2][(int)pos.getY() + 1] = doMakeAvailable;
        placementMap[(int)pos.getX() + 2][(int)pos.getY() + 2] = doMakeAvailable;

        placementMap[(int)pos.getX() - 1][(int)pos.getY() - 2] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY() - 2] = doMakeAvailable;
        placementMap[(int)pos.getX() + 1][(int)pos.getY() - 2] = doMakeAvailable;

        placementMap[(int)pos.getX() - 1][(int)pos.getY() + 2] = doMakeAvailable;
        placementMap[(int)pos.getX()][(int)pos.getY() + 2] = doMakeAvailable;
        placementMap[(int)pos.getX() + 1][(int)pos.getY() + 2] = doMakeAvailable;
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

    public static void makeAvailable5x5(Point2d pos) {
        change5x5(pos, true);
    }

    public static void makeUnavailable5x5(Point2d pos) {
        change5x5(pos, false);
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
            case _5x5:
                makeUnavailable5x5(pos);
                break;
        }
    }

    private static Point2d getAddOnPos(Point2d factoryPos) {
        return factoryPos.add(Point2d.of(2.5f, -0.5f));
    }

    public static void makeAvailable(Unit structure) {
        makeAvailable((Units)structure.getType(), structure.getPosition().toPoint2d());
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
    public static boolean canFitFactoryAndPathingShape(Point2d factoryPos) {
        for (Point2d vector : factoryAndPathingShape) {
            if (!isPlaceable(factoryPos.add(vector))) {
                return false;
            }
        }
        return true;
    }

    public static boolean canFitFactoryAndAddOn(Point2d factoryPos) {
        return canFit3x3(factoryPos.add(Point2d.of(-2, 0))) &&
                canFit3x3(factoryPos) &&
                canFit2x2(getAddOnPos(factoryPos));
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

    public static void visualizeFactoryAndAddOn(Point2d factoryPos) {
        DebugHelper.drawBox(factoryPos, Color.GREEN, 1.5f);
        DebugHelper.drawBox(getAddOnPos(factoryPos), Color.GREEN, 1f);
    }

    //TODO: delete - for testing
    public static int populateMainBaseFactoryPos(int columnIndex, boolean doVisualize) {
        int numFactories = 0;
        for (int x=(int)(LocationConstants.MIN_X + 0.5f); x<LocationConstants.MAX_X; x++) {
            for (int y=(int)(LocationConstants.MAX_Y - 0.5f); y>LocationConstants.MIN_Y; y--) {
                Point2d pos = Point2d.of(x + 0.5f, y + 0.5f);
                if (x % 7 == columnIndex && InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, pos)) {
                    if ((canFitFactoryAndAddOn(pos) && canFitFactoryAndAddOn(pos.add(Point2d.of(0, -3)))) ||
                            canFitFactoryAndPathingShape(pos)) {
                        if (doVisualize) {
                            visualizeFactoryAndAddOn(pos);
                        }
                        numFactories++;
                        makeUnavailable(Units.TERRAN_FACTORY, pos);
                    }
                }
            }
        }
        return numFactories;
    }

    public static void setColumn() {
        int bestColumnNumFactories = 0;
        for (int i=0; i<7; i++) {
            initializeMap();
            int numFactories = populateMainBaseFactoryPos(i, false);
            if (numFactories > bestColumnNumFactories) {
                bestColumnNumFactories = numFactories;
                buildColumn = i;
            }
        }
        initializeMap();
        populateMainBaseFactoryPos(buildColumn, true);
    }
}
