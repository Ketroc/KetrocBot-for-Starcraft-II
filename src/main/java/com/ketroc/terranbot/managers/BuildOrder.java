package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.purchases.PurchaseUnit;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;
import com.ketroc.terranbot.strategies.BunkerContain;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;

public class BuildOrder {
    public static UnitInPool proxyScv;

    public static void onGameStart() {
        if (BunkerContain.proxyBunkerLevel != 0) {
            BunkerContain.addNewRepairScv();
        }
        switch (LocationConstants.opponentRace) { //TODO: fix so that bunker contain can be used vs any race with code 1 or 2
            case TERRAN:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                }
                else if (BunkerContain.proxyBunkerLevel > 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos2));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    if (BunkerContain.proxyBunkerLevel == 2) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                        //KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    }
                    else {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.STARPORTS.remove(0)));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.STARPORTS.remove(1)));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    }
                }
                else {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    if (Strategy.NUM_MARINES > 0) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
                    }
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));

                    //finish reaper wall first
                    if (!Strategy.NO_RAMP_WALL) {
                        if (LocationConstants.reaperBlock3x3s.size() >= 2) {
                            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                            if (LocationConstants.reaperBlock3x3s.size() == 3) {
                                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.reaperBlock3x3s.get(2)));
                            }
                        }
                        for (int i = 0; i < LocationConstants.reaperBlockDepots.size() - 2; i++) {
                            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        }
                    }

                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    if (Strategy.NO_RAMP_WALL || LocationConstants.reaperBlock3x3s.size() < 2) { //build eng bay now if not in the wall
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    }
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                }
                break;
            case PROTOSS:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                }
                else if (BunkerContain.proxyBunkerLevel != 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2)); //WALL_2x2
                    LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    //Bot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvList.get(0).unit(), Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                }
                else if (Strategy.EXPAND_SLOWLY) {
                    PfExpand2BaseOpener();
                }
                else {
                    PfExpandOpener();
                }
                break;
            case RANDOM:
            case ZERG:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                }
                else if (BunkerContain.proxyBunkerLevel != 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2)); //WALL_2x2
                    LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    //Bot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvList.get(0).unit(), Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                }
                else if (Strategy.EXPAND_SLOWLY) {
                    PfExpand2BaseOpener();
                }
                else {
                    PfExpandOpener();
                }
                break;
        }
//        Ketroc.purchaseQueue.add(new PurchaseStructure(Units.INVALID));
    }

    private static void PfExpandOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        //KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
//        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
//        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
    }

    private static void PfExpand2BaseOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
    }

    private static void TvTPfExpand2BaseOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
    }

    private static void marineAllInBuild() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
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
