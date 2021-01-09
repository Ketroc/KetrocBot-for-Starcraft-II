package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.query.AvailableAbilities;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.spatial.PointI;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.Tester;
import com.ketroc.terranbot.models.MuleMessages;
import com.ketroc.terranbot.utils.*;

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
    private UnitInPool fastScv;
    private UnitInPool fastScv2;
    private Unit natCC;
    private Unit minPatch;

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
                .filter(playerInfo -> playerInfo.getPlayerId() == enemyId)
                .findFirst()
                .get()
                .getRequestedRace();
        LocationConstants.onGameStart(Bot.OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0));

        DebugHelper.onGameStart();
//        debug().debugGodMode().debugFastBuild().debugIgnoreFood().debugIgnoreMineral().debugIgnoreResourceCost();
//        debug().debugGiveAllTech();
//        debug().debugCreateUnit(Units.TERRAN_COMMAND_CENTER, LocationConstants.baseLocations.get(1), myId, 1);
//        debug().debugCreateUnit(Units.NEUTRAL_MINERAL_FIELD, Point2d.of(88.5f, 90.5f), myId, 1);
//        debug().debugCreateUnit(Units.ZERG_CREEP_TUMOR_BURROWED, , myId, 1);
//        debug().debugCreateUnit(Units.ZERG_BANELING_BURROWED, Point2d.of(80, 100), myId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_IMMORTAL, Point2d.of(30, 30), enemyId, 1);
        debug().sendDebug();

//        commandCenter = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();
//        z = commandCenter.getPosition().getZ();
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        if (Bot.OBS.getGameLoop() < 10) {
            return;
        }
        if (unitInPool.unit().getType() == Units.TERRAN_SCV && fastScv == null) {
            fastScv = unitInPool;
            Bot.ACTION.unitCommand(fastScv.unit(), Abilities.HARVEST_GATHER, minPatch, false);
        }
        else if (unitInPool.unit().getType() == Units.TERRAN_SCV) {
            fastScv2 = unitInPool;
            Bot.ACTION.unitCommand(fastScv2.unit(), Abilities.HARVEST_GATHER, minPatch, false);
        }
    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        Print.print(unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() + "f));");
//
//        switch ((Units)unit.getType()) {
//            case TERRAN_SUPPLY_DEPOT:
//                depotPos = unit.getPosition().toPoint2d().add(Point2d.of(0.5f, 0.5f));
//                Print.print("start");
//                break;
//            case TERRAN_SENSOR_TOWER:
//                //Print.print("pos: " + unit.getPosition().toPoint2d());
//                Point2d p = unit.getPosition().toPoint2d().sub(depotPos);
//                Print.print("G.add(" + p.getX() + "f, " + p.getY() + "f);");
//        }

    }

    @Override
    public void onStep() {
        super.onStep();
        Point2d nearPos = Position.towards(LocationConstants.baseLocations.get(0), LocationConstants.baseLocations.get(1), 5);
        List<UnitInPool> units = Bot.OBS.getUnits(Alliance.ENEMY);
        List<UnitInPool> interceptors = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.PROTOSS_INTERCEPTOR);
        if (!interceptors.isEmpty()) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(interceptors), Abilities.ATTACK,
                    LocationConstants.baseLocations.get(8), false);
        }

        if (Time.nowFrames() == 10) {
            debug().debugCreateUnit(Units.TERRAN_LIBERATOR, nearPos, myId, 1);
        }
        if (Time.nowFrames() == 190) {
            int blah = 23984389;
        }
        if (Time.nowFrames() == 450) {

        }

        if (fastScv != null) {
            if (fastScv.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS)) {
                returnMicro(fastScv.unit());
            }
            else {
                harvestMicro(fastScv.unit());
            }
        }

        if (fastScv2 != null) {
            if (fastScv2.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS)) {
                returnMicro(fastScv2.unit());
            }
            else {
                harvestMicro(fastScv2.unit());
            }
        }

        ACTION.sendActions();
        DEBUG.sendDebug();
    }

    private void harvestMicro(Unit scv) {
        float distToPatch = UnitUtils.getDistance(scv, minPatch);
        if (distToPatch < 2.9f && distToPatch > 1.45f && UnitUtils.getOrder(scv) == Abilities.HARVEST_GATHER) {
            Point2d movePos = Position.towards(minPatch.getPosition().toPoint2d(), scv.getPosition().toPoint2d(), 1f);
            DebugHelper.draw3dBox(movePos, Color.YELLOW, 0.2f);
            DEBUG.sendDebug();
            Bot.ACTION.unitCommand(scv, Abilities.MOVE, movePos, false);
            return;
        }
        if (distToPatch <= 1.45f && UnitUtils.getOrder(scv) == Abilities.MOVE) {
            Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, minPatch, false);
        }
        if (!scv.getOrders().isEmpty()) {
            UnitOrder order = scv.getOrders().get(0);
            if (order.getAbility() == Abilities.HARVEST_GATHER && !order.getTargetedUnitTag().equals(minPatch.getTag())) {
                Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, minPatch, false);
            }
        }
    }

    private void returnMicro(Unit scv) {
        float distToCC = UnitUtils.getDistance(scv, natCC);
        if (distToCC < 4.1f && distToCC > 2.95f && UnitUtils.getOrder(scv) == Abilities.HARVEST_RETURN) {
            Point2d movePos = Position.towards(natCC.getPosition().toPoint2d(), scv.getPosition().toPoint2d(), 2.6f);
            DebugHelper.draw3dBox(movePos, Color.YELLOW, 0.2f);
            DEBUG.sendDebug();
            Bot.ACTION.unitCommand(scv, Abilities.MOVE, movePos, false);
        }
        if (distToCC <= 2.95f && UnitUtils.getOrder(scv) == Abilities.MOVE) {
            Bot.ACTION.unitCommand(scv, Abilities.HARVEST_RETURN, false);
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