package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.LocationConstants;

public class DebugHelper {
    public static float z;
    private static final int TEXT_SIZE = 11;
    private static int lineNum;

    public static void onGameStart() {
        z = Bot.OBS.terrainHeight(LocationConstants.baseLocations.get(0)) + 0.5f;
    }

    public static void onStep() {
        lineNum = 0;
    }

    public static void drawBox(Point2d pos, Color color, float radius) {
        float z = Bot.OBS.terrainHeight(pos) + 0.5f;
        if (Bot.isDebugOn) {
            float x = pos.getX();
            float y = pos.getY();
            Bot.DEBUG.debugBoxOut(Point.of(x-radius,y-radius, z), Point.of(x+radius,y+radius, z), color);
        }
    }

    public static void draw3dBox(Point2d pos, Color color, float radius) {
        if (Bot.isDebugOn) {
            float x = pos.getX();
            float y = pos.getY();

            float left = x - radius;
            float right = x + radius;
            float bottom = y - radius;
            float top = y + radius;
            float up = z + 2;
            float down = z - 3;
            Bot.DEBUG.debugBoxOut(Point.of(left, bottom, up), Point.of(right, top, up), color);
            Bot.DEBUG.debugLineOut(Point.of(left, top, up), Point.of(left, top, down), color);
            Bot.DEBUG.debugLineOut(Point.of(left, bottom, up), Point.of(left, bottom, down), color);
            Bot.DEBUG.debugLineOut(Point.of(right, top, up), Point.of(right, top, down), color);
            Bot.DEBUG.debugLineOut(Point.of(right, bottom, up), Point.of(right, bottom, down), color);
        }
    }

    public static void drawBox(float x, float y, Color color, float radius) {
        if (Bot.isDebugOn) {
            Bot.DEBUG.debugBoxOut(Point.of(x-radius,y-radius, z), Point.of(x+radius,y+radius, z), color);
        }
    }

    public static void drawText(String text, Point2d pos, Color color) {
        if (Bot.isDebugOn) {
            float x = pos.getX();
            float y = pos.getY();
            Bot.DEBUG.debugTextOut(text, Point.of(x, y, z), color, TEXT_SIZE);
        }
    }

    public static void addInfoLine(String text) {
        if (Bot.isDebugOn) {
            Bot.DEBUG.debugTextOut(text, Point2d.of(0.1f, ((100f + 20f * lineNum++) / 1080f)), Color.WHITE, 12);
        }
    }

    public static void drawText(String text, float x, float y, Color color) {
        if (Bot.isDebugOn) {
            Bot.DEBUG.debugTextOut(text, Point.of(x, y, z), color, TEXT_SIZE);
        }
    }

    public static void boxUnit(Unit unit) {
        if (Bot.isDebugOn) {
            draw3dBox(unit.getPosition().toPoint2d(), Color.GREEN, 0.5f);
        }
    }
}
