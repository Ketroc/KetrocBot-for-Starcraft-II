package com.ketroc.utils;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.micro.Ghost;
import com.ketroc.micro.GhostBasic;
import io.vertx.codegen.annotations.Nullable;

public class InfluenceMaps {

    public static boolean[][] pointDetected;
    public static boolean[][] pointIn5RangeVsGround;
    public static boolean[][] point6RangevsGround;
    public static boolean[][] point2RangevsGround;
    public static boolean[][] pointInRavenCastRange;
    public static boolean[][] enemyInVikingRange;
    public static boolean[][] enemyInMissileTurretRange;
    public static boolean[][] pointIn5RangeVsBoth;
    public static boolean[][] pointIn7RangeVsBoth;
    public static boolean[][] pointInEnemyVision;
    public static int[][] pointThreatToAirValue;
    public static int[][] pointEnemyAttackersWith10Range;
    public static boolean[][] pointThreatToAir;
    public static int[][] pointThreatToAirFromGround;
    public static int[][] pointThreatToAirPlusBufferValue;
    public static boolean[][] pointThreatToAirPlusBuffer;
    public static float[][] pointSupplyInSeekerRange;
    public static float[][] pointEmpValue;
    public static float[][] pointAutoturretValue;
    public static int[][] pointThreatToGroundValue;
    public static int[][] pointThreatToGroundPlusBufferValue;
    public static boolean[][] pointThreatToGroundPlusBuffer;
    public static int[][] pointDamageToGroundValue;  //1shot damage potential
    public static int[][] pointDamageToAirValue;  //1shot damage potential
    public static boolean[][] pointThreatToGround;
    public static boolean[][] pointPersistentDamageToGround;
    public static boolean[][] pointPersistentDamageToAir;
    public static int[][] pointPFTargetValue; //1.5 splash radius range (PF is 1.25, tank is 1.5 ... +enemy radius and rounding effects)
    public static boolean[][] pointGroundUnitWithin13;
    public static boolean[][] pointRaiseDepots;
    public static boolean[][] pointVikingsStayBack;
    public static final boolean[][] pointInMainBase = new boolean[800][800];
    public static final boolean[][] pointIn2ndProductionArea = new boolean[800][800];
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

    @Nullable
    public static Point2d getBestEmpPos(Unit ghost) {
        Point2d ghostPos = Position.toNearestHalfPoint(ghost.getPosition().toPoint2d());
        Point2d bestEmpPos = getHighestEmpValuePos(ghostPos, Ghost.EMP_RANGE + ghost.getRadius());

        //now check for better position behind this target (find something good within 10 range, now try 11 range)
        if (bestEmpPos != null) {
            bestEmpPos = getHighestEmpValuePos(bestEmpPos, 2.5f);
        }
        return bestEmpPos;
    }

    @Nullable
    public static Point2d getHighestEmpValuePos(Point2d centerPos, float range) { //note threshold must be met
        int centerX = toMapCoord(centerPos.getX());
        int centerY = toMapCoord(centerPos.getY());
        int minX = (int)(centerX - range);
        int maxX = (int)(centerX + range);
        int minY = (int)(centerY - range);
        int maxY = (int)(centerY + range);

        int bestEmpX = 0;
        int bestEmpY = 0;
        float bestEmpValue = 0;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                float distance = (float)centerPos.distance(Point2d.of(x/2, y/2));
                if (distance <= range) {
                    float curEmpValue = getValue(pointEmpValue, x/2, y/2);
                    if (curEmpValue > bestEmpValue) {
                        bestEmpValue = curEmpValue;
                        bestEmpX = x;
                        bestEmpY = y;
                    }
                }
            }
        }
        return bestEmpValue < GhostBasic.getEmpThreshold() ? null : Point2d.of(bestEmpX/2, bestEmpY/2);
    }

    public static void visualizeMap(boolean[][] map, Color color) {
        int xMin = InfluenceMaps.toMapCoord(PosConstants.MIN_X);
        int xMax = InfluenceMaps.toMapCoord(PosConstants.MAX_X);
        int yMin = InfluenceMaps.toMapCoord(PosConstants.MIN_Y);
        int yMax = InfluenceMaps.toMapCoord(PosConstants.MAX_Y);
        for (int x = xMin + 1; x <= xMax - 1; x++) {
            for (int y = yMin + 1; y <= yMax - 1; y++) {
                if (map[x][y]) {
                    DebugHelper.drawBox(x / 2f, y / 2f, color, 0.25f);
                }
            }
        }
        Bot.DEBUG.sendDebug();
    }

    public static void visualizeMap(float[][] map, Color color) {
        int xMin = InfluenceMaps.toMapCoord(PosConstants.MIN_X);
        int xMax = InfluenceMaps.toMapCoord(PosConstants.MAX_X);
        int yMin = InfluenceMaps.toMapCoord(PosConstants.MIN_Y);
        int yMax = InfluenceMaps.toMapCoord(PosConstants.MAX_Y);
        for (int x = xMin + 1; x <= xMax - 1; x++) {
            for (int y = yMin + 1; y <= yMax - 1; y++) {
                if (map[x][y] > 0) {
                    DebugHelper.drawText(String.valueOf((int)map[x][y]), x / 2f, y / 2f, color);
                }
            }
        }
        Bot.DEBUG.sendDebug();
    }

    public static void visualizeMap(int[][] map, Color color) {
        int xMin = InfluenceMaps.toMapCoord(PosConstants.MIN_X);
        int xMax = InfluenceMaps.toMapCoord(PosConstants.MAX_X);
        int yMin = InfluenceMaps.toMapCoord(PosConstants.MIN_Y);
        int yMax = InfluenceMaps.toMapCoord(PosConstants.MAX_Y);
        for (int x = xMin + 1; x <= xMax - 1; x++) {
            for (int y = yMin + 1; y <= yMax - 1; y++) {
                if (map[x][y] > 0) {
                    DebugHelper.drawText(String.valueOf(map[x][y]), x / 2f, y / 2f, color);
                }
            }
        }
        Bot.DEBUG.sendDebug();
    }
}
