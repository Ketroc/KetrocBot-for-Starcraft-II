package com.ketroc.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.micro.Cyclone;
import com.ketroc.utils.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TestingBot extends Bot {
    public static UnitInPool bunker;
    public static UnitInPool marine;
    public static final int NEUTRAL_PLAYER_ID = 11;
    public Point2d mySpawnPos;
    public Point2d enemySpawnPos;
    public List<Point2d> possibleCcPosList;
    public UnitInPool mainCC;
    public Cyclone cyclone;

    public TestingBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    @Override
    public void onGameStart() {
        super.onGameStart();
        mySpawnPos = OBS.getStartLocation().toPoint2d();
        enemySpawnPos = OBS.getGameInfo().getStartRaw().get().getStartLocations().stream()
                .filter(pos -> mySpawnPos.distance(pos) > 10)
                .findFirst().get();


        //set map
        LocationConstants.MAP = OBS.getGameInfo().getMapName();

        //set enemy race
        LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                .filter(playerInfo -> playerInfo.getPlayerId() == enemyId)
                .findFirst()
                .get()
                .getRequestedRace();


        PlacementMap.initializeMap();

        //start first scv
        mainCC = OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);
        ActionHelper.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
        ACTION.sendActions();

        //get map, get hardcoded map locations
        LocationConstants.onGameStart(mainCC);

        //build unit lists
        try {
            GameCache.onStep();
        } catch (Exception e) {
            e.printStackTrace();
        }

        LocationConstants.onGameStart(OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0));

//        DebugHelper.onGameStart();
        debug().debugGiveAllTech().debugGiveAllResources().debugFastBuild().debugGodMode();
//        debug().debugCreateUnit(Units.ZERG_ZERGLING_BURROWED, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-3), myId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_TEMPEST, mySpawnPos, enemyId, 1);
//        debug().debugCreateUnit(Units.TERRAN_RAVEN, mySpawnPos, myId, 1);
        debug().sendDebug();


    }


    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        int w = 293847;
    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        System.out.println(unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() + "f));");
    }

    @Override
    public void onStep() {
//        if (Time.nowFrames() % Strategy.STEP_SIZE != 0) {
//            return;
//        }
//        super.onStep();
//        try {
//            GameCache.onStep();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        List<UnitInPool> enemyHatchList = OBS.getUnits(Alliance.ENEMY, u -> u.unit().getType() == Units.ZERG_HATCHERY);
//        if (!enemyHatchList.isEmpty()) {
//            Unit myFlyingCC = OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_COMMAND_CENTER_FLYING).get(0).unit();
//            System.out.println("distance = " + UnitUtils.getDistance(myFlyingCC, enemyHatchList.get(0).unit()));
//            int wer = 392847;
//        }

        ACTION.sendActions();
        DEBUG.sendDebug();
    }

    public List<Point2d> possibleCcPos() {
        Point2d SCREEN_TOP_RIGHT = OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP1().toPoint2d();
        float minX = 2.5f;
        float minY = 3.5f;
        float maxX = SCREEN_TOP_RIGHT.getX() - 3.5f;
        float maxY = SCREEN_TOP_RIGHT.getY() - 2.5f;
        List<Point2d> resourceNodePosList = OBS.getUnits(Alliance.NEUTRAL, u ->
                UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()) ||
                UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType()))
                .stream()
                .map(UnitInPool::unit)
                .map(Unit::getPosition)
                .map(Point::toPoint2d)
                .collect(Collectors.toList());


        List<Point2d> uncheckedPosList = new ArrayList<>();
        for (float x = minX; x <= maxX; x += 3) {
            for (float y = minY; y <= maxY; y += 3) {
                Point2d thisPos = Point2d.of(x, y);
                if (enemySpawnPos.distance(thisPos) > 100 &&
                        checkCcCorners(x, y) &&
                        resourceNodePosList.stream().noneMatch(p -> p.distance(thisPos) < 6)) {
                    uncheckedPosList.add(thisPos);
                }
            }
        }
        uncheckedPosList = uncheckedPosList.stream().sorted(Comparator.comparing(p -> p.distance(mySpawnPos))).collect(Collectors.toList());

        return uncheckedPosList;
    }

    private Point2d queryPossibleCcList() {
        long start = System.currentTimeMillis();
        List<QueryBuildingPlacement> queryList = possibleCcPosList.stream()
                .map(p -> QueryBuildingPlacement
                        .placeBuilding()
                        .useAbility(Abilities.BUILD_COMMAND_CENTER)
                        .on(p).build())
                .collect(Collectors.toList());
        List<Boolean> placementList = QUERY.placement(queryList);
        Print.print("giant query = " + (System.currentTimeMillis() - start));
        for (int i=0; i<placementList.size(); i++) {
            if (!placementList.get(i).booleanValue()) {
                placementList.remove(i);
                possibleCcPosList.remove(i--);
            }
        }

        return null;
    }

    private boolean checkCcCorners(float x, float y) {
        Point2d top = Point2d.of(x, y+2.5f);
        Point2d bottom = Point2d.of(x, y-3.5f);
        Point2d left = Point2d.of(x-2.5f, y);
        Point2d right = Point2d.of(x+3.5f, y);
        Point2d center = Point2d.of(x, y);
        Point2d topLeft = Point2d.of(x-2.5f, y+2.5f);
        Point2d topRight = Point2d.of(x+3.5f, y+2.5f);
        Point2d botLeft = Point2d.of(x-2.5f, y-3.5f);
        Point2d botRight = Point2d.of(x+3.5f, y-3.5f);
        return OBS.isPlacable(topLeft) && OBS.isPlacable(topRight) &&
                OBS.isPlacable(botLeft) && OBS.isPlacable(botRight) &&
                OBS.isPlacable(top) && OBS.isPlacable(bottom) &&
                OBS.isPlacable(left) && OBS.isPlacable(right) &&
                OBS.isPlacable(center);
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
    }

    @Override
    public void onGameEnd() {
        try {
            control().saveReplay(Path.of("./data/" + System.currentTimeMillis() + ".SC2Replay"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) {
        if (unitInPool.unit().getType() == Units.TERRAN_CYCLONE) {
            debug().debugCreateUnit(Units.TERRAN_CYCLONE, LocationConstants.baseLocations.get(1), myId, 1);
        }
    }
}