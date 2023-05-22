package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.gamestate.GameCache;
import com.ketroc.geometry.Position;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PlacementMap {
    public static boolean[][] placementMap = new boolean[400][400];
    public static boolean[][] ccPlacementMap = new boolean[400][400];
    //public static boolean[][] originalPlacementMap = new boolean[400][400];
    //public static boolean[][] pathingMap = new boolean[400][400];
    //public static float[][] chokeValueMap = new float[400][400];

    /* shape:
        ..XXX....
        ..XXXXX..
        00XXXXX..
        000000000
        000000000 */
    public static final Point2d[] _3x3_WITH_ADDON_SHAPE = new Point2d[] {
            Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1),
            Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0), Point2d.of(3, 0),
            Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1), Point2d.of(3, -1),
            Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2), Point2d.of(5, -2),
            Point2d.of(-3, -3), Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3), Point2d.of(5, -3)};
    /* shape:
        ..XXX..
        ..XXX..
        00XXX..
        0000000
        0000000 */
    public static final Point2d[] _3x3_SHAPE1 = new Point2d[] {
            Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1),
            Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0),
            Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1),
            Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2),
            Point2d.of(-3, -3), Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3)};
    /* shape:
        ...XXX..
        ...XXX..
        ...XXX..
        .0000000
        .0000000 */
    public static final Point2d[] _3x3_SHAPE2 = new Point2d[] {
            Point2d.of(0, 1), Point2d.of(1, 1), Point2d.of(2, 1),
            Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0),
            Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1),
            Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2),
            Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3)};
    /* shape:
        ....XXX..
        ....XXX..
        ....XXX..
        ..0000000
        ..0000000 */
    public static final Point2d[] _3x3_SHAPE3 = new Point2d[] {
            Point2d.of(1, 1), Point2d.of(2, 1), Point2d.of(3, 1),
            Point2d.of(1, 0), Point2d.of(2, 0), Point2d.of(3, 0),
            Point2d.of(1, -1), Point2d.of(2, -1), Point2d.of(3, -1),
            Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2), Point2d.of(3, -2), Point2d.of(4, -2), Point2d.of(5, -2),
            Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3), Point2d.of(5, -3)};
    /* shape:
        ..XXXXX..
        ..XXXXX..
        00XXXXX..
        00XXXXX..
        00XXXXX..
        000000000
        .0000000. */
    public static final Point2d[] _5x5_SHAPE = new Point2d[] {
            Point2d.of(-2, 2), Point2d.of(-1, 2), Point2d.of(0, 2), Point2d.of(1, 2), Point2d.of(2, 2),
            Point2d.of(-3, 1), Point2d.of(-2, 1), Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1), Point2d.of(2, 1),
            Point2d.of(-4, 0), Point2d.of(-3, 0), Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0),
            Point2d.of(-4, -1), Point2d.of(-3, -1), Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1),
            Point2d.of(-4, -2), Point2d.of(-3, -2), Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2),
            Point2d.of(-4, -3), Point2d.of(-3, -3), Point2d.of(-2, -3), Point2d.of(-1, -3), Point2d.of(0, -3), Point2d.of(1, -3), Point2d.of(2, -3), Point2d.of(3, -3), Point2d.of(4, -3),
            Point2d.of(-3, -4), Point2d.of(-2, -4), Point2d.of(-1, -4), Point2d.of(0, -4), Point2d.of(1, -4), Point2d.of(2, -4), Point2d.of(3, -4)};
    /* shape:
        ..XXXXX
        ..XXXXX
        ..XXXXX
        ..XXXXX
        ..XXXXX  */
    public static final Point2d[] _5x5 = new Point2d[] {
            Point2d.of(-2, 2), Point2d.of(-1, 2), Point2d.of(0, 2), Point2d.of(1, 2), Point2d.of(2, 2),
            Point2d.of(-2, 1), Point2d.of(-1, 1), Point2d.of(0, 1), Point2d.of(1, 1), Point2d.of(2, 1),
            Point2d.of(-2, 0), Point2d.of(-1, 0), Point2d.of(0, 0), Point2d.of(1, 0), Point2d.of(2, 0),
            Point2d.of(-2, -1), Point2d.of(-1, -1), Point2d.of(0, -1), Point2d.of(1, -1), Point2d.of(2, -1),
            Point2d.of(-2, -2), Point2d.of(-1, -2), Point2d.of(0, -2), Point2d.of(1, -2), Point2d.of(2, -2)};
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

    public static void onGameStart() {
        initializeMap(false);
        int mainBaseColumn = getColumnMainBase();
        int secondaryColumn = getColumnSecondary();
        populateMainBase3x3WithAddonPos(mainBaseColumn, true);

        replace1StarportWith1CommandCenter();

        replace3StarportsWith2Ccs();

        PosConstants._3x3AddonPosList.sort(Comparator.comparing(p -> 1000 - p.distance(PosConstants.myRampPos)));
        populateMainBase3x3Pos(mainBaseColumn, true);
        if (PosConstants.MACRO_OCS.isEmpty()) {
            replace2StarportsWith1Cc();
        }
        populateDepotPos(mainBaseColumn);
        topUp3x3List();

        populateSecondary3x3WithAddonPos(secondaryColumn, true);
        replace1StarportWith1CommandCenter();
        replace3StarportsWith2Ccs();

        List<Point2d> ccsOutsideMain = PosConstants.MACRO_OCS.stream()
                .filter(pos -> !UnitUtils.isInMyMain(pos))
                .sorted(Comparator.comparing(pos -> 1000 - pos.distance(PosConstants.baseLocations.get(1))))
                .collect(Collectors.toList());
        ccsOutsideMain.forEach(ccPos -> {
            PosConstants.MACRO_OCS.remove(ccPos);
            makeAvailable(Units.TERRAN_COMMAND_CENTER, ccPos);
            PosConstants.exposedMacroOcList.add(0, ccPos);
        });
    }

    private static void populateDepotPos(int mainBaseColumn) {
        List<Point2d> depotPosList = new ArrayList<>();
        for (int x = (int)(PosConstants.MAX_X - 0.5f); x> PosConstants.MIN_X; x--) {
            for (int y = (int)(PosConstants.MIN_Y + 0.5f); y< PosConstants.MAX_Y; y++) {
                Point2d pos = Point2d.of(x, y);
                if ((x+2) % 7 == mainBaseColumn && InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, pos)) {
                    if (canFit2x2(pos) && (pos.distance(Bot.OBS.getStartLocation().toPoint2d()) > 6.5f || !isNodeNearby(pos))) {
                        makeUnavailable(Units.TERRAN_SUPPLY_DEPOT, pos);
                        depotPosList.add(pos);
                    }
                }
            }
        }
        depotPosList.sort(Comparator.comparing(point2d -> point2d.distance(PosConstants.getBackCorner())));
        PosConstants.extraDepots.addAll(depotPosList);
    }

    private static boolean isNodeNearby(Point2d pos) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, u ->
                (UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()) || UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType())) &&
                UnitUtils.getDistance(u.unit(), pos) < 5).size() > 0;
    }

    private static void topUp3x3List() {
// turned off because fast wall off is important
//        if (LocationConstants._3x3Structures.size() < 4 &&
//                !LocationConstants._3x3Structures.contains(LocationConstants.MID_WALL_3x3)) {
//            LocationConstants.extraDepots.remove(LocationConstants.MID_WALL_2x2);
//            LocationConstants._3x3Structures.add(1, LocationConstants.MID_WALL_3x3);
//        }
        while (PosConstants._3x3Structures.size() < 4) {
            Point2d lastStarportPos = PosConstants._3x3AddonPosList.remove(PosConstants._3x3AddonPosList.size() - 1);
            //makeAvailable(Units.TERRAN_TECHLAB, getAddOnPos(lastStarportPos));
            PosConstants._3x3Structures.add(lastStarportPos);
            PosConstants.extraDepots.add(getAddOnPos(lastStarportPos));
        }
    }

    private static void replace2StarportsWith1Cc() {
        //replaces 2 stacked starports with 1 command center
        for (int i = PosConstants._3x3AddonPosList.size() - 1; i >= 0; i--) {
            Point2d starportPos = PosConstants._3x3AddonPosList.get(i);
            Point2d aboveStarportPos = starportPos.add(0, 3);
            if (PosConstants._3x3AddonPosList.contains(aboveStarportPos)) {
                PosConstants._3x3AddonPosList.remove(starportPos);
                PosConstants._3x3AddonPosList.remove(aboveStarportPos);
                makeAvailable(Units.TERRAN_STARPORT, starportPos);
                makeAvailable(Units.TERRAN_TECHLAB, starportPos.add(2.5f, -0.5f));
                makeAvailable(Units.TERRAN_STARPORT, aboveStarportPos);
                makeAvailable(Units.TERRAN_TECHLAB, aboveStarportPos.add(2.5f, -0.5f));
                Point2d ccPos = starportPos.add(1, 1);
                PosConstants.addMacroOcPos(ccPos);
                makeUnavailable(Units.TERRAN_COMMAND_CENTER, ccPos);
                return;
            }
        }
    }

    public static void initializeMap(boolean isColumnSet) {
        for (int x = PosConstants.MIN_X; x<PosConstants.MAX_X; x++) {
            for (int y = PosConstants.MIN_Y; y<PosConstants.MAX_Y; y++) {
                placementMap[x][y] = Bot.OBS.isPlacable(Point2d.of(x+0.5f, y+0.5f));
                ccPlacementMap[x][y] = Bot.OBS.isPlacable(Point2d.of(x+0.5f, y+0.5f));
                //originalPlacementMap[x][y] = Bot.OBS.isPlacable(Point2d.of(x+0.5f, y+0.5f));
                //pathingMap[x][y] = Bot.OBS.isPathable(Point2d.of(x+0.5f, y+0.5f));
            }
        }

        //TODO: for testing
//        DecimalFormat df = new DecimalFormat("0.0");
//        for (int x = PosConstants.MIN_X; x<PosConstants.MAX_X; x++) {
//            for (int y = PosConstants.MIN_Y; y<PosConstants.MAX_Y; y++) {
//                if (pathingMap[x][y]) {
//                    Point2d thisPos = Point2d.of(x+0.5f, y+0.5f);
//                    chokeValueMap[x][y] = Position.getSortedNearbyPlacements(thisPos, 10).stream()
//                            .filter(p -> !Bot.OBS.isPathable(p))
//                            .findFirst()
//                            .map(p -> Float.parseFloat(df.format(p.distance(thisPos))))
//                            .orElse(20.0f);
//                }
//            }
//        }

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

        //remove all base CC positions from to placement grid
        PosConstants.baseLocations.forEach(ccPos -> makeUnavailable5x5(ccPos));

        //remove expansion base turrets TODO: can't work cuz order must be: build placement map, then Base.onGameStart, then this code
//        GameCache.baseList.stream()
//                .flatMap(base -> base.getInFrontPositions().stream())
//                .forEach(p -> makeUnavailable2x2(p.getPos()));

        //visualize initial placementMap without structure positions removed
        DebugHelper.drawBox(PosConstants.myRampPos, Color.RED, 0.3f);
        calcRampWallPos();

        //remove ramp wall
        makeUnavailable2x2(PosConstants.WALL_2x2);
        makeUnavailable3x3(PosConstants.WALL_3x3);
        makeUnavailable3x3(PosConstants.MID_WALL_3x3);

        //make space around ramp entrance
        makeUnavailable3x3(PosConstants.MID_WALL_3x3.add(-2, -2));
        makeUnavailable3x3(PosConstants.MID_WALL_3x3.add(0, -2));
        //DebugHelper.drawBox(LocationConstants.MID_WALL_3x3, Color.GRAY, 3.5f);

        //remove reaper jump wall
        if (PosConstants.opponentRace == Race.TERRAN && Strategy.gamePlan != GamePlan.MARINE_RUSH) {
            PosConstants.reaperBlockDepots.forEach(p -> makeUnavailable2x2(p));
            PosConstants.reaperBlock3x3s.forEach(p -> makeUnavailable3x3(p));
        }

        //remove nat wall
        if (Strategy.DO_WALL_NAT) {
            PosConstants.natWallDepots.forEach(p -> makeUnavailable2x2(p));
            PosConstants.natWall3x3s.forEach(p -> makeUnavailable3x3(p));
        }
    }

    public static void visualStructureListOrder(List<Point2d> structureList, Color color) {
        for (int i=0; i<structureList.size(); i++) {
            DebugHelper.drawText((i) + "", structureList.get(i), color);
        }
    }

    public static void visualizePlacementMap() {
        for (float x = PosConstants.MIN_X + 0.5f; x< PosConstants.MAX_X; x++) {
            for (float y = PosConstants.MIN_Y + 0.5f; y< PosConstants.MAX_Y; y++) {
                if (placementMap[(int)x][(int)y]) {
                    DebugHelper.drawBox(Point2d.of(x, y), Color.TEAL, 0.4f);
                    //DebugHelper.drawText((int)x + "," + (int)y, Point2d.of(x-0.4f, y), Color.TEAL, 8);
                }
            }
        }
    }

//    public static void visualizePathingMap() {
//        for (float x = PosConstants.MIN_X + 0.5f; x< PosConstants.MAX_X; x++) {
//            for (float y = PosConstants.MIN_Y + 0.5f; y< PosConstants.MAX_Y; y++) {
//                if (pathingMap[(int)x][(int)y]) {
//                    DebugHelper.drawBox(Point2d.of(x, y), Color.TEAL, 0.4f);
//                    //DebugHelper.drawText((int)x + "," + (int)y, Point2d.of(x-0.4f, y), Color.TEAL, 8);
//                }
//            }
//        }
//    }

//    public static void visualizeChokeValueMap() {
//        for (int x = PosConstants.MIN_X; x<PosConstants.MAX_X; x++) {
//            for (int y = PosConstants.MIN_Y; y<PosConstants.MAX_Y; y++) {
//                if (pathingMap[x][y]) {
//                    //DebugHelper.drawBox(Point2d.of(x, y), Color.TEAL, 0.4f);
//                    DebugHelper.drawText(String.valueOf(chokeValueMap[x][y]), Point2d.of(x+0.2f, y+0.5f), Color.RED, 11);
//                }
//            }
//        }
//    }

    public static void visualizeCcPlacementMap() {
        for (float x = PosConstants.MIN_X + 0.5f; x< PosConstants.MAX_X; x++) {
            for (float y = PosConstants.MIN_Y + 0.5f; y< PosConstants.MAX_Y; y++) {
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

    private static Point2d getAddOnPos(Point2d starportPos) {
        return starportPos.add(Point2d.of(2.5f, -0.5f));
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

    public static boolean canFitPathingShape(Point2d placementPos, Point2d[] shapePoints) {
        for (Point2d vector : shapePoints) {
            if (!isPlaceable(placementPos.add(vector), false)) {
                return false;
            }
        }
        if (Arrays.equals(shapePoints, _5x5_SHAPE)) { //check CC isn't too slow to mining node
            for (Point2d vector : _5x5) {
                if (!isPlaceable(placementPos.add(vector), true)) {
                    return false;
                }
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
                placementMap[x-1][y] && placementMap[x][y] && placementMap[x][y] &&
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
        for (int x = (int)(PosConstants.MIN_X + 0.5f); x< PosConstants.MAX_X; x++) {
            for (int y = (int)(PosConstants.MIN_Y + 0.5f); y< PosConstants.MAX_Y; y++) {
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
        DebugHelper.drawBox(placementPos, Color.YELLOW, 2.45f);
    }

    public static void visualize2x2(Point2d placementPos) {
        DebugHelper.drawBox(placementPos, Color.TEAL, 0.95f);
    }

    public static int populateSecondary3x3WithAddonPos(int columnIndex, boolean isColumnSet) {
        return populateArea3x3WithAddonPos(InfluenceMaps.pointIn2ndProductionArea, columnIndex, isColumnSet);
    }
    public static int populateMainBase3x3WithAddonPos(int columnIndex, boolean isColumnSet) {
        return populateArea3x3WithAddonPos(InfluenceMaps.pointInMainBase, columnIndex, isColumnSet);
    }
    public static int populateArea3x3WithAddonPos(boolean[][] map, int columnIndex, boolean isColumnSet) {
        int num3x3WithAddons = 0;
        List<Point2d> _3x3PosList = new ArrayList<>();
        for (int x = (int)(PosConstants.MIN_X + 0.5f); x< PosConstants.MAX_X; x++) {
            for (int y = (int)(PosConstants.MAX_Y - 0.5f); y> PosConstants.MIN_Y; y--) {
                Point2d pos = Point2d.of(x + 0.5f, y + 0.5f);
                if (x % 7 == columnIndex && InfluenceMaps.getValue(map, pos)) {
                    if (canFitPathingShape(pos, _3x3_WITH_ADDON_SHAPE)) {
                        num3x3WithAddons++;
                        makeUnavailable(Units.TERRAN_STARPORT, pos);
                        if (isColumnSet) {
                            _3x3PosList.add(pos);
                        }
                    }
                }
            }
        }
        if (map == InfluenceMaps.pointIn2ndProductionArea) {
            _3x3PosList.sort(Comparator.comparing(point2d -> point2d.distance(PosConstants.baseLocations.get(1))));
        }
        PosConstants._3x3AddonPosList.addAll(_3x3PosList);
        return num3x3WithAddons;
    }

    private static void replace3StarportsWith2Ccs() {
        //exit because: not enough starports for check to pass
        if (PosConstants._3x3AddonPosList.size() < 3) {
            return;
        }

        for (int i = PosConstants._3x3AddonPosList.size() - 1; i >= 2; i--) {
            Point2d botStarportPos = PosConstants._3x3AddonPosList.get(i);
            Point2d midStarportPos = botStarportPos.add(0, 3);
            Point2d topStarportPos = botStarportPos.add(0, 6);

            //skip because: there aren't 3 stacked starports
            if (!midStarportPos.equals(PosConstants._3x3AddonPosList.get(i - 1)) ||
                    !topStarportPos.equals(PosConstants._3x3AddonPosList.get(i - 2))) {
                continue;
            }

            //remove bottom 3 starports for testing 2 CCs instead
            makeAvailable(Units.TERRAN_STARPORT, botStarportPos);
            makeAvailable(Units.TERRAN_TECHLAB, botStarportPos.add(2.5f, -0.5f));
            makeAvailable(Units.TERRAN_STARPORT, midStarportPos);
            makeAvailable(Units.TERRAN_TECHLAB, midStarportPos.add(2.5f, -0.5f));
            makeAvailable(Units.TERRAN_STARPORT, topStarportPos);
            makeAvailable(Units.TERRAN_TECHLAB, topStarportPos.add(2.5f, -0.5f));

            //skip because: 2 CCs don't fit
            Point2d topCcPos = topStarportPos.add(1, -1);
            Point2d botCcPos = topCcPos.add(0, -5);
            if (!canFitPathingShape(topCcPos, _5x5_SHAPE) || !canFitPathingShape(botCcPos, _5x5_SHAPE)) {
                makeUnavailable(Units.TERRAN_STARPORT, botStarportPos);
                makeUnavailable(Units.TERRAN_TECHLAB, botStarportPos.add(2.5f, -0.5f));
                makeUnavailable(Units.TERRAN_STARPORT, midStarportPos);
                makeUnavailable(Units.TERRAN_TECHLAB, midStarportPos.add(2.5f, -0.5f));
                makeUnavailable(Units.TERRAN_STARPORT, topStarportPos);
                makeUnavailable(Units.TERRAN_TECHLAB, topStarportPos.add(2.5f, -0.5f));
                continue;
            }

            //remove the 3 starports for 2 CCs
            PosConstants._3x3AddonPosList.remove(botStarportPos);
            PosConstants._3x3AddonPosList.remove(midStarportPos);
            PosConstants._3x3AddonPosList.remove(topStarportPos);
            PosConstants.addMacroOcPos(topCcPos);
            PosConstants.addMacroOcPos(botCcPos);
            makeUnavailable(Units.TERRAN_COMMAND_CENTER, topCcPos);
            makeUnavailable(Units.TERRAN_COMMAND_CENTER, botCcPos);
            i -= 2;

            //exit if starport:cc ratio is good enough
            if (PosConstants._3x3AddonPosList.size() < 20 &&
                    PosConstants.MACRO_OCS.size()*2 + 3 >= PosConstants._3x3AddonPosList.size()) {
                return;
            }
        }
    }

    private static void replace1StarportWith1CommandCenter() {
        List<Point2d> _5x5PosList = new ArrayList<>();
        for (int i = 0; i< PosConstants._3x3AddonPosList.size(); i++) {
            Point2d starportPos = PosConstants._3x3AddonPosList.get(i);
            makeAvailable(Units.TERRAN_STARPORT, starportPos);
            makeAvailable(Units.TERRAN_TECHLAB, starportPos.add(2.5f, -0.5f));
            Point2d ccPos = starportPos.add(1, -1);
            if (canFitPathingShape(ccPos, _5x5_SHAPE)) {
                _5x5PosList.add(ccPos);
                PosConstants._3x3AddonPosList.remove(i--);
                makeUnavailable(Units.TERRAN_COMMAND_CENTER, ccPos);
            } else {
                makeUnavailable(Units.TERRAN_STARPORT, starportPos);
            }
        }
        _5x5PosList.sort(Comparator.comparing(point2d -> point2d.distance(PosConstants.getBackCorner())));
        PosConstants.MACRO_OCS.addAll(_5x5PosList);
    }

    public static int populateMainBase3x3Pos(int columnIndex, boolean doVisualize) {
        int num3x3s = 0;
        for (int x = (int)(PosConstants.MIN_X + 0.5f); x< PosConstants.MAX_X; x++) {
            for (int y = (int)(PosConstants.MAX_Y - 0.5f); y> PosConstants.MIN_Y; y--) {
                Point2d pos = Point2d.of(x + 0.5f, y + 0.5f);
                if (x % 7 == columnIndex && InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, pos)) {
                    if (canFitPathingShape(pos, _3x3_SHAPE1)) {
                        if (doVisualize) {
                            visualize3x3(pos);
                        }
                        PosConstants._3x3Structures.add(pos);
                        num3x3s++;
                        makeUnavailable(Units.TERRAN_ARMORY, pos);
                    } else if (canFitPathingShape(pos, _3x3_SHAPE2)) {
                        pos = pos.add(1, 0);
                        if (doVisualize) {
                            visualize3x3(pos);
                        }
                        PosConstants._3x3Structures.add(pos);
                        num3x3s++;
                        makeUnavailable(Units.TERRAN_ARMORY, pos);
                    } else if (canFitPathingShape(pos, _3x3_SHAPE3)) {
                        pos = pos.add(2, 0);
                        if (doVisualize) {
                            visualize3x3(pos);
                        }
                        PosConstants._3x3Structures.add(pos);
                        num3x3s++;
                        makeUnavailable(Units.TERRAN_ARMORY, pos);
                    }
                }
            }
        }
        return num3x3s;
    }

    public static int getColumnSecondary() {
        return getColumn(InfluenceMaps.pointIn2ndProductionArea);
    }
    public static int getColumnMainBase() {
        return getColumn(InfluenceMaps.pointInMainBase);
    }
    public static int getColumn(boolean[][] map) {
        int bestColumnNumStarports = 0;
        int bestColumn = 0;
        for (int i=0; i<7; i++) {
            initializeMap(false);
            int numStarports = populateArea3x3WithAddonPos(map, i, false);
            if (numStarports > bestColumnNumStarports) {
                bestColumnNumStarports = numStarports;
                bestColumn = i;
            }
        }
        initializeMap(true);
        return bestColumn;
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

    public static void calcRampWallPos() {
        if (PosConstants.WALL_3x3 != null) {
            return;
        }
        List<Point2d> rampPosList = getHighGroundRampBorderPosList();
        //hack for terrain height tiles on NE-to-SW shaped main ramps
        if (rampPosList.size() == 3) {
            rampPosList.add(
                    rampPosList.stream()
                        .min(Comparator.comparing(p -> p.getX()))
                        .get()
                        .add(-1, 0)
            );
            rampPosList.add(
                    rampPosList.stream()
                        .min(Comparator.comparing(p -> p.getY()))
                        .get()
                        .add(0, -1)
            );
        }

        //far point - furthest pos from my spawn pos
        Point2d farPos = rampPosList.stream()
                .max(Comparator.comparing(p -> p.distance(GameCache.baseList.get(0).getCcPos())))
                .get();
        //closest point - furthest pos from far point
        Point2d closePos = rampPosList.stream()
                .max(Comparator.comparing(p -> p.distance(farPos)))
                .get();

        //center point - closest pos to average of far and close point
        Point2d midPos = rampPosList.stream()
                .min(Comparator.comparing(p -> p.distance(farPos.add(closePos).div(2))))
                .get();

        PosConstants.WALL_3x3 = get3x3Pos(farPos, midPos);
        PosConstants.WALL_2x2 = get2x2Pos(closePos, midPos);
        PosConstants.MID_WALL_2x2 = getMid2x2Pos(closePos, midPos);
        PosConstants.MID_WALL_3x3 = getMid3x3Pos(closePos, midPos);
        PosConstants._3x3Structures.add(PosConstants.MID_WALL_3x3);
        PosConstants._3x3Structures.add(PosConstants.WALL_3x3);
        PosConstants.extraDepots.add(PosConstants.WALL_2x2);
        PosConstants.extraDepots.add(PosConstants.MID_WALL_2x2);
        PosConstants.insideMainWall = Position.towards(
                Position.towards(PosConstants.MID_WALL_3x3, PosConstants.myRampPos, -1f),
                PosConstants.mainBaseMidPos,
                2f
        );
    }

    private static Point2d get3x3Pos(Point2d farPos, Point2d midPos) {
        int xDist = (int)Math.abs(farPos.getX() - midPos.getX());
        int yDist = (int)Math.abs(farPos.getY() - midPos.getY());

        int xMove;
        int yMove;
        if (xDist > yDist) {
            xMove = (farPos.getX() > midPos.getX()) ? -1 : 1;
            yMove = (farPos.getY() > midPos.getY()) ? 1 : -1;
        }
        else {
            xMove = (farPos.getX() > midPos.getX()) ? 1 : -1;
            yMove = (farPos.getY() > midPos.getY()) ? -1 : 1;
        }
        return farPos.add(xMove, yMove);
    }

    private static Point2d get2x2Pos(Point2d closePos, Point2d midPos) {
        int xDist = (int)Math.abs(closePos.getX() - midPos.getX());
        int yDist = (int)Math.abs(closePos.getY() - midPos.getY());

        float xMove;
        float yMove;
        if (xDist > yDist) {
            xMove = (closePos.getX() > midPos.getX()) ? -0.5f : 0.5f;
            yMove = (closePos.getY() > midPos.getY()) ? 0.5f : -0.5f;
        }
        else {
            xMove = (closePos.getX() > midPos.getX()) ? 0.5f : -0.5f;
            yMove = (closePos.getY() > midPos.getY()) ? -0.5f : 0.5f;
        }
        return closePos.add(xMove, yMove);
    }

    private static Point2d getMid2x2Pos(Point2d closePos, Point2d midPos) {
        int xDist = (int)Math.abs(closePos.getX() - midPos.getX());
        int yDist = (int)Math.abs(closePos.getY() - midPos.getY());

        float xMove;
        float yMove;
        if (xDist > yDist) {
            xMove = (closePos.getX() > midPos.getX()) ? -2.5f : 2.5f;
            yMove = (closePos.getY() > midPos.getY()) ? 0.5f : -0.5f;
        }
        else {
            xMove = (closePos.getX() > midPos.getX()) ? 0.5f : -0.5f;
            yMove = (closePos.getY() > midPos.getY()) ? -2.5f : 2.5f;
        }
        return closePos.add(xMove, yMove);
    }

    private static Point2d getMid3x3Pos(Point2d closePos, Point2d midPos) {
        int xDist = (int)Math.abs(closePos.getX() - midPos.getX());
        int yDist = (int)Math.abs(closePos.getY() - midPos.getY());

        float xMove;
        float yMove;
        if (xDist > yDist) {
            xMove = (closePos.getX() > midPos.getX()) ? -3f : 3f;
            yMove = (closePos.getY() > midPos.getY()) ? 1f : -1f;
        }
        else {
            xMove = (closePos.getX() > midPos.getX()) ? 1f : -1f;
            yMove = (closePos.getY() > midPos.getY()) ? -3f : 3f;
        }
        return closePos.add(xMove, yMove);
    }

    private static List<Point2d> getHighGroundRampBorderPosList() {
        float rampHeight = Bot.OBS.terrainHeight(PosConstants.myRampPos);
        List<Point2d> rampAreaPosList = Position.getPosListGrid(PosConstants.myRampPos, 4);
        return rampAreaPosList.stream()
                .filter(p -> Bot.OBS.terrainHeight(p) > rampHeight)
                .filter(p -> Bot.OBS.isPlacable(p))
                .filter(p -> isNextToARampPos(p))
                .collect(Collectors.toList());
    }

    private static boolean isNextToARampPos(Point2d pos) {
        List<Point2d> surroundingPosList = new ArrayList<>();
        surroundingPosList.add(pos.add(0, 1));
        surroundingPosList.add(pos.add(1, 0));
        surroundingPosList.add(pos.add(0, -1));
        surroundingPosList.add(pos.add(-1, 0));

        return surroundingPosList.stream()
                .anyMatch(p -> Bot.OBS.isPathable(p) && !Bot.OBS.isPlacable(p));
    }

}
