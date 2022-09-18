package com.ketroc.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.geometry.Position;
import com.ketroc.micro.Cyclone;
import com.ketroc.models.MannerMule;
import com.ketroc.utils.Error;
import com.ketroc.utils.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    public static boolean hasScanned;
    public static boolean scanEffectArrived;
    public static boolean scanEnded;
    public static Point2d broodlingPos;

    public TestingBot(String opponentId) {
        super(opponentId);
    }

    @Override
    public void onGameStart() {
        super.onGameStart();
        mySpawnPos = OBS.getStartLocation().toPoint2d();
        enemySpawnPos = OBS.getGameInfo().getStartRaw().get().getStartLocations().stream()
                .filter(pos -> mySpawnPos.distance(pos) > 10)
                .findFirst().get();


        //set map
        PosConstants.MAP = OBS.getGameInfo().getMapName();

        //set enemy race
        PosConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                .filter(playerInfo -> playerInfo.getPlayerId() == enemyId)
                .findFirst()
                .get()
                .getRequestedRace();

        //start first scv
        mainCC = OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);
        ActionHelper.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
        ACTION.sendActions();

        //get map, get hardcoded map locations
        PosConstants.onGameStart(mainCC);

        //build unit lists
        try {
            GameCache.onStepStart();
        } catch (Exception e) {
            Error.onException(e);
        }

        PosConstants.onGameStart(OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0));

//        DebugHelper.onGameStart();
        debug().debugGiveAllResources().debugFastBuild().debugGiveAllTech();
//        MannerMule.doTrollMule = true;

        broodlingPos = Position.towards(mySpawnPos, PosConstants.myRampPos, 9);
        debug().debugCreateUnit(Units.ZERG_BROODLORD, mySpawnPos, myId, 25);
        debug().debugCreateUnit(Units.ZERG_BROODLING, broodlingPos, myId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_TEMPEST, mySpawnPos, enemyId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_PROBE, mySpawnPos, myId, 1);
        debug().sendDebug();
//        MannerMule.onGameStart();

    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        //printMuleMessagePointsUsingDrones();
    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        System.out.println(unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() + "f));");
    }

    @Override
    public void onStep() {
        //build unit lists
        try {
            GameCache.onStepStart();

//            debug().debugCreateUnit(Units.ZERG_ROACH, GameCache.baseList.get(1).getCcPos(), enemyId, 1);
//            debug().sendDebug();
//
//            List<UnitInPool> broodLords = observation().getUnits(u -> u.unit().getType() == Units.ZERG_BROODLORD);
//            boolean isAllOffCooldown = broodLords.stream().allMatch(u -> u.unit().getWeaponCooldown().get() == 0);
//            List<Unit> broodUnits = broodLords.stream().map(UnitInPool::unit).collect(Collectors.toList());
//            List<UnitInPool> broodLings = observation().getUnits(u -> u.unit().getType() == Units.ZERG_BROODLING);
//            Unit broodlingTarget = broodLings.stream()
//                    .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), broodlingPos))).get().unit();
//
//            //BROOD LORDS
//            //if broods are all off cooldown shoot at "broodling"
//            if (isAllOffCooldown) {
//                actions().unitCommand(broodUnits, Abilities.ATTACK, broodlingTarget, false);
//            }
//            //group the brood lords
//            else {
//                actions().unitCommand(broodUnits, Abilities.MOVE, mySpawnPos, false);
//            }
//
//            //BROODLINGS
//            //keep longest duration broodling at base midpoint
//            actions().unitCommand(broodlingTarget, Abilities.MOVE, broodlingPos, false);
//
//            //a-move the rest to the enemy base
//            List<Unit> otherBroodlings = broodLings.stream()
//                    .filter(u -> !u.getTag().equals(broodlingTarget.getTag()))
//                    .map(UnitInPool::unit)
//                    .collect(Collectors.toList());
//            actions().unitCommand(otherBroodlings, Abilities.ATTACK, enemySpawnPos, false);
//
//
//
        } catch (Exception e) {
            Error.onException(e);
        }

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
            Error.onException(e);
        }
    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) {
        if (unitInPool.unit().getType() == Units.TERRAN_CYCLONE) {
            debug().debugCreateUnit(Units.TERRAN_CYCLONE, PosConstants.baseLocations.get(1), myId, 1);
        }
    }

    public static void printScvLetterPos() {
        List<Point2d> scvPosList = OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MULE)
                .stream()
                .map(u -> u.unit().getPosition().toPoint2d())
                .collect(Collectors.toList());
        float minX = scvPosList.stream()
                .min(Comparator.comparing(p -> p.getX()))
                .map(p -> p.getX())
                .orElse(0f);
        float maxY = scvPosList.stream()
                .max(Comparator.comparing(p -> p.getY()))
                .map(p -> p.getY())
                .orElse(0f);
        StringBuilder str = new StringBuilder("float[][] letter = new float[][]{");
        for (Point2d scvPos : scvPosList) {
            str.append("{").append(scvPos.getX()-minX).append("f,").append(maxY-scvPos.getY()).append("f}, ");
        }
        str.deleteCharAt(str.length()-1).deleteCharAt(str.length()-1).append("};");
        System.out.println(str);
    }
}