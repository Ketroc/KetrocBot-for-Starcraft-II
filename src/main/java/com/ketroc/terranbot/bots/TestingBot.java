package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.Tester;
import com.ketroc.terranbot.managers.ActionErrorManager;
import com.ketroc.terranbot.utils.DebugHelper;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TestingBot extends Bot {
    public static UnitInPool bunker;
    public static UnitInPool marine;
    public float z;
    public Unit commandCenter;
    public int myId;
    public int enemyId;
    public int neutralId = 16;
    public Point2d mySpawnPos;
    public Point2d enemySpawnPos;
    public List<Point2d> possibleCcPosList;
    public TestingBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    @Override
    public void onGameStart() {
        super.onGameStart();
        myId = Bot.OBS.getPlayerId();
        enemyId = Bot.OBS.getGameInfo().getPlayersInfo().stream()
                .map(PlayerInfo::getPlayerId)
                .filter(id -> id != myId)
                .findFirst()
                .get();
        mySpawnPos = Bot.OBS.getStartLocation().toPoint2d();
        enemySpawnPos = Bot.OBS.getGameInfo().getStartRaw().get().getStartLocations().stream()
                .filter(pos -> mySpawnPos.distance(pos) > 10)
                .findFirst().get();


//        debug().debugGodMode().debugFastBuild().debugIgnoreFood().debugIgnoreMineral().debugIgnoreResourceCost();
//        debug().debugCreateUnit(Units.NEUTRAL_MINERAL_FIELD, Point2d.of(108.5f, 100.5f), neutralId, 1);
//        debug().debugCreateUnit(Units.TERRAN_CYCLONE, Point2d.of(100, 100), myId, 1);
//        debug().debugCreateUnit(Units.TERRAN_SUPPLY_DEPOT, Point2d.of(40, 40), myId, 1);
////        debug().debugCreateUnit(Units.PROTOSS_PHOENIX, Point2d.of(114, 120), enemyId, 1);
//        debug().sendDebug();

//        commandCenter = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();
//        z = commandCenter.getPosition().getZ();
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        System.out.println((Units)unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() + "f));");

    }

    @Override
    public void onStep() {
        super.onStep();
        if (Time.nowFrames() == 300) {
            long start = System.currentTimeMillis();
            possibleCcPosList = possibleCcPos();
            possibleCcPosList.forEach(point2d -> DebugHelper.drawBox(point2d, Color.BLUE, 0.2f));
            System.out.println("possibleCcPos() time = " + (System.currentTimeMillis() - start));
            System.out.println("possibleCcPosList.size() = " + possibleCcPosList.size());
            Point2d ccPos = queryPossibleCcList();
            System.out.println("possibleCcPosList.size() after query = " + possibleCcPosList.size());
        }
        if (Time.nowFrames() == 400) {
            possibleCcPosList.forEach(point2d -> DebugHelper.drawBox(point2d, Color.BLUE, 0.2f));
        }
        if (Time.nowFrames() == 1000) {
            Point2d ccPos = queryPossibleCcList();
            possibleCcPosList.forEach(point2d -> DebugHelper.drawBox(point2d, Color.BLUE, 0.2f));
        }
        ACTION.sendActions();
        DEBUG.sendDebug();
        if (Time.nowFrames() == 300 || Time.nowFrames() == 400 || Time.nowFrames() == 1400) {
            int q = 0;
        }
    }

    public List<Point2d> possibleCcPos() {
        Point2d SCREEN_TOP_RIGHT = Bot.OBS.getGameInfo().getStartRaw().get().getPlayableArea().getP1().toPoint2d();
        float minX = 2.5f;
        float minY = 3.5f;
        float maxX = SCREEN_TOP_RIGHT.getX() - 3.5f;
        float maxY = SCREEN_TOP_RIGHT.getY() - 2.5f;
        List<Point2d> resourceNodePosList = Bot.OBS.getUnits(Alliance.NEUTRAL, u ->
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
        List<Boolean> placementList = Bot.QUERY.placement(queryList);
        System.out.println("giant query = " + (System.currentTimeMillis() - start));
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
        return Bot.OBS.isPlacable(topLeft) && Bot.OBS.isPlacable(topRight) &&
                Bot.OBS.isPlacable(botLeft) && Bot.OBS.isPlacable(botRight) &&
                Bot.OBS.isPlacable(top) && Bot.OBS.isPlacable(bottom) &&
                Bot.OBS.isPlacable(left) && Bot.OBS.isPlacable(right) &&
                Bot.OBS.isPlacable(center);
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
    }


}