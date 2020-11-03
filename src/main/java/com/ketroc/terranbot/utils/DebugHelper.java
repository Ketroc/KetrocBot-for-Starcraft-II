package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.LocationConstants;

public class DebugHelper {
    public static float z;
    private static final int TEXT_SIZE = 11;

    public static void onGameStart() {
        z = Bot.OBS.terrainHeight(LocationConstants.baseLocations.get(0)) + 0.2f;
    }

    public static void drawBox(Point2d pos, Color color, float radius) {
        //z = Bot.OBS.terrainHeight(pos) + 0.5f;
        if (Bot.isDebugOn) {
            float x = pos.getX();
            float y = pos.getY();
            Bot.DEBUG.debugBoxOut(Point.of(x-radius,y-radius, z), Point.of(x+radius,y+radius, z), color);
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

    public static void drawText(String text, float x, float y, Color color) {
        if (Bot.isDebugOn) {
            Bot.DEBUG.debugTextOut(text, Point.of(x, y, z), color, TEXT_SIZE);
        }
    }

}
