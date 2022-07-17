package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Rectangle;
import com.ketroc.managers.ArmyManager;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.ExpansionClearing;
import com.ketroc.models.Ignored;
import com.ketroc.models.StructureScv;
import com.ketroc.strategies.Strategy;

public class DebugHelper {
    public static float z;
    private static final int TEXT_SIZE = 18;
    public static boolean isDebugOn;
    public static boolean doTestingSpawns = true;
    private static int lineNum;

    public static void onGameStart() {
        z = Bot.OBS.terrainHeight(PosConstants.baseLocations.get(0)) + 0.5f;
    }

    public static void onStep() {
        if (doTestingSpawns) {
            testingStuff();
        }
        if (!isDebugOn) {
            return;
        }
        displayGameInfo();
        Bot.DEBUG.sendDebug();
        lineNum = 0;
    }


    private static void testingStuff() {
        //spawn at start
        if (Time.at(1)) {
//            Bot.DEBUG.debugCreateUnit(Units.TERRAN_CYCLONE, PosConstants.baseLocations.get(1), Bot.myId, 3);
//            Bot.DEBUG.debugCreateUnit(Units.TERRAN_RAVEN, PosConstants.baseLocations.get(1), Bot.myId, 1);
//            Bot.DEBUG.debugCreateUnit(Units.ZERG_ROACH_BURROWED, PosConstants.BUNKER_NATURAL, Bot.enemyId, 15);
        }

        //spawn every minute
        if (Time.nowFrames() > Time.toFrames("3:00") && Time.periodic(1)) {
//            GameCache.baseList.stream().filter(Base::isMyBase).forEach(base ->
//                    Bot.DEBUG.debugCreateUnit(Units.TERRAN_WIDOWMINE_BURROWED, base.getResourceMidPoint(), Bot.enemyId, 1));
//            Bot.DEBUG.debugCreateUnit(Units.TERRAN_GHOST, PosConstants.BUNKER_NATURAL, Bot.myId, 5);
            Bot.DEBUG.debugCreateUnit(Units.ZERG_OVERLORD, PosConstants.proxyBarracksPos, Bot.enemyId, 3);
            Bot.DEBUG.debugCreateUnit(Units.ZERG_ROACH, PosConstants.proxyBarracksPos, Bot.enemyId, 14);
//            Bot.DEBUG.debugCreateUnit(Units.ZERG_VIPER, PosConstants.proxyBarracksPos, Bot.enemyId, 2);
//            UnitMicroList.getUnitSubList(Cyclone.class)
//                    .forEach(cyclone -> {
//                        if (Math.random() > 0.35) Bot.DEBUG.debugKillUnit(cyclone.unit.unit());
//                    });
//            GameCache.bansheeList
//                    .forEach(banshee -> {
//                        if (Math.random() > 0.35) Bot.DEBUG.debugKillUnit(banshee);
//                    });

            Bot.DEBUG.sendDebug();
        }

        if (Time.at(Time.toFrames(5))) {
//            Bot.DEBUG.debugCreateUnit(Units.TERRAN_GHOST_ACADEMY, LocationConstants.mainBaseMidPos, Bot.myId, 1);
//            Bot.DEBUG.debugCreateUnit(Units.TERRAN_FACTORY, LocationConstants.mainBaseMidPos, Bot.myId, 1);
//            Bot.DEBUG.debugGiveAllResources();
//            //GameCache.baseList.get(0).scvReport();
//                Point2d pylonPos = Position.towards(LocationConstants.baseLocations.get(1), LocationConstants.baseLocations.get(0), -5);
//                pylonPos = Position.towards(pylonPos, LocationConstants.baseLocations.get(3), -5);
//                Bot.DEBUG.debugCreateUnit(Units.PROTOSS_PYLON, LocationConstants.BUNKER_NATURAL, myId, 1);
//                GameCache.baseList.forEach(base -> {
//                    DebugHelper.drawBox(base.getCcPos(), Color.WHITE, 2.5f);
//                    DebugHelper.drawBox(base.getResourceMidPoint(), Color.WHITE, 0.3f);
//                    base.getMineralPatches().forEach(patch -> {
//                        DebugHelper.drawLine(patch.getByNodePos(), patch.getByCCPos(), Color.GRAY);
//                    });
//                    base.getGases().forEach(patch -> {
//                        DebugHelper.drawLine(patch.getByNodePos(), patch.getByCCPos(), Color.GRAY);
//                    });
//                    base.getTurrets().forEach(turret -> {
//                        DebugHelper.drawBox(turret.getPos(), Color.GREEN, 1f);
//                    });
//                });
//            Bot.DEBUG.sendDebug();
        }

        if (Time.at(Time.toFrames(6))) {
//            Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_GHOST_ACADEMY)
//                    .forEach(u -> ActionHelper.unitCommand(u.unit(), Abilities.BUILD_NUKE, false));
//            Bot.DEBUG.sendDebug();
        }
    }

    private static void displayGameInfo() {
        for (int i = 0; i< GameCache.baseList.size(); i++) {
            if (GameCache.baseList.get(i).isEnemyBase) {
                DebugHelper.addInfoLine("enemy base index: " + i);
                break;
            }
        }
        DebugHelper.addInfoLine("scvs/gas: " + WorkerManager.numScvsPerGas);
        DebugHelper.addInfoLine("");


        for (int i = 0; i < ExpansionClearing.expoClearList.size(); i++) {
            DebugHelper.addInfoLine("base: " + ExpansionClearing.expoClearList.get(i).expansionPos +
                    " raven: " +
                    (ExpansionClearing.expoClearList.get(i).raven != null
                            ? ExpansionClearing.expoClearList.get(i).raven.unit.unit().getPosition().toPoint2d()
                            : "none"));
        }
        DebugHelper.addInfoLine("# Scvs Ignored: " + Ignored.ignoredUnits.stream()
                .filter(ignored -> Bot.OBS.getUnit(ignored.unitTag) != null)
                .map(ignored -> Bot.OBS.getUnit(ignored.unitTag).unit().getType())
                .filter(unitType -> unitType == Units.TERRAN_SCV)
                .count());
        DebugHelper.addInfoLine("# Scvs Building: " + StructureScv.scvBuildingList.stream()
                .map(structureScv -> structureScv.getScv().unit().getType())
                .filter(unitType -> unitType == Units.TERRAN_SCV)
                .count());
        DebugHelper.addInfoLine("doOffense: " + ArmyManager.doOffense);
        DebugHelper.addInfoLine("banshees: " + GameCache.bansheeList.size());
        DebugHelper.addInfoLine("liberators: " + GameCache.liberatorList.size());
        DebugHelper.addInfoLine("ravens: " + GameCache.ravenList.size());
        DebugHelper.addInfoLine("vikings: " + GameCache.vikingList.size());
        if (PosConstants.opponentRace == Race.PROTOSS) {
            DebugHelper.addInfoLine("tempests: " + UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size());
        }

        UnitInPool tempest = UnitUtils.getClosestEnemyUnitOfType(Units.PROTOSS_TEMPEST, ArmyManager.retreatPos);
        if (tempest != null) {
            DebugHelper.addInfoLine("vikings near tempest: " + UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER,
                    tempest.unit().getPosition().toPoint2d(), Strategy.DIVE_RANGE).size());
        }

        DebugHelper.addInfoLine("vikings wanted: " + ArmyManager.calcNumVikingsNeeded()*0.7);
        DebugHelper.addInfoLine("Purchase Queue: " + KetrocBot.purchaseQueue.size());
        DebugHelper.addInfoLine("Switches.enemyCanProduceAir: " + Switches.enemyCanProduceAir);
        if (ArmyManager.attackGroundPos != null) {
            DebugHelper.draw3dBox(ArmyManager.attackGroundPos, Color.YELLOW, 0.6f);
        }
        for (int i = 0; i < KetrocBot.purchaseQueue.size() && i < 5; i++) {
            DebugHelper.addInfoLine(KetrocBot.purchaseQueue.get(i).getType());
        }

//        DebugHelper.draw3dBox(LocationConstants.enemyMineralPos, Color.BLUE, 0.67f);
//        DebugHelper.draw3dBox(LocationConstants.pointOnEnemyRamp, Color.GREEN, 0.5f);
//        DebugHelper.draw3dBox(LocationConstants.pointOnMyRamp, Color.GREEN, 0.5f);
    }

    public static void drawRect(float top, float bottom, float left, float right, Color color) {
        if (!isDebugOn) {
            return;
        }
        float z = Bot.OBS.terrainHeight(Point2d.of(left, bottom)) + 0.2f;
        Bot.DEBUG.debugBoxOut(Point.of(left, bottom, z), Point.of(right, top, z), color);
    }

    public static void drawRect(Rectangle rect, Color color) {
        drawRect(rect.getTop(), rect.getBottom(), rect.getLeft(), rect.getRight(), color);
    }

    public static void drawSphere(Point2d pos, Color color, float radius) {
        if (!isDebugOn) {
            return;
        }
        float z = Bot.OBS.terrainHeight(pos) + 0.2f;
        Bot.DEBUG.debugSphereOut(Point.of(pos.getX(),pos.getY(), z), radius, color);
    }

    public static void drawLine(Point2d pos1, Point2d pos2, Color color) {
        if (!isDebugOn) {
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
        if (!isDebugOn) {
            return;
        }
        Bot.DEBUG.debugLineOut(pos1, pos2, color);
    }

    public static void draw3dBox(Point2d pos, Color color, float radius) {
        if (!isDebugOn) {
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
        drawBox(x, y, color, radius, true);
    }

    public static void drawBox(float x, float y, Color color, float radius, boolean atTerrainLevel) {
        if (!isDebugOn) {
            return;
        }
        Bot.DEBUG.debugBoxOut(
                Point.of(x-radius,y-radius, Bot.OBS.terrainHeight(Point2d.of(x-radius,y-radius)) + 0.2f),
                Point.of(x+radius,y+radius, Bot.OBS.terrainHeight(Point2d.of(x+radius,y+radius)) + 0.2f),
                color
        );
    }

    public static void drawBox(Point2d pos, Color color, float radius) {
        drawBox(pos, color, radius, true);
    }


    public static void drawBox(Point2d pos, Color color, float radius, boolean isAtTerrainLevel) {
        if (!isDebugOn) {
            return;
        }
        float z = Bot.OBS.terrainHeight(pos) + 0.2f;
        float x = pos.getX();
        float y = pos.getY();
        Bot.DEBUG.debugBoxOut(Point.of(x-radius,y-radius, z),
                Point.of(x+radius,y+radius, z),
                color);
    }


    public static void drawText(String text, Point2d pos, Color color) {
        drawText(text, pos, color, TEXT_SIZE);
    }

    public static void drawText(String text, Point2d pos, Color color, int textSize) {
        if (!isDebugOn) {
            return;
        }
        float x = pos.getX();
        float y = pos.getY();
        Bot.DEBUG.debugTextOut(text, Point.of(x, y, Bot.OBS.terrainHeight(pos) + 0.2f), color, textSize);
    }

    public static void addInfoLine(String text) {
        if (!isDebugOn) {
            return;
        }
        Bot.DEBUG.debugTextOut(text, Point2d.of(0.1f, ((100f + 20f * lineNum++) / 1080f)), Color.WHITE, 12);
    }

    public static void drawText(String text, float x, float y, Color color) {
        drawText(text, x, y, color, TEXT_SIZE);
    }

    public static void drawText(String text, float x, float y, Color color, int textSize) {
        if (!isDebugOn) {
            return;
        }
        //Bot.DEBUG.debugTextOut(text, Point.of(x, y, z), color, textSize);
        Bot.DEBUG.debugTextOut(text, Point.of(x, y, Bot.OBS.terrainHeight(Point2d.of(x, y)) + 0.3f), color, textSize);
    }

    public static void boxUnit(Unit unit) {
        boxUnit(unit, Color.GREEN);
    }

    public static void boxUnit(Unit unit, Color color) {
        if (!isDebugOn) {
            return;
        }
        draw3dBox(unit.getPosition().toPoint2d(), color, 0.5f);
    }

    public static void gridTheMap() {
        boolean isDebugOnSave = isDebugOn;
        isDebugOn = true;
        for (int x = PosConstants.MIN_X; x< PosConstants.MAX_X; x++) {
            for (int y = PosConstants.MIN_Y; y < PosConstants.MAX_Y; y++) {
                if (Bot.OBS.isPathable(Point2d.of(x, y))) {
                    drawBox(x, y, Color.GRAY, 0.5f);
                    if (Bot.OBS.terrainHeight(Point2d.of(x - 0.2f, y + 0.2f)) + 2 > Bot.OBS.terrainHeight(Point2d.of(x, y))) {
                        drawText(x + ",\n" + y, x - 0.2f, y + 0.2f, Color.WHITE, 8);
                    }
                }
            }
        }
        Bot.DEBUG.sendDebug();
        isDebugOn = isDebugOnSave;
    }

}
