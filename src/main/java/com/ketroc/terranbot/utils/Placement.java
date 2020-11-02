package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Placement {
    public static List<Point2d> possibleCcPosList;
    public static float MIN_DISTANCE_FROM_ENEMY_NAT;

    public static void onGameStart() {
        MIN_DISTANCE_FROM_ENEMY_NAT = (LocationConstants.MAP == MapNames.GOLDEN_WALL) ? 155 : 100;
        setPossibleCcPos();
        queryPossibleCcList();
    }

    public static void setPossibleCcPos() {
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


        possibleCcPosList = new ArrayList<>();
        for (float x = minX; x <= maxX; x += 3) {
            for (float y = minY; y <= maxY; y += 3) {
                Point2d thisPos = Point2d.of(x, y);
                if (LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-2).distance(thisPos) > MIN_DISTANCE_FROM_ENEMY_NAT &&
                        checkCcCorners(x, y) &&
                        resourceNodePosList.stream().noneMatch(p -> p.distance(thisPos) < 6)) {
                    possibleCcPosList.add(thisPos);
                }
            }
        }
        possibleCcPosList = possibleCcPosList.stream().sorted(Comparator.comparing(p -> p.distance(LocationConstants.baseLocations.get(0)))).collect(Collectors.toList());

    }

    private static void queryPossibleCcList() {
        if (possibleCcPosList.isEmpty()) {
            return;
        }
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
    }

    public static Point2d getNextExtraCCPos() {
        queryPossibleCcList();
        if (possibleCcPosList.isEmpty()) {
            return null;
        }
        return possibleCcPosList.remove(0);
    }

    private static boolean checkCcCorners(float x, float y) {
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

}
