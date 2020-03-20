package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.BuildManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocationConstants {
    public static String MAP;
    public static Point2d DEPOT1;
    public static Point2d BARRACKS;
    public static Point2d BUNKER1;
    public static Point2d DEPOT2;
    public static Point2d BUNKER2;
    public static Point2d ENGINEERING_BAY;
    public static Point2d FACTORY;
    public static Point2d ARMORY_WEAPONS;
    public static Point2d ARMORY_ARMOR;
    public static List<Point2d> extraDepots = new ArrayList<>();
    public static List<Point2d> STARPORTS = new ArrayList<>();
    public static List<Point2d> TURRETS = new ArrayList<>();


    public static List<Point> myExpansionLocations;
    public static List<Point> enemyExpansionLocations;
    public static Point2d bansheeRallyPos;
    public static int baseAttackIndex = 5;

    public static void onStep() {
        if (Bot.OBS.getGameLoop() % 5000 == 0) {
            baseAttackIndex = 6;
        }
    }

    public static void rotateBaseAttackIndex() {
        baseAttackIndex--;
        if (baseAttackIndex == 0) {
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
        //com.ketroc.terranbot.ArmyManager.attackPos = myExpansionLocations.get(myExpansionLocations.size()-2).toPoint2d();

//        //hidden expansions
//        hiddenExpansionLocations = new ArrayList<Point>(myExpansionLocations);
//        hiddenExpansionLocations.sort((Point p1, Point p2) -> {
//            return (int)(
//                    Math.min(
//                        com.ketroc.terranbot.Bot.QUERY.pathingDistance(mainCC.unit().getPosition().toPoint2d(), p2.toPoint2d()),
//                        com.ketroc.terranbot.Bot.QUERY.pathingDistance(enemyMainPosition.toPoint2d(), p2.toPoint2d())) -
//                    Math.min(
//                            com.ketroc.terranbot.Bot.QUERY.pathingDistance(mainCC.unit().getPosition().toPoint2d(), p1.toPoint2d()),
//                            com.ketroc.terranbot.Bot.QUERY.pathingDistance(enemyMainPosition.toPoint2d(), p1.toPoint2d())
//                    ));
//        });

    }

    private static void setHardcodedLocations(UnitInPool mainCC) {
        boolean isTopPos = mainCC.unit().getPosition().getY() > 100;
        switch (MAP) {
            case MapNames.TRITON:
                if (isTopPos) {
                    DEPOT1 = Point2d.of(71.0f, 154.0f);
                    BARRACKS = Point2d.of(69.5f, 144.5f);
                    BUNKER1 = Point2d.of(87.5f, 152.5f);
                    DEPOT2 = Point2d.of(71.0f, 152.0f);
                    BUNKER2 = Point2d.of(72.5f, 146.5f);
                    ENGINEERING_BAY = Point2d.of(73.5f, 150.5f);
                    FACTORY = Point2d.of(63.5f, 166.5f);
                    STARPORTS.add(Point2d.of(53.5f, 169.5f));
                    STARPORTS.add(Point2d.of(53.5f, 166.5f));
                    STARPORTS.add(Point2d.of(47.5f, 167.5f));
                    STARPORTS.add(Point2d.of(47.5f, 164.5f));
                    STARPORTS.add(Point2d.of(53.5f, 153.5f));
                    STARPORTS.add(Point2d.of(47.5f, 145.5f));
                    STARPORTS.add(Point2d.of(52.5f, 150.5f));
                    STARPORTS.add(Point2d.of(52.5f, 147.5f));
                    STARPORTS.add(Point2d.of(52.5f, 144.5f));
                    STARPORTS.add(Point2d.of(52.5f, 141.5f));
                    STARPORTS.add(Point2d.of(58.5f, 153.5f));
                    STARPORTS.add(Point2d.of(58.5f, 150.5f));
                    STARPORTS.add(Point2d.of(58.5f, 147.5f));
                    STARPORTS.add(Point2d.of(58.5f, 144.5f));
                    ARMORY_WEAPONS = Point2d.of(48.5f, 151.5f);
                    ARMORY_ARMOR = Point2d.of(48.5f, 148.5f);
                    TURRETS.add(Point2d.of(51.0f, 157.0f));
                    TURRETS.add(Point2d.of(57.0f, 162.0f));
                    extraDepots.add(Point2d.of(42.0f, 161.0f));
                    extraDepots.add(Point2d.of(44.0f, 163.0f));
                    extraDepots.add(Point2d.of(44.0f, 161.0f));
                    extraDepots.add(Point2d.of(42.0f, 163.0f));
                    extraDepots.add(Point2d.of(46.0f, 163.0f));
                    extraDepots.add(Point2d.of(46.0f, 161.0f));
                    extraDepots.add(Point2d.of(44.0f, 157.0f));
                    extraDepots.add(Point2d.of(46.0f, 157.0f));
                    extraDepots.add(Point2d.of(46.0f, 155.0f));
                    extraDepots.add(Point2d.of(44.0f, 155.0f));
                    extraDepots.add(Point2d.of(44.0f, 151.0f));
                    extraDepots.add(Point2d.of(42.0f, 151.0f));
                    extraDepots.add(Point2d.of(46.0f, 151.0f));
                    extraDepots.add(Point2d.of(44.0f, 149.0f));
                    extraDepots.add(Point2d.of(46.0f, 149.0f));
                    extraDepots.add(Point2d.of(44.0f, 147.0f));
                    extraDepots.add(Point2d.of(46.0f, 159.0f));
                    extraDepots.add(Point2d.of(44.0f, 159.0f));
                    extraDepots.add(Point2d.of(46.0f, 153.0f));
                    extraDepots.add(Point2d.of(44.0f, 153.0f));
                }
                else {
                    DEPOT1 = Point2d.of(145.0f, 50.0f);
                    BARRACKS = Point2d.of(146.5f, 59.5f);
                    BUNKER1 = Point2d.of(128.5f, 51.5f);
                    DEPOT2 = Point2d.of(145.0f, 52.0f);
                    BUNKER2 = Point2d.of(143.5f, 57.5f);
                    ENGINEERING_BAY = Point2d.of(142.5f, 53.5f);
                    FACTORY = Point2d.of(151.5f, 36.5f);
                    STARPORTS.add(Point2d.of(157.5f, 34.5f));
                    STARPORTS.add(Point2d.of(166.5f, 39.5f));
                    STARPORTS.add(Point2d.of(160.5f, 36.5f));
                    STARPORTS.add(Point2d.of(166.5f, 36.5f));
                    STARPORTS.add(Point2d.of(161.5f, 50.5f));
                    STARPORTS.add(Point2d.of(161.5f, 53.5f));
                    STARPORTS.add(Point2d.of(161.5f, 56.5f));
                    STARPORTS.add(Point2d.of(161.5f, 59.5f));
                    STARPORTS.add(Point2d.of(155.5f, 50.5f));
                    STARPORTS.add(Point2d.of(155.5f, 53.5f));
                    STARPORTS.add(Point2d.of(155.5f, 56.5f));
                    STARPORTS.add(Point2d.of(155.5f, 59.5f));
                    ARMORY_WEAPONS = Point2d.of(167.5f, 52.5f);
                    ARMORY_ARMOR = Point2d.of(167.5f, 55.5f);
                    TURRETS.add(Point2d.of(165.0f, 47.0f));
                    TURRETS.add(Point2d.of(159.0f, 42.0f));
                    extraDepots.add(Point2d.of(174.0f, 53.0f));
                    extraDepots.add(Point2d.of(172.0f, 57.0f)); //172, 57
                    extraDepots.add(Point2d.of(172.0f, 53.0f));
                    extraDepots.add(Point2d.of(172.0f, 55.0f));
                    extraDepots.add(Point2d.of(170.0f, 53.0f));
                    extraDepots.add(Point2d.of(172.0f, 51.0f));
                    extraDepots.add(Point2d.of(170.0f, 55.0f));
                    extraDepots.add(Point2d.of(170.0f, 51.0f));
                    extraDepots.add(Point2d.of(172.0f, 47.0f));
                    extraDepots.add(Point2d.of(170.0f, 49.0f));
                    extraDepots.add(Point2d.of(170.0f, 47.0f));
                    extraDepots.add(Point2d.of(170.0f, 45.0f));
                    extraDepots.add(Point2d.of(172.0f, 49.0f));
                    extraDepots.add(Point2d.of(172.0f, 45.0f));
                    extraDepots.add(Point2d.of(174.0f, 43.0f));
                    extraDepots.add(Point2d.of(170.0f, 43.0f));
                    extraDepots.add(Point2d.of(172.0f, 43.0f));
                    extraDepots.add(Point2d.of(174.0f, 41.0f));
                    extraDepots.add(Point2d.of(172.0f, 41.0f));
                    extraDepots.add(Point2d.of(170.0f, 41.0f));
                }
        }
    }
}
