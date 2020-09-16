package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.strategies.BunkerContain;
import com.ketroc.terranbot.strategies.Strategy;

public class BuildOrder {
    public static UnitInPool proxyScv;

    public static void onGameStart() {
        if (BunkerContain.proxyBunkerLevel != 0) {
            BunkerContain.addAnotherRepairScv();
        }
        switch (LocationConstants.opponentRace) { //TODO: fix so that bunker contain can be used vs any race with code 1 or 2
            case TERRAN:
                LocationConstants.prepareReaperWallLocations();
                if (BunkerContain.proxyBunkerLevel == 2) {
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    Bot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvs.get(0).unit(), Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvs.get(0).unit(), Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));

                    Point2d factoryPos = (LocationConstants.MAP.equals(MapNames.ZEN) || LocationConstants.MAP.equals(MapNames.THUNDERBIRD))
                            ? LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-3)
                            : LocationConstants.FACTORY;
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, factoryPos));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                }
                else {
                    Switches.tvtFastStart = true;
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                }
                break;
            case PROTOSS:
                if (BunkerContain.proxyBunkerLevel != 0) {
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2)); //WALL_2x2
                    LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                    Bot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvs.get(0).unit(), Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    Bot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvs.get(0).unit(), Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                }
                else {
                    //TODO: more intuitive logic on 1st depot location
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2)); //WALL_2x2
                    LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));

                }
                break;
            case RANDOM:
            case ZERG: //TODO: make purchase depot not take a location
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2)); //WALL_2x2
                LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                break;
        }
    }

    private static Point2d getBunkerContainPosition() {
        Point2d natCCPos = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-2);
        Point2d bunkerPos = Position.towards(natCCPos, LocationConstants.baseLocations.get(1), 11);

        if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, bunkerPos)) {
            return bunkerPos;
        }
        else if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(bunkerPos, natCCPos, 1))) {
            return Position.towards(bunkerPos, natCCPos, 1);
        }
        else {
            Point2d enemyMainCCPos = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1);
            boolean clockwiseFirst = Position.rotate(bunkerPos, natCCPos, 10).distance(enemyMainCCPos) >
                    Position.rotate(bunkerPos, natCCPos, -10).distance(enemyMainCCPos);
            for (int i=10; i<90; i+=10) {
                int rotation = (clockwiseFirst) ? i : (i * -1);
                Point2d rotatedPos = Position.rotate(bunkerPos, natCCPos, rotation);
                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, rotatedPos)) {
                    return rotatedPos;
                }
                else if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(rotatedPos, natCCPos, 1))) {
                    return Position.towards(rotatedPos, natCCPos, 1);
                }
            }
            for (int i=10; i<90; i+=10) {
                int rotation = (!clockwiseFirst) ? i : (i * -1);
                Point2d rotatedPos = Position.rotate(bunkerPos, natCCPos, rotation);
                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, rotatedPos)) {
                    return rotatedPos;
                }
                else if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(rotatedPos, natCCPos, 1))) {
                    return Position.towards(rotatedPos, natCCPos, 1);
                }
            }
            return natCCPos;
        }
    }
}
