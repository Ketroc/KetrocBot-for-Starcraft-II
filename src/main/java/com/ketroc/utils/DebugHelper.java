package com.ketroc.utils;

import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Rectangle;

public class DebugHelper {
    public static float z;
    private static final int TEXT_SIZE = 30;
    private static int lineNum;

    public static void onGameStart() {
        z = Bot.OBS.terrainHeight(LocationConstants.baseLocations.get(0)) + 0.5f;
    }

    public static void onStep() {
        lineNum = 0;
    }

    public static void drawBox(Point2d pos, Color color, float radius) {
        if (!Bot.isDebugOn) {
            return;
        }
        float z = Bot.OBS.terrainHeight(pos) + 0.2f;
        float x = pos.getX();
        float y = pos.getY();
        Bot.DEBUG.debugBoxOut(Point.of(x-radius,y-radius, z),
                Point.of(x+radius,y+radius, z),
                color);
    }

    public static void drawRect(float top, float bottom, float left, float right, Color color) {
        if (!Bot.isDebugOn) {
            return;
        }
        float z = Bot.OBS.terrainHeight(Point2d.of(left, bottom)) + 0.2f;
        Bot.DEBUG.debugBoxOut(Point.of(left, bottom, z), Point.of(right, top, z), color);
    }

    public static void drawRect(Rectangle rect, Color color) {
        drawRect(rect.getTop(), rect.getBottom(), rect.getLeft(), rect.getRight(), color);
    }

    public static void drawSphere(Point2d pos, Color color, float radius) {
        if (!Bot.isDebugOn) {
            return;
        }
        float z = Bot.OBS.terrainHeight(pos) + 0.2f;
        Bot.DEBUG.debugSphereOut(Point.of(pos.getX(),pos.getY(), z), radius, color);
    }

    public static void drawLine(Point2d pos1, Point2d pos2, Color color) {
        if (!Bot.isDebugOn) {
            return;
        }
        float z1 = Bot.OBS.terrainHeight(pos1) + 0.2f;
        float z2 = Bot.OBS.terrainHeight(pos2) + 0.2f;
        Bot.DEBUG.debugLineOut(
                Point.of(pos1.getX(), pos1.getY(), z1),
                Point.of(pos2.getX(), pos2.getY(), z2),
                color
        );
    }

    public static void drawLine(Point pos1, Point pos2, Color color) {
        if (!Bot.isDebugOn) {
            return;
        }
        Bot.DEBUG.debugLineOut(pos1, pos2, color);
    }

    public static void draw3dBox(Point2d pos, Color color, float radius) {
        if (!Bot.isDebugOn) {
            return;
        }

        //if point isn't over chasm, change z from default to terrainHeight
        float z = DebugHelper.z;
        float terrainHeight = Bot.OBS.terrainHeight(pos);
        if (Math.abs(z - terrainHeight) < 7) {
            z = terrainHeight;
        }
        float x = pos.getX();
        float y = pos.getY();

        float left = x - radius;
        float right = x + radius;
        float bottom = y - radius;
        float top = y + radius;
        float up = z + 3;
        float down = z - 8;
        Bot.DEBUG.debugBoxOut(Point.of(left, bottom, up), Point.of(right, top, up), color);
        Bot.DEBUG.debugLineOut(Point.of(left, top, up), Point.of(left, top, down), color);
        Bot.DEBUG.debugLineOut(Point.of(left, bottom, up), Point.of(left, bottom, down), color);
        Bot.DEBUG.debugLineOut(Point.of(right, top, up), Point.of(right, top, down), color);
        Bot.DEBUG.debugLineOut(Point.of(right, bottom, up), Point.of(right, bottom, down), color);
    }

    public static void drawBox(float x, float y, Color color, float radius) {
        if (Bot.isDebugOn) {
            Bot.DEBUG.debugBoxOut(
                    Point.of(x-radius,y-radius, Bot.OBS.terrainHeight(Point2d.of(x-radius,y-radius)) + 0.2f),
                    Point.of(x+radius,y+radius, Bot.OBS.terrainHeight(Point2d.of(x+radius,y+radius)) + 0.2f),
                    color
            );
        }
    }

    public static void drawText(String text, Point2d pos, Color color) {
        drawText(text, pos, color, TEXT_SIZE);
    }

    public static void drawText(String text, Point2d pos, Color color, int textSize) {
        if (!Bot.isDebugOn) {
            return;
        }
        float x = pos.getX();
        float y = pos.getY();
        Bot.DEBUG.debugTextOut(text, Point.of(x, y, z), color, textSize);
    }

    public static void addInfoLine(String text) {
        if (!Bot.isDebugOn) {
            return;
        }
        Bot.DEBUG.debugTextOut(text, Point2d.of(0.1f, ((100f + 20f * lineNum++) / 1080f)), Color.WHITE, 12);
    }

    public static void drawText(String text, float x, float y, Color color) {
        drawText(text, x, y, color, TEXT_SIZE);
    }

    public static void drawText(String text, float x, float y, Color color, int textSize) {
        if (!Bot.isDebugOn) {
            return;
        }
        //Bot.DEBUG.debugTextOut(text, Point.of(x, y, z), color, textSize);
        Bot.DEBUG.debugTextOut(text, Point.of(x, y, Bot.OBS.terrainHeight(Point2d.of(x, y)) + 0.3f), color, textSize);
    }

    public static void boxUnit(Unit unit) {
        if (!Bot.isDebugOn) {
            return;
        }
        draw3dBox(unit.getPosition().toPoint2d(), Color.GREEN, 0.5f);
    }
}
