package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.BuildManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocationConstants {
    public static String MAP;
    public static Point2d REAPER_JUMP_2x2;
    public static Point2d REAPER_JUMP_3x3;
    public static Point2d BUNKER_NATURAL;
    public static Point2d WALL_2x2;
    public static Point2d WALL_3x3;
    public static Point2d MID_WALL_3x3;
    public static Point2d FACTORY;
    public static Point2d ARMORY_WEAPONS;
    public static Point2d ARMORY_ARMOR;
    public static List<Point2d> extraDepots = new ArrayList<>();
    public static List<Point2d> STARPORTS = new ArrayList<>();
    public static List<Point2d> TURRETS = new ArrayList<>();
    public static List<Point2d> MACRO_OCS = new ArrayList<>();


    public static List<Point> myExpansionLocations;
    public static List<Point> enemyExpansionLocations;
    public static Point2d bansheeRallyPos;
    public static int baseAttackIndex = 8;
    public static Race opponentRace = Race.TERRAN;

    public static void onStep() {
        if (Bot.OBS.getGameLoop() % 5000 == 0) {
            baseAttackIndex = 8;
        }
    }

    public static void rotateBaseAttackIndex() {
        baseAttackIndex--;
        if (baseAttackIndex == 0) {
            //TODO: initiate end game clean up
            baseAttackIndex = 6;
        }
    }

    public static void init(UnitInPool mainCC) {
        setHardcodedLocations(mainCC);
        fillExpansionLists(mainCC);
    }

    private static void fillExpansionLists(UnitInPool mainCC) {
        //my expansions
        List<Point> queryResult = Bot.QUERY.calculateExpansionLocations(Bot.OBS);
        myExpansionLocations = new ArrayList<>(queryResult);
        myExpansionLocations.sort((Point p1, Point p2) -> {
            return (int)(Bot.QUERY.pathingDistance(mainCC.unit().getPosition().toPoint2d(), p1.toPoint2d()) -
                    Bot.QUERY.pathingDistance(mainCC.unit().getPosition().toPoint2d(), p2.toPoint2d()));
        });
        Point enemyMainPosition = myExpansionLocations.remove(0); //first entry is enemy main

        //enemy expansions
        enemyExpansionLocations = new ArrayList<>(myExpansionLocations);
        enemyExpansionLocations.sort((Point p1, Point p2) -> {
            return (int)(Bot.QUERY.pathingDistance(enemyMainPosition.toPoint2d(), p1.toPoint2d()) -
                    Bot.QUERY.pathingDistance(enemyMainPosition.toPoint2d(), p2.toPoint2d()));
        });
        myExpansionLocations = myExpansionLocations.subList(0, (myExpansionLocations.size()+1)/2);
        enemyExpansionLocations = enemyExpansionLocations.subList(0, enemyExpansionLocations.size()/2);
        Collections.reverse(enemyExpansionLocations);
        myExpansionLocations.addAll(enemyExpansionLocations);
        myExpansionLocations.add(0, mainCC.unit().getPosition()); //add my main base to start
        myExpansionLocations.add(enemyMainPosition); //add enemy main base to end

        bansheeRallyPos = BuildManager.getMidPoint(myExpansionLocations.get(0).toPoint2d(), myExpansionLocations.get(1).toPoint2d());

        //TODO: delete later.  temporary banshee positions
        ArmyManager.retreatPos = bansheeRallyPos;
        //ArmyManager.attackPos = myExpansionLocations.get(myExpansionLocations.size()-2).toPoint2d();

//        //hidden expansions
//        hiddenExpansionLocations = new ArrayList<Point>(myExpansionLocations);
//        hiddenExpansionLocations.sort((Point p1, Point p2) -> {
//            return (int)(
//                    Math.min(
//                        Bot.QUERY.pathingDistance(mainCC.unit().getPosition().toPoint2d(), p2.toPoint2d()),
//                        Bot.QUERY.pathingDistance(enemyMainPosition.toPoint2d(), p2.toPoint2d())) -
//                    Math.min(
//                            Bot.QUERY.pathingDistance(mainCC.unit().getPosition().toPoint2d(), p1.toPoint2d()),
//                            Bot.QUERY.pathingDistance(enemyMainPosition.toPoint2d(), p1.toPoint2d())
//                    ));
//        });

    }

    private static void setHardcodedLocations(UnitInPool mainCC) {
        boolean isTopPos = mainCC.unit().getPosition().getY() > 100;
        switch (MAP) {
            case MapNames.TRITON:
                if (isTopPos) {
                    REAPER_JUMP_2x2 = Point2d.of(72f, 145f);
                    REAPER_JUMP_3x3 = Point2d.of(69.5f, 144.5f);
                    BUNKER_NATURAL = Point2d.of(87.5f, 152.5f);
                    WALL_2x2 = Point2d.of(71.0f, 154.0f);
                    WALL_3x3 = Point2d.of(73.5f, 150.5f);
                    MID_WALL_3x3 = Point2d.of(70.5f, 151.5f);
                    STARPORTS.add(Point2d.of(67.5f, 163.5f));
                    STARPORTS.add(Point2d.of(64.5f, 161.5f));
                    STARPORTS.add(Point2d.of(62.5f, 164.5f));
                    STARPORTS.add(Point2d.of(58.5f, 168.5f));
                    STARPORTS.add(Point2d.of(53.5f, 166.5f));
                    STARPORTS.add(Point2d.of(47.5f, 164.5f));
                    STARPORTS.add(Point2d.of(47.5f, 167.5f));
                    STARPORTS.add(Point2d.of(42.5f, 162.5f));
                    STARPORTS.add(Point2d.of(44.5f, 157.5f));
                    STARPORTS.add(Point2d.of(45.5f, 152.5f));
                    STARPORTS.add(Point2d.of(47.5f, 149.5f));
                    STARPORTS.add(Point2d.of(47.5f, 146.5f));
                    STARPORTS.add(Point2d.of(47.5f, 143.5f));
                    STARPORTS.add(Point2d.of(51.5f, 153.5f));
                    STARPORTS.add(Point2d.of(53.5f, 150.5f));
                    ARMORY_WEAPONS = Point2d.of(53.5f, 169.5f);
                    ARMORY_ARMOR = Point2d.of(44.5f, 149.5f);
                    TURRETS.add(Point2d.of(51.0f, 157.0f));
                    TURRETS.add(Point2d.of(57.0f, 162.0f));
                    MACRO_OCS.add(Point2d.of(60.5f, 157.5f));
                    MACRO_OCS.add(Point2d.of(59.5f, 152.5f));
                    MACRO_OCS.add(Point2d.of(53.5f, 146.5f));
                    MACRO_OCS.add(Point2d.of(54.5f, 141.5f));
                    MACRO_OCS.add(Point2d.of(65.5f, 156.5f));
                    MACRO_OCS.add(Point2d.of(65.5f, 151.5f));
                    MACRO_OCS.add(Point2d.of(65.5f, 146.5f));
                    MACRO_OCS.add(Point2d.of(59.5f, 147.5f));
                    MACRO_OCS.add(Point2d.of(59.5f, 142.5f));
                    extraDepots.add(Point2d.of(70.0f, 157.0f));
                    extraDepots.add(Point2d.of(70.0f, 159.0f));
                    extraDepots.add(Point2d.of(70.0f, 161.0f));
                    extraDepots.add(Point2d.of(42.0f, 151.0f));
                    extraDepots.add(Point2d.of(42.0f, 149.0f));
                    extraDepots.add(Point2d.of(65.0f, 166.0f));
                    extraDepots.add(Point2d.of(67.0f, 166.0f));
                    extraDepots.add(Point2d.of(65.0f, 168.0f));
                    extraDepots.add(Point2d.of(63.0f, 168.0f));
                    extraDepots.add(Point2d.of(56.0f, 170.0f));
                    extraDepots.add(Point2d.of(56.0f, 168.0f));
                    extraDepots.add(Point2d.of(45.0f, 166.0f));
                    extraDepots.add(Point2d.of(45.0f, 164.0f));
                    extraDepots.add(Point2d.of(43.0f, 160.0f));
                    extraDepots.add(Point2d.of(44.0f, 155.0f));
                    extraDepots.add(Point2d.of(46.0f, 155.0f));
                }
                else {
                    REAPER_JUMP_2x2 = Point2d.of(143f, 60f);
                    REAPER_JUMP_3x3 = Point2d.of(145.5f, 59.5f);
                    BUNKER_NATURAL = Point2d.of(128.5f, 51.5f);
                    WALL_2x2 = Point2d.of(145.0f, 50.0f);
                    WALL_3x3 = Point2d.of(142.5f, 53.5f);
                    MID_WALL_3x3 = Point2d.of(145.5f, 52.5f);

                    ARMORY_WEAPONS = Point2d.of(171.5f, 48.5f);
                    ARMORY_ARMOR = Point2d.of(160.5f, 51.5f);

                    STARPORTS.add(Point2d.of(152.5f, 36.5f));
                    STARPORTS.add(Point2d.of(159.5f, 37.5f));
                    STARPORTS.add(Point2d.of(158.5f, 34.5f));
                    STARPORTS.add(Point2d.of(165.5f, 36.5f));
                    STARPORTS.add(Point2d.of(165.5f, 39.5f));
                    STARPORTS.add(Point2d.of(170.5f, 44.5f));
                    STARPORTS.add(Point2d.of(170.5f, 41.5f));
                    STARPORTS.add(Point2d.of(170.5f, 52.5f));
                    STARPORTS.add(Point2d.of(170.5f, 55.5f));
                    STARPORTS.add(Point2d.of(164.5f, 52.5f));
                    STARPORTS.add(Point2d.of(164.5f, 55.5f));
                    STARPORTS.add(Point2d.of(164.5f, 58.5f));
                    STARPORTS.add(Point2d.of(164.5f, 58.5f));
                    STARPORTS.add(Point2d.of(164.5f, 61.5f));
                    STARPORTS.add(Point2d.of(158.5f, 60.5f));
                    STARPORTS.add(Point2d.of(158.5f, 63.5f));

                    TURRETS.add(Point2d.of(165.0f, 47.0f));
                    TURRETS.add(Point2d.of(159.0f, 42.0f));

                    MACRO_OCS.add(Point2d.of(150.5f, 45.5f));
                    MACRO_OCS.add(Point2d.of(149.5f, 40.5f));
                    MACRO_OCS.add(Point2d.of(150.5f, 50.5f));
                    MACRO_OCS.add(Point2d.of(155.5f, 46.5f));
                    MACRO_OCS.add(Point2d.of(157.5f, 56.5f));
                    MACRO_OCS.add(Point2d.of(150.5f, 55.5f));
                    MACRO_OCS.add(Point2d.of(156.5f, 51.5f));
                    MACRO_OCS.add(Point2d.of(152.5f, 60.5f));

                    extraDepots.add(Point2d.of(153.0f, 39.0f));
                    extraDepots.add(Point2d.of(153.0f, 41.0f));
                    extraDepots.add(Point2d.of(146.0f, 47.0f));
                    extraDepots.add(Point2d.of(146.0f, 45.0f));
                    extraDepots.add(Point2d.of(146.0f, 43.0f));
                    extraDepots.add(Point2d.of(146.0f, 41.0f));
                    extraDepots.add(Point2d.of(150.0f, 37.0f));
                    extraDepots.add(Point2d.of(157.0f, 37.0f));
                    extraDepots.add(Point2d.of(167.0f, 63.0f));
                    extraDepots.add(Point2d.of(170.0f, 61.0f));
                    extraDepots.add(Point2d.of(170.0f, 59.0f));
                    extraDepots.add(Point2d.of(161.0f, 57.0f));
                }
        }
    }
}
