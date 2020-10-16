package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitSnapshot;
import com.ketroc.terranbot.bots.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Position {
    public static Point2d towards(Point2d origin, Point2d target, float distance) {
        if (origin.equals(target)) { //move the distance left if origin == target
            return inBounds(origin.getX() + distance, origin.getY());
        }
        Point2d vector = unitVector(origin, target);
        return inBounds(origin.add(vector.mul(distance)));
    }

    private static Point2d inBounds(Point2d p) {
        return Point2d.of(inBoundsX(p.getX()), inBoundsY(p.getY()));
    }

    private static Point2d inBounds(float x, float y) {
        return Point2d.of(inBoundsX(x), inBoundsY(y));
    }

    private static float inBoundsX(float x) {
        x = Math.min(x, LocationConstants.MAX_X);
        x = Math.max(x, 0);
        return x;
    }

    private static float inBoundsY(float y) {
        y = Math.min(y, LocationConstants.MAX_Y);
        y = Math.max(y, 0);
        return y;
    }

    private static boolean isOutOfBoundsX(float x) {
        return x > LocationConstants.MAX_X || x < 0;
    }

    private static boolean isOutOfBoundsY(float y) {
        return y > LocationConstants.MAX_Y || y < 0;
    }

    public static boolean atEdgeOfMap(Point2d p) {
        return p.getX() == 0 || p.getX() == LocationConstants.MAX_X ||
                p.getY() == 0 || p.getY() == LocationConstants.MAX_Y;
    }

    public static Point2d towards(Point2d origin, Point2d target, float xDistance, float yDistance) {
        if (target.getX() < origin.getX()) {
            xDistance *= -1;
        }
        if (target.getY() < origin.getY()) {
            yDistance *= -1;
        }
        float x = origin.getX() + xDistance;

        float y = origin.getY() + yDistance;
        return inBounds(x, y);
    }

    public static Point2d midPoint(Point2d p1, Point2d p2) {
        return p1.add(p2).div(2);
    }

    public static Point2d midPoint(List<Point2d> pointList) {
        Point2d midPoint = Point2d.of(0,0);
        for (Point2d p : pointList) {
            midPoint.add(p);
        }
        return midPoint.div(pointList.size());
    }

    public static Point2d midPointUnitsWeighted(List<Unit> unitList) {
        Point2d midPoint = Point2d.of(0, 0);
        for (Unit u : unitList) {
            midPoint = midPoint.add(u.getPosition().toPoint2d());
        }
        return midPoint.div(unitList.size());
    }

    public static Point2d midPointUnitsMedian(List<Unit> unitList) {
        if (unitList.isEmpty()) {
            return null;
        }
        List<Float> xCoords = unitList.stream()
                .map(UnitSnapshot::getPosition)
                .map(Point::getX)
                .sorted()
                .collect(Collectors.toList());
        List<Float> yCoords = unitList.stream()
                .map(UnitSnapshot::getPosition)
                .map(Point::getY)
                .sorted()
                .collect(Collectors.toList());
        return Point2d.of(xCoords.get(xCoords.size()/2), yCoords.get(yCoords.size()/2));
    }

    public static Point2d midPointUnits(List<Unit> unitList) {
        float minX, maxX, minY, maxY;
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = 0;
        for (Unit u : unitList) {
            Point2d p = u.getPosition().toPoint2d();
            minX = Math.min(p.getX(), minX);
            maxX = Math.max(p.getX(), maxX);
            minY = Math.min(p.getY(), minY);
            maxY = Math.max(p.getY(), maxY);
        }
        return Point2d.of((minX+maxX)/2f, (minY+maxY)/2f);
    }

    public static Point2d midPointUnitInPools(List<UnitInPool> unitList) {
        if (unitList.isEmpty()) {
            return null;
        }
        Point2d midPoint = Point2d.of(0,0);
        for (UnitInPool u : unitList) {
            midPoint = midPoint.add(u.unit().getPosition().toPoint2d());
        }
        return midPoint.div(unitList.size());
    }

    public static Point2d unitVector(Point2d from, Point2d to) {
        return normalize(to.sub(from));
    }

    public static Point2d rotate(Point2d origin, Point2d pivotPoint, double angle) {
        return rotate(origin, pivotPoint, angle, false);
    }

    public static Point2d rotate(Point2d origin, Point2d pivotPoint, double angle, boolean nullOutOfBounds) {
        double rads = Math.toRadians(angle);
        double sin = Math.sin(rads);
        double cos = Math.cos(rads);

        origin = origin.sub(pivotPoint);

        //rotate point
        float xnew = (float)(origin.getX() * cos - origin.getY() * sin);
        float ynew = (float)(origin.getX() * sin + origin.getY() * cos);

        //add back the pivot point
        float x = xnew + pivotPoint.getX();
        float y = ynew + pivotPoint.getY();

        if (nullOutOfBounds && (isOutOfBoundsX(x) || isOutOfBoundsY(y))) {
            return null;
        }
        else {
            return inBounds(x, y);
        }
    }

    public static Point2d normalize(Point2d vector) {
        double length = Math.sqrt(vector.getX() * vector.getX() + vector.getY() * vector.getY());
        return Point2d.of((float)(vector.getX() / length), (float)(vector.getY() / length));
    }

    public static Point2d nearestHalfPoint(Point2d point) {
        return Point2d.of(roundToNearestHalf(point.getX()), roundToNearestHalf(point.getY()));
    }

    public static Point2d toHalfPoint(Point2d point) { //useful for 1x1, 3x3, and 5x5 structure placements
        return Point2d.of((int)point.getX() + 0.5f, (int)point.getY() + 0.5f);
    }

    public static Point2d toWholePoint(Point2d point) { //useful for 2x2 structure placements
        return Point2d.of(Math.round(point.getX()), Math.round(point.getY()));
    }

    public static float roundToNearestHalf(float number) {
        return Math.round(number * 2) / 2;
    }

    //moves this point on 1 plane to the closest point clear of the obstacle
    public static Point2d moveClear(Point2d pointToMove, Unit obstacle, float minDistance) {
        Point2d obstaclePoint = nearestHalfPoint(obstacle.getPosition().toPoint2d());
        return moveClear(pointToMove, obstaclePoint, minDistance);
    }

    public static Point2d moveClear(Point2d pointToMove, Point2d obstacle, float minDistance) {
        return moveClear(pointToMove, obstacle, minDistance, Float.MAX_VALUE);
    }

    public static Point2d moveClearExactly(Point2d pointToMove, Point2d obstacle, float distance) {
        return moveClear(pointToMove, obstacle, distance, distance);
    }

    public static Point2d moveClear(Point2d pointToMove, Point2d obstacle, float minDistance, float maxDistance) {
        float xDistance = Math.abs(obstacle.getX() - pointToMove.getX()); //find x distance
        float yDistance = Math.abs(obstacle.getY() - pointToMove.getY()); //find y distance

        Point2d newPoint;
        float distance;
        if (xDistance > yDistance) { //move on x-axis
            distance = Math.min(maxDistance, Math.max(minDistance, xDistance));
            newPoint = inBounds(moveNumberExactly(pointToMove.getX(), obstacle.getX(), distance), pointToMove.getY());
        }
        else { //move on y-axis
            distance = Math.min(maxDistance, Math.max(minDistance, yDistance));
            newPoint = inBounds(pointToMove.getX(), moveNumberExactly(pointToMove.getY(), obstacle.getY(), distance));
        }
        return newPoint;
    }

    //get Point at terrain height from Point2d
    public static Point point2dToPoint(Point2d p) {
        return Point.of(p.getX(), p.getY(), Bot.OBS.terrainHeight(p));
    }

    //returns true if 2 points are on the same elevation
    public static boolean isSameElevation(Point p1, Point p2) {
        return Math.abs(p1.getZ() - p2.getZ()) < 1.2;
    }

    private static float moveNumberAtLeast(float number, float blocker, float stayClearBy) {
        if (blocker >= number && blocker - number < stayClearBy) {
            number = blocker - stayClearBy;
        }
        else if (number >= blocker && number - blocker < stayClearBy) {
            number = blocker + stayClearBy;
        }
        return number;
    }

    private static float moveNumberExactly(float number, float blocker, float stayClearBy) {
        if (blocker >= number) {
            number = blocker - stayClearBy;
        }
        else if (number >= blocker) {
            number = blocker + stayClearBy;
        }
        return number;
    }

    public static float getZ(float x, float y) {
        return getZ(Point2d.of(x, y));
    }

    public static float getZ(Point2d p) {
        return Bot.OBS.terrainHeight(p) + 0.3f;
    }

    public static float distance(float x1, float y1, float x2, float y2) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        return (float)Math.sqrt(width*width + height*height);
    }

    public static Point2d findNearestPlacement(Ability placementAbility, Point2d pos) {
        return findNearestPlacement(placementAbility, pos, 5);
    }

    public static Point2d findNearestPlacement(Units structureType, Point2d pos) {
        return findNearestPlacement(Bot.OBS.getUnitTypeData(false).get(structureType).getAbility().orElse(Abilities.INVALID), pos, 5);
    }

    public static Point2d findNearestPlacement(Units structureType, Point2d pos, int searchRadius) {
        return findNearestPlacement(Bot.OBS.getUnitTypeData(false).get(structureType).getAbility().orElse(Abilities.INVALID), pos, searchRadius);
    }

    public static Point2d findNearestPlacement(Ability placementAbility, Point2d pos, int searchRadius) {
        if (Bot.QUERY.placement(placementAbility, pos)) {
            return pos;
        }
        float x = pos.getX();
        float y = pos.getY();
        boolean isMoveUp = true;
        boolean isMoveRight = true;
        boolean isMoveVertical = true;
        int xLength = 1;
        int yLength = 1;
        int numTurns = searchRadius*4 + 1;
        for (int count=0; count<numTurns; count++) {
            if (isMoveVertical) {
                for (int i = 0; i < yLength; i++) {
                    y += (isMoveUp) ? 1 : -1;
                    Point2d testPos = Point2d.of(x, y);
                    if (Bot.QUERY.placement(placementAbility, testPos)) {
                        return testPos;
                    }
                }
                yLength++;
                isMoveUp = !isMoveUp;
                isMoveVertical = !isMoveVertical;
            }
            else {
                for (int i = 0; i < xLength; i++) {
                    x += (isMoveRight) ? 1 : -1;
                    Point2d testPos = Point2d.of(x, y);
                    if (Bot.QUERY.placement(placementAbility, testPos)) {
                        return testPos;
                    }
                }
                xLength++;
                isMoveRight = !isMoveRight;
                isMoveVertical = !isMoveVertical;
            }
        }
        return null;
    }

    public static List<Point2d> getSpiralList(Point2d pos, int searchRadius) {
        List<Point2d> spiralList = new ArrayList<>();
        spiralList.add(pos);

        float x = pos.getX();
        float y = pos.getY();
        boolean isMoveUp = true;
        boolean isMoveRight = true;
        boolean isMoveVertical = true;
        int xLength = 1;
        int yLength = 1;
        int numTurns = searchRadius*4 + 1;
        for (int count=0; count<numTurns; count++) {
            if (isMoveVertical) {
                for (int i = 0; i < yLength; i++) {
                    y += (isMoveUp) ? 1 : -1;
                    spiralList.add(Point2d.of(x, y));
                }
                yLength++;
                isMoveUp = !isMoveUp;
                isMoveVertical = !isMoveVertical;
            }
            else {
                for (int i = 0; i < xLength; i++) {
                    x += (isMoveRight) ? 1 : -1;
                    spiralList.add(Point2d.of(x, y));
                }
                xLength++;
                isMoveRight = !isMoveRight;
                isMoveVertical = !isMoveVertical;
            }
        }
        return spiralList;
    }
}
