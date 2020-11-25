package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.DebugHelper;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Tester {

    /**
     * Clusters units within some distance of each other and returns a list of them and their center of mass.
     */
    public static Map<Point2d, List<UnitInPool>> cluster(ObservationInterface observation, List<UnitInPool> units, double distanceApart) {
        return cluster(observation, units, distanceApart,true);
    }


    /**
     * Clusters units within some distance of each other and returns a list of them and their center of mass.
     */
    public static Map<Point2d, List<UnitInPool>> cluster(ObservationInterface observation, List<UnitInPool> units, double distanceApart, boolean isElevationResticted) {
        Map<Point2d, List<UnitInPool>> clusters = new LinkedHashMap<>();
        float minX = -1; float maxX = -1; float minY = -1; float maxY = -1;
        for (UnitInPool u : units) {
            double distance = Double.MAX_VALUE;
            Point2d unitPos = u.unit().getPosition().toPoint2d();
            Map.Entry<Point2d, List<UnitInPool>> targetCluster = null;

            // Find the cluster this mineral patch is closest to.
            for (Map.Entry<Point2d, List<UnitInPool>> cluster : clusters.entrySet()) {
                double d = unitPos.distance(cluster.getKey());
                boolean isSameElevation = isSameElevation(observation, unitPos, cluster.getKey());

                if (d < distance && (!isElevationResticted || isSameElevation)) {
                    distance = d;
                    targetCluster = cluster;
                }
            }

            // If the target cluster is some distance away don't use it.
            if (targetCluster == null || distance > distanceApart) {
                ArrayList<UnitInPool> unitsInCluster = new ArrayList<>();
                unitsInCluster.add(u);
                clusters.put(unitPos, unitsInCluster);
                continue;
            }

            // Otherwise append to that cluster and update it's center of mass.
            if (targetCluster.getValue() == null) {
                targetCluster.setValue(new ArrayList<>());
            }
            targetCluster.getValue().add(u);
            Point2d centerOfCluster = getCenterPos(targetCluster.getValue());
            clusters.put(centerOfCluster, clusters.remove(targetCluster.getKey()));
        }
        return clusters;
    }

    public static Point2d getCenterPos(List<UnitInPool> unitList) {
        float minX, maxX, minY, maxY;
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = 0;
        for (UnitInPool u : unitList) {
            Point2d p = u.unit().getPosition().toPoint2d();
            minX = Math.min(p.getX(), minX);
            maxX = Math.max(p.getX(), maxX);
            minY = Math.min(p.getY(), minY);
            maxY = Math.max(p.getY(), maxY);
        }
        return Point2d.of((minX+maxX)/2f, (minY+maxY)/2f);
    }

    public static List<Point2d> calculateExpansionLocations(ObservationInterface observation) {
        List<UnitInPool> resources = observation.getUnits(unitInPool -> {
            Set<UnitType> nodes = new HashSet<>(asList(
                    Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750,
                    Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750,
                    Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750,
                    Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750,
                    Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750,
                    Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750,
                    Units.NEUTRAL_VESPENE_GEYSER, Units.NEUTRAL_PROTOSS_VESPENE_GEYSER,
                    Units.NEUTRAL_SPACE_PLATFORM_GEYSER, Units.NEUTRAL_PURIFIER_VESPENE_GEYSER,
                    Units.NEUTRAL_SHAKURAS_VESPENE_GEYSER, Units.NEUTRAL_RICH_VESPENE_GEYSER
            ));
            return nodes.contains(unitInPool.unit().getType());
        });

        List<Point2d> expansionLocations = new ArrayList<>();
        Map<Point2d, List<UnitInPool>> clusters = cluster(observation, resources, 15);
        for (Map.Entry<Point2d, List<UnitInPool>> cluster : clusters.entrySet()) {

            Point2d basePos = cluster.getKey();
            List<UnitInPool> nodes = cluster.getValue();

            //estimate base position
            basePos = estimateBasePos(basePos, nodes);

            //adjust basePos by grid restraints on each resource node in the cluster
            while (true) {
                Point2d finalBasePos = basePos;
                nodes = nodes.stream()
                        .sorted(Comparator.comparing(u -> u.unit().getPosition().toPoint2d().distance(finalBasePos)))
                        .collect(Collectors.toList());
                Point2d adjustedPoint = pushAwayFromNodes(basePos, nodes);
                if (adjustedPoint != null) {
                    basePos = adjustedPoint;
                    continue;
                }
                adjustedPoint = pullTowardsNodes(basePos, nodes);
                if (adjustedPoint != null) {
                    basePos = adjustedPoint;
                    continue;
                }
                break;
            }
            expansionLocations.add(basePos);
        }
        expansionLocations.forEach(point2d -> DebugHelper.draw3dBox(point2d, Color.GREEN, 2.5f));
        Bot.DEBUG.sendDebug();
        return expansionLocations;
    }

    private static Point2d estimateBasePos(Point2d basePos, List<UnitInPool> nodes) {
        for (int i=0; i<6; i++) {
            Point2d finalBestGuess = basePos;
            Point2d closestNodePos = nodes.stream()
                    .min(Comparator.comparing(node -> UnitUtils.getDistance(node.unit(), finalBestGuess))).get()
                    .unit().getPosition().toPoint2d();
            basePos = Position.towards(closestNodePos, basePos, 6.2f);
        }
        basePos = Position.toHalfPoint(Point2d.of(basePos.getX(), basePos.getY()));
        return basePos;
    }

    private static Point2d pullTowardsNodes(Point2d basePos, List<UnitInPool> nodes) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            UnitInPool node = nodes.get(i);
            Point2d nodePos = Position.toNearestHalfPoint(node.unit().getPosition().toPoint2d());
            boolean isMineralNode = node.unit().getType().toString().contains("MINERAL");
            float xMinDistCenter = isMineralNode ? 6.5f : 7;
            float yMinDistCenter = isMineralNode ? 6f : 7;
            float xMaxDist = isMineralNode ? 7.5f : 7;
            float yMaxDist = 7;
            float xDist = Math.abs(nodePos.getX() - basePos.getX());
            float yDist = Math.abs(nodePos.getY() - basePos.getY());
            if (xDist < yDist) {
                if (xDist < xMinDistCenter && yDist > yMaxDist) {
                    return moveYFromNodeBy(basePos, nodePos, yMaxDist);
                }
            }
            else {
                if (yDist < yMinDistCenter && xDist > xMaxDist) {
                    return moveXFromNodeBy(basePos, nodePos, xMaxDist);
                }
            }
        }
        return null;
    }

    private static Point2d pushAwayFromNodes(Point2d basePos, List<UnitInPool> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            UnitInPool node = nodes.get(i);
            Point2d nodePos = Position.toNearestHalfPoint(node.unit().getPosition().toPoint2d());
            boolean isMineralNode = node.unit().getType().toString().contains("MINERAL");
            float xMinDistCenter = isMineralNode ? 6.5f : 7;
            float xMinDistCorner = isMineralNode ? 5.5f : 6;
            float yMinDistCenter = isMineralNode ? 6f : 7;
            float yMinDistCorner = isMineralNode ? 5f : 6;
            float xDist = Math.abs(nodePos.getX() - basePos.getX());
            float yDist = Math.abs(nodePos.getY() - basePos.getY());
            if (xDist < yDist) {
                if (xDist < xMinDistCorner && yDist < yMinDistCenter) {
                    return moveYFromNodeBy(basePos, nodePos, yMinDistCenter);
                }
                else if (xDist < xMinDistCenter && yDist < yMinDistCorner) {
                    return moveYFromNodeBy(basePos, nodePos, yMinDistCorner);
                }
            }
            else {
                if (yDist < yMinDistCorner && xDist < xMinDistCenter) {
                    return moveXFromNodeBy(basePos, nodePos, xMinDistCenter);
                }
                else if (yDist < yMinDistCenter && xDist < xMinDistCorner) {
                    return moveXFromNodeBy(basePos, nodePos, xMinDistCorner);
                }
            }
        }
        return null;
    }

    private static Point2d moveXFromNodeBy(Point2d origin, Point2d nodePos, float distance) {
        float newX = 0;
        if (origin.getX() < nodePos.getX()) {
            newX = nodePos.getX() - distance;
        }
        else {
            newX = nodePos.getX() + distance;
        }
        return Point2d.of(newX, origin.getY());
    }

    private static Point2d moveYFromNodeBy(Point2d origin, Point2d nodePos, float distance) {
        float newY = 0;
        if (origin.getY() < nodePos.getY()) {
            newY = nodePos.getY() - distance;
        }
        else {
            newY = nodePos.getY() + distance;
        }
        return Point2d.of(origin.getX(), newY);
    }

    private static int findPosition(List<Point2d> expansionLocations, List<UnitInPool> nodes, Point2d nodeMidPoint, Point2d bestGuess) {
        int queryCount = 0;
        List<Point2d> possiblePositions = Position.getSpiralList(bestGuess, 2)
                .stream()
                .sorted(Comparator.comparing(p -> p.distance(nodeMidPoint)))
                .collect(Collectors.toList());
        for (Point2d attemptPos : possiblePositions) {
            if (nodes.stream().noneMatch(node -> UnitUtils.getDistance(node.unit(), attemptPos) < 6)) {
                queryCount++;
                if (Bot.QUERY.placement(Abilities.BUILD_NEXUS, attemptPos)) {
                    expansionLocations.add(attemptPos);
                    DebugHelper.drawBox(attemptPos, Color.GREEN, 2.5f);
                    System.out.println("query count for this base is " + queryCount);
                    return queryCount;
                }
            }
        }
        return -1;
    }

    private static Point2d bufferPoint(Point2d origin, Point2d awayFrom, boolean onXAxis, float distanceBuffer) {
        if (onXAxis) {
            float newX = (awayFrom.getX() < origin.getX()) ? (awayFrom.getX() + distanceBuffer) : (awayFrom.getX() - distanceBuffer);
            return Point2d.of(newX, origin.getY());
        }
        else {
            float newY = (awayFrom.getY() < origin.getY()) ? (awayFrom.getY() + distanceBuffer) : (awayFrom.getY() - distanceBuffer);
            return Point2d.of(origin.getX(), newY);
        }
    }

    private static Point2d nearestHalfPoint(Point2d point) {
        return Point2d.of(roundToNearestHalf(point.getX()), roundToNearestHalf(point.getY()));
    }

    private static float roundToNearestHalf(float number) {
        return Math.round(number * 2) / 2;
    }


    private static boolean isMineralNode(UnitInPool node) {
        return node.unit().getType().toString().contains("MINERAL_FIELD");
    }

    private static boolean isGeyserNode(UnitInPool node) {
        return node.unit().getType().toString().contains("GEYSER");
    }

    private static boolean isSameElevation(ObservationInterface observation, Point2d p1, Point2d p2) {
        return Math.abs(observation.terrainHeight(p1) - observation.terrainHeight(p2)) < 1;

    }

}
