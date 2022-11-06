package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.PosConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Position {
    public static Point2d towards(Unit originUnit, Point2d target, float distance) {
        return towards(originUnit.getPosition().toPoint2d(), target, distance);
    }

    public static Point2d towards(Point2d origin, Unit targetUnit, float distance) {
        return towards(origin, targetUnit.getPosition().toPoint2d(), distance);
    }

    public static Point2d towards(Unit originUnit, Unit targetUnit, float distance) {
        return towards(originUnit.getPosition().toPoint2d(), targetUnit.getPosition().toPoint2d(), distance);
    }

    public static Point2d towards(Point2d origin, Point2d target, float distance) {
        if (origin.equals(target)) { //move the distance left if origin == target
            return inBounds(origin.getX() + distance, origin.getY());
        }
        Point2d vector = unitVector(origin, target);
        return inBounds(origin.add(vector.mul(distance)));
    }


    public static Point2d towards(Unit originUnit, Point2d target, float xDistance, float yDistance) {
        return towards(originUnit.getPosition().toPoint2d(), target, xDistance, yDistance);
    }

    public static Point2d towards(Point2d origin, Unit targetUnit, float xDistance, float yDistance) {
        return towards(origin, targetUnit.getPosition().toPoint2d(), xDistance, yDistance);
    }

    public static Point2d towards(Unit originUnit, Unit targetUnit, float xDistance, float yDistance) {
        return towards(originUnit.getPosition().toPoint2d(), targetUnit.getPosition().toPoint2d(), xDistance, yDistance);
    }

    public static Point2d towards(Point2d origin, Point2d target, float xDistance, float yDistance) {
        if (target.getX() < origin.getX()) {
            xDistance = -xDistance;
        }
        if (target.getY() < origin.getY()) {
            yDistance = -yDistance;
        }
        float x = origin.getX() + xDistance;
        float y = origin.getY() + yDistance;
        return inBounds(x, y);
    }

    private static Point2d inBounds(Point2d p) {
        return Point2d.of(inBoundsX(p.getX()), inBoundsY(p.getY()));
    }

    private static Point2d inBounds(float x, float y) {
        return Point2d.of(inBoundsX(x), inBoundsY(y));
    }

    private static float inBoundsX(float x) {
        x = Math.min(x, PosConstants.MAX_X);
        x = Math.max(x, PosConstants.MIN_X);
        return x;
    }

    private static float inBoundsY(float y) {
        y = Math.min(y, PosConstants.MAX_Y);
        y = Math.max(y, PosConstants.MIN_Y);
        return y;
    }

    public static boolean isOutOfBounds(Point2d p) {
        return isOutOfBoundsX(p.getX()) || isOutOfBoundsY(p.getY());
    }

    private static boolean isOutOfBoundsX(float x) {
        return x > PosConstants.MAX_X || x < PosConstants.MIN_X;
    }

    private static boolean isOutOfBoundsY(float y) {
        return y > PosConstants.MAX_Y || y < PosConstants.MIN_Y;
    }

    public static boolean isOnBoundary(Point2d p) {
        return isOnBoundaryX(p.getX()) || isOnBoundaryY(p.getY());
    }

    private static boolean isOnBoundaryX(float x) {
        return x == PosConstants.MAX_X || x == PosConstants.MIN_X;
    }

    private static boolean isOnBoundaryY(float y) {
        return y == PosConstants.MAX_Y || y == PosConstants.MIN_Y;
    }

    public static Point2d midPoint(Point2d p1, Point2d p2) {
        return p1.add(p2).div(2);
    }

    public static Point2d midPoint(List<Point2d> pointList) {
        Point2d midPoint = Point2d.of(0,0);
        for (Point2d p : pointList) {
            midPoint = midPoint.add(p);
        }
        return midPoint.div(pointList.size());
    }

    public static Point2d midPointUnitInPoolsWeighted(List<UnitInPool> unitList) {
        Point2d midPoint = Point2d.of(0, 0);
        for (UnitInPool u : unitList) {
            midPoint = midPoint.add(u.unit().getPosition().toPoint2d());
        }
        return midPoint.div(unitList.size());
    }

    public static Point2d midPointUnitsWeighted(List<Unit> unitList) {
        Point2d midPoint = Point2d.of(0, 0);
        for (Unit u : unitList) {
            midPoint = midPoint.add(u.getPosition().toPoint2d());
        }
        return midPoint.div(unitList.size());
    }

    public static Point2d midPointUnitsMedian(Collection<Unit> unitList) {
        if (unitList.isEmpty()) {
            return null;
        }
        List<Float> xCoords = unitList.stream()
                .map(Unit::getPosition)
                .map(Point::getX)
                .sorted()
                .collect(Collectors.toList());
        List<Float> yCoords = unitList.stream()
                .map(Unit::getPosition)
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

    public static Point2d rotateTowards(Point2d origin, Point2d pivotPoint, Point2d towardsTarget, double angle) {
        float angleDiff = getSignedAngleDifference(getAngle(origin, pivotPoint), getAngle(origin, towardsTarget));
        if (angleDiff < 1) {
            angle = -angle;
        }
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

    public static Point2d toNearestHalfPoint(Point2d point) {
        return Point2d.of(roundToNearestHalf(point.getX()), roundToNearestHalf(point.getY()));
    }

    public static Point2d toHalfPoint(Point2d point) { //useful for 1x1, 3x3, and 5x5 structure placements
        return Point2d.of((int)point.getX() + 0.5f, (int)point.getY() + 0.5f);
    }

    public static Point2d toWholePoint(Point2d point) { //useful for 2x2 structure placements
        return Point2d.of(Math.round(point.getX()), Math.round(point.getY()));
    }

    public static float roundToNearestHalf(float number) {
        return Math.round(number * 2) / 2f;
    }

    //moves this point on 1 plane to the closest point clear of the obstacle
    public static Point2d moveClear(Point2d pointToMove, Unit obstacle, float minDistance) {
        Point2d obstaclePoint = toNearestHalfPoint(obstacle.getPosition().toPoint2d());
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

    public static double distance(float x1, float y1, float x2, float y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    public static float distanceSquared(float x1, float y1, float x2, float y2) {
        float width = x2 - x1;
        float height = y2 - y1;
        return width*width + height*height;
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
        return findNearestPlacement(placementAbility, pos, searchRadius, p -> true);
    }

    public static Point2d findNearestPlacement(Ability placementAbility, Point2d pos, int searchRadius, Predicate<Point2d> searchFilter) {
        List<Point2d> possiblePosList = getPosListGridByClosest(pos, searchRadius, searchFilter);
        return getFirstPosFromQuery(possiblePosList, placementAbility);
    }

    public static Point2d getFirstPosFromQuery(List<Point2d> posList, Ability placementAbility) {
        List<QueryBuildingPlacement> queryList = posList.stream()
                .map(p -> QueryBuildingPlacement.placeBuilding().useAbility(placementAbility).on(p).build())
                .collect(Collectors.toList());

        List<Boolean> placementList = Bot.QUERY.placement(queryList);
        if (placementList.contains(true)) {
            return posList.get(placementList.indexOf(true));
        }
        return null;
    }

    public static List<Point2d> getPosListGrid(Point2d pos, int radius) {
        List<Point2d> posList = new ArrayList<>();
        float posX = (int)pos.getX() + 0.5f;
        float posY = (int)pos.getY() + 0.5f;
        float startX = posX - radius;
        float endX = posX + radius;
        float startY = posY - radius;
        float endY = posY + radius;
        for (float x=startX; x<=endX; x++) {
            for (float y = startY; y <= endY; y++) {
                posList.add(Point2d.of(x, y));
            }
        }
        return posList;
    }

    public static List<Point2d> getPosListGridByClosest(Point2d pos, int radius, Predicate<Point2d> searchFilter) {
        List<Point2d> posList = getPosListGrid(pos, radius);
        return posList.stream()
                .filter(searchFilter)
                .sorted(Comparator.comparing(p -> p.distance(pos)))
                .collect(Collectors.toList());
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

    public static float getAngle(Unit origin, Unit target) {
        return getAngle(target.getPosition().toPoint2d(), origin.getPosition().toPoint2d());
    }

    // 0 = right, 90 = up, 180 = left, 270 = down
    public static float getAngle(Point2d origin, Point2d target) {
        return ((float)Math.toDegrees(Math.atan2(target.getX() - origin.getX(), origin.getY() - target.getY())) + 270) % 360;
    }

    public static float getAngleDifference(float angle1, float angle2) {
        return getAngleDifference(angle1, angle2, false);
    }

    public static float getSignedAngleDifference(float angle1, float angle2) {
        return getAngleDifference(angle1, angle2, true);
    }

    private static float getAngleDifference(float angle1, float angle2, boolean isSigned) {
        float difference = Math.abs(angle1 - angle2) % 360;
        difference = (difference > 180) ? 360 - difference : difference;
        if (isSigned &&
                !((angle1 - angle2 >= 0 && angle1 - angle2 <= 180) || (angle1 - angle2 <= -180 && angle1 - angle2 >= -360))){
            difference = -difference;
        }
        return difference;
    }

    public static float getFacingAngle(Unit unit) {
        return (float)Math.toDegrees(unit.getFacing());
    }

    public static Point2d getDestinationByAngle(Point2d origin, float angle, float distance) {
        return inBounds(
                Point2d.of(
                        distance * (float)Math.cos(angle) + origin.getX(),
                        distance * (float)Math.sin(angle) + origin.getY()
                )
        );
    }

    //moves towards target where the x-distance or y-distance has moved by the specified distance
    public static Point2d towards1dDistance(Point2d origin, Point2d target, float distance) {
        Point2d unitVector = unitVector(origin, target);
        return inBounds(origin.add(unitVector.mul(distance / Math.max(Math.abs(unitVector.getX()), Math.abs(unitVector.getY())))));
    }

    public static Point2d towardsXAxis(Point2d origin, Point2d target, float distance) {
        float xCoord = origin.getX() + (origin.getX() > target.getX() ? -distance : distance);
        return inBounds(Point2d.of(xCoord, origin.getY()));
    }

    public static Point2d towardsYAxis(Point2d origin, Point2d target, float distance) {
        float yCoord = origin.getY() + (origin.getY() > target.getY() ? -distance : distance);
        return inBounds(Point2d.of(origin.getX(), yCoord));
    }
}
