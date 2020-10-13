package com.ketroc.terranbot;

import com.github.ocraft.s2client.protocol.spatial.Point2d;

public class InfluenceMaps {

    public static boolean[][] pointDetected;
    public static boolean[][] pointInBansheeRange;
    public static boolean[][] pointInVikingRange;
    public static int[][] pointThreatToAir;
    public static int[][] pointThreatToAirFromGround;
    public static boolean[][] pointThreatToAirPlusBuffer;
    public static float[][] pointSupplyInSeekerRange;
    public static int[][] pointThreatToGround;
    public static int[][] pointPFTargetValue;
    public static boolean[][] pointGroundUnitWithin13;
    public static boolean[][] pointRaiseDepots;
    public static boolean[][] pointVikingsStayBack;
    public static final boolean[][] pointInMainBase = new boolean[800][800];
    public static final boolean[][] pointInEnemyMainBase = new boolean[800][800];
    public static final boolean[][] pointInNat = new boolean[800][800];
    public static final boolean[][] pointInEnemyNat = new boolean[800][800];

    public static boolean getValue(boolean[][] map, Point2d point) {
        return getValue(map, point.getX(), point.getY());
    }

    public static boolean getValue(boolean[][] map, float x, float y) {
        return map[toMapCoord(x)][toMapCoord(y)];
    }

    public static float getValue(float[][] map, Point2d point) {
        return getValue(map, point.getX(), point.getY());
    }

    public static float getValue(float[][] map, float x, float y) {
        return map[toMapCoord(x)][toMapCoord(y)];
    }

    public static int getValue(int[][] map, Point2d point) {
        return getValue(map, point.getX(), point.getY());
    }

    public static int getValue(int[][] map, float x, float y) {
        return map[toMapCoord(x)][toMapCoord(y)];
    }

    public static int toMapCoord(float coord) {
        return Math.round(coord*2);
    }
}
