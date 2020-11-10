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

            int size = targetCluster.getValue().size();
            Point2d centerOfMass = targetCluster.getKey().mul(size - 1).add(unitPos).div(size);
            clusters.put(centerOfMass, clusters.remove(targetCluster.getKey()));
        }
        return clusters;
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
        int queryCount = 0;
        for (Map.Entry<Point2d, List<UnitInPool>> cluster : clusters.entrySet()) {
            Point2d nodeMidPoint = cluster.getKey();
            Point2d basePos = Position.toHalfPoint(nodeMidPoint);
            List<UnitInPool> nodes = cluster.getValue();
            //DebugHelper.drawBox(basePos, Color.RED, 0.25f);
//            nodes = nodes.stream()
//                    .sorted(Comparator.comparing(node -> UnitUtils.getDistance(node.unit(), nodeMidPoint)))
//                    .collect(Collectors.toList());
//            Point2d bestGuess = Point2d.of(0,0);
//            for (int i=0; i<4; i++) {
//                Point2d nodePos = nodes.get(i).unit().getPosition().toPoint2d();
//                bestGuess = bestGuess.add(Position.towards(nodePos, nodeMidPoint, 8.5f));
//            }
//            bestGuess = bestGuess.div(4);


            for (int i=0; i<6; i++) {
                Point2d finalBestGuess = basePos;
                Point2d closestNodePos = nodes.stream()
                        .min(Comparator.comparing(node -> UnitUtils.getDistance(node.unit(), finalBestGuess))).get()
                        .unit().getPosition().toPoint2d();
                basePos = Position.towards(closestNodePos, basePos, 5f);
            }
            basePos = Position.toHalfPoint(Point2d.of(basePos.getX(), basePos.getY()));
            //DebugHelper.drawBox(basePos, Color.YELLOW, 0.26f);

            boolean didAdjust = true;
            while (didAdjust) {
                didAdjust = false;
                nodes = nodes.stream()
                        .sorted(Comparator.comparing(u -> u.unit().getPosition().toPoint2d().distance(nodeMidPoint)))
                        .collect(Collectors.toList());
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
                    DebugHelper.drawBox(basePos, Color.YELLOW, 0.26f);
                    DebugHelper.drawBox(nodePos, Color.RED, 0.26f);
                    Bot.DEBUG.sendDebug();
                    int skldfj = 0;
                    if (xDist < yDist) {
                        if (xDist < xMinDistCorner && yDist < yMinDistCenter) {
                            basePos = moveYFromNodeBy(basePos, nodePos, yMinDistCenter);
                            didAdjust = true;
                            break;
                        } else if (xDist < xMinDistCenter && yDist < yMinDistCorner) {
                            basePos = moveYFromNodeBy(basePos, nodePos, yMinDistCorner);
                            didAdjust = true;
                            break;
                        }
                    } else {
                        if (yDist < yMinDistCorner && xDist < xMinDistCenter) {
                            basePos = moveXFromNodeBy(basePos, nodePos, xMinDistCenter);
                            didAdjust = true;
                            break;
                        } else if (yDist < yMinDistCenter && xDist < xMinDistCorner) {
                            basePos = moveXFromNodeBy(basePos, nodePos, xMinDistCorner);
                            didAdjust = true;
                            break;
                        }
                    }
                }
            }
            DebugHelper.drawBox(basePos, Color.GREEN, 2.5f);



//TODO: this is the best guess that is 95% accurate

//            Point2d bestGuess = nodeMidPoint;
//            for (int i=0; i<6; i++) {
//                Point2d finalBestGuess = bestGuess;
//                Point2d closestNodePos = nodes.stream()
//                        .min(Comparator.comparing(node -> UnitUtils.getDistance(node.unit(), finalBestGuess))).get()
//                        .unit().getPosition().toPoint2d();
//                bestGuess = Position.towards(closestNodePos, bestGuess, 6.2f);
//            }
//            bestGuess = Point2d.of((int)bestGuess.getX() + 0.5f, (int)bestGuess.getY() + 0.5f);
//            DebugHelper.drawBox(bestGuess, Color.YELLOW, 0.26f);
//
//            queryCount += findPosition(expansionLocations, nodes, nodeMidPoint, bestGuess);










//            boolean moveUp = nodes.stream().filter(u -> u.unit().getPosition().toPoint2d().getY() < nodeMidPoint.getY()).count() > nodes.size()/2f;
//            boolean moveRight = nodes.stream().filter(u -> u.unit().getPosition().toPoint2d().getX() < nodeMidPoint.getX()).count() > nodes.size()/2f;
//            for (UnitInPool node : cluster.getValue()) {
//                Point2d nodePos = nearestHalfPoint(node.unit().getPosition().toPoint2d());
//
//                //buffer mineral node
//                if (isMineralNode(node)) {
//                    //move vertically
//                    if ((moveUp && nodePos.getY() < basePos.getY()) || (!moveUp && nodePos.getY() > basePos.getY())) {
//                        if ((nodePos.getX() - basePos.getX()) < 6) {
//                            float yDistance = Math.abs(nodePos.getY() - basePos.getY());
//                            if (yDistance < 5.5) {
//                                basePos = bufferPoint(basePos, nodePos, false, 6);
//                            } else if (yDistance < 6.5) {
//                                basePos = bufferPoint(basePos, nodePos, false, 5);
//                            }
//                        }
//                    }
//                    //move horizontally
//                    if ((moveRight && nodePos.getX() < basePos.getX()) || (!moveRight && nodePos.getX() > basePos.getX())) {
//                        if (Math.abs(nodePos.getY() - basePos.getY()) < 6.5) {
//                            float xDistance = Math.abs(nodePos.getX() - basePos.getX());
//                            if (xDistance < 5) {
//                                basePos = bufferPoint(basePos, nodePos, true, 6.5f);
//                            } else if (xDistance < 6) {
//                                basePos = bufferPoint(basePos, nodePos, true, 5.5f);
//                            }
//                        }
//                    }
//                }
//
//                //buffer gas geyser
//                else {
//                    //move vertically
//                    if (Math.abs(nodePos.getX() - basePos.getX()) < 7) {
//                        float yDistance = Math.abs(nodePos.getY() - basePos.getY());
//                        if (yDistance < 6) {
//                            basePos = bufferPoint(basePos, nodePos, false, 7);
//                        } else if (yDistance < 7) {
//                            basePos = bufferPoint(basePos, nodePos, false, 6);
//                        }
//                    }
//                    //move horizontally
//                    if (Math.abs(nodePos.getY() - basePos.getY()) < 7) {
//                        float xDistance = Math.abs(nodePos.getX() - basePos.getX());
//                        if (xDistance < 6) {
//                            basePos = bufferPoint(basePos, nodePos, true, 7);
//                        } else if (xDistance < 7) {
//                            basePos = bufferPoint(basePos, nodePos, true, 6);
//                        }
//                    }
//                }
//            }
//            expansionLocations.add(basePos);
//            DebugHelper.drawBox(basePos, Color.GREEN, 2.5f);

        }
        Bot.DEBUG.sendDebug();
        return expansionLocations;
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
        return node.unit().toString().contains("MINERAL_FIELD");
    }

    private static boolean isGeyserNode(UnitInPool node) {
        return node.unit().getType().toString().contains("VESPENE_GEYSER");
    }

    private static boolean isSameElevation(ObservationInterface observation, Point2d p1, Point2d p2) {
        return Math.abs(observation.terrainHeight(p1) - observation.terrainHeight(p2)) < 1;

    }

}
