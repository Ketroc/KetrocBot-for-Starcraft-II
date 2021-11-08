package com.ketroc.utils;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.geometry.Position;

public class InfluenceMaps {

    public static boolean[][] pointDetected;
    public static boolean[][] pointInHellionRange;
    public static boolean[][] pointInBansheeRange;
    public static boolean[][] pointInRavenCastRange;
    public static boolean[][] enemyInVikingRange;
    public static boolean[][] enemyInMissileTurretRange;
    public static boolean[][] pointInMarineRange;
    public static boolean[][] pointInEnemyVision;
    public static int[][] pointThreatToAirValue;
    public static boolean[][] pointThreatToAir;
    public static int[][] pointThreatToAirFromGround;
    public static int[][] pointThreatToAirPlusBufferValue;
    public static boolean[][] pointThreatToAirPlusBuffer;
    public static float[][] pointSupplyInSeekerRange;
    public static int[][] pointThreatToGroundValue;
    public static int[][] pointThreatToGroundPlusBufferValue;
    public static boolean[][] pointThreatToGroundPlusBuffer;
    public static int[][] pointDamageToGroundValue;  //1shot damage potential
    public static int[][] pointDamageToAirValue;  //1shot damage potential
    public static boolean[][] pointThreatToGround;
    public static boolean[][] pointPersistentDamageToGround;
    public static int[][] pointPFTargetValue; //1.5 splash radius range (PF is 1.25, tank is 1.5 ... +enemy radius and rounding effects)
    public static boolean[][] pointGroundUnitWithin13;
    public static boolean[][] pointRaiseDepots;
    public static boolean[][] pointVikingsStayBack;
    public static final boolean[][] pointInMainBase = new boolean[800][800];
    public static final boolean[][] pointInEnemyMainBase = new boolean[800][800];
    public static final boolean[][] pointInNat = new boolean[800][800];
    public static final boolean[][] pointInNatExcludingBunkerRange = new boolean[800][800];
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

    //return highest threat of the 4 corners of the structure
    public static int getThreatToStructure(Unit structure) {
        return getThreatToStructure(pointThreatToGroundValue, structure);
    }

    //return highest threat of the 4 corners of the structure
    public static int getThreatToStructure(Units structureType, Point2d structurePos) {
        return getThreatToStructure(pointThreatToGroundValue, structureType, structurePos);
    }

    //return highest threat of the 4 corners of the structure
    public static int getAirThreatToStructure(Units structureType, Point2d structurePos) {
        return getThreatToStructure(pointThreatToAirValue, structureType, structurePos);
    }

    //return highest threat of the 4 corners of the structure
    public static int getAirThreatToStructure(Unit structure) {
        return getThreatToStructure(pointThreatToAirValue, structure);
    }

    //return highest threat of the 4 corners of the structure
    public static int getGroundThreatToStructure(Units structureType, Point2d structurePos) {
        return getThreatToStructure(pointThreatToGroundValue, structureType, structurePos);
    }

    //return highest threat of the 4 corners of the structure
    public static int getGroundThreatToStructure(Unit structure) {
        return getThreatToStructure(pointThreatToGroundValue, structure);
    }

    //return highest threat of the 4 corners of the structure
    private static int getThreatToStructure(int[][] map, Unit structure) {
        return getThreatToStructure(map, (Units)structure.getType(), structure.getPosition().toPoint2d());
    }

    //return highest threat of the 4 corners of the structure
    private static int getThreatToStructure(int[][] map, Units structureType, Point2d structurePos) {
        structurePos = Position.toNearestHalfPoint(structurePos);
        float x = structurePos.getX();
        float y = structurePos.getY();
        float radius = UnitUtils.getStructureRadius(structureType);
        return Math.max(getValue(map, x + radius, y + radius),
                Math.max(getValue(map, x - radius, y + radius),
                        Math.max(getValue(map, x + radius, y - radius),
                                getValue(map, x - radius, y - radius))));
    }

    public static int toMapCoord(float coord) {
        return Math.round(coord*2);
    }
}
