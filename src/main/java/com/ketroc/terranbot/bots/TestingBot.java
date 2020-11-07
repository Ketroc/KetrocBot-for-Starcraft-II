package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.spatial.PointI;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.models.MuleMessages;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.time.LocalDate;
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

    public Point2d depotPos;

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

        //set map
        LocationConstants.MAP = Bot.OBS.getGameInfo().getMapName();

        //set enemy race
        LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                .filter(playerInfo -> playerInfo.getPlayerId() != Bot.OBS.getPlayerId())
                .findFirst()
                .get()
                .getRequestedRace();
        LocationConstants.onGameStart(Bot.OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0));

        debug().debugGodMode().debugFastBuild().debugIgnoreFood().debugIgnoreMineral().debugIgnoreResourceCost();
//        debug().debugCreateUnit(Units.NEUTRAL_MINERAL_FIELD, Point2d.of(108.5f, 100.5f), neutralId, 1);
//        debug().debugCreateUnit(Units.TERRAN_MULE, Point2d.of(90, 100), myId, 1);
//        debug().debugCreateUnit(Units.TERRAN_SUPPLY_DEPOT, Point2d.of(40, 40), myId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_IMMORTAL, Point2d.of(30, 30), enemyId, 1);
        debug().sendDebug();

//        commandCenter = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();
//        z = commandCenter.getPosition().getZ();
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        System.out.println(unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() + "f));");
//
//        switch ((Units)unit.getType()) {
//            case TERRAN_SUPPLY_DEPOT:
//                depotPos = unit.getPosition().toPoint2d().add(Point2d.of(0.5f, 0.5f));
//                System.out.println("start");
//                break;
//            case TERRAN_SENSOR_TOWER:
//                //System.out.println("pos: " + unit.getPosition().toPoint2d());
//                Point2d p = unit.getPosition().toPoint2d().sub(depotPos);
//                System.out.println("G.add(" + p.getX() + "f, " + p.getY() + "f);");
//        }

    }

    @Override
    public void onStep() {
        super.onStep();
        Unit cc = Bot.OBS.getUnits(u -> u.unit().getType().toString().contains("COMMAND")).stream()
                .map(UnitInPool::unit)
                .findFirst()
                .get();
        if (Time.nowFrames() == 100) {
            System.out.println("cc.getTag() = " + cc.getTag());
        }
        if (Time.nowFrames() == 800) {
            System.out.println("cc.getTag() = " + cc.getTag());
        }
        if (Time.nowFrames() == 1600) {
            System.out.println("cc.getTag() = " + cc.getTag());
        }

        ACTION.sendActions();
        DEBUG.sendDebug();
        if (false) {
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