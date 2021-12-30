package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.purchases.*;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.LocationConstants;
import com.ketroc.geometry.Position;

import java.util.Comparator;

public class BuildOrder {
    public static UnitInPool proxyScv;

    public static void onGameStart() {
        switch (LocationConstants.opponentRace) { //TODO: fix so that bunker contain can be used vs any race with code 1 or 2
            case TERRAN:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                }
//                else if (Strategy.gamePlan == GamePlan.TANK_VIKING) {
//                    _1_1_1_Opener();
//                }
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
                }
                else if (Strategy.gamePlan == GamePlan.RAVEN_CYCLONE) {
                    _1base1Fact2StarportOpener();
                }
                else if (Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING) {
                    _111ExpandOpener();
                }
                else {
                    Switches.fastDepotBarracksOpener = true;
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    //KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SCV, GameCache.baseList.get(0).getCc()));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    if (!LocationConstants.FACTORIES.isEmpty()) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                        LocationConstants.FACTORIES.clear();
                    }
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                    if (Strategy.MAX_MARINES > 0) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
                    }

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
                    if (Strategy.NUM_BASES_TO_OC < 2 &&
                            (Strategy.NO_RAMP_WALL || LocationConstants.reaperBlock3x3s.size() < 2)) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    }

                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
                    if (Purchase.numStructuresQueuedOfType(Units.TERRAN_SUPPLY_DEPOT) < 3) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    }
                    KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_CLOAK));
                    KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_BANSHEE));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));

                    //KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                    //KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
                    build3rdCC();
                }
                break;
            case PROTOSS:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                }
                else if (Strategy.gamePlan == GamePlan.ONE_BASE_BANSHEE_CYCLONE) {
                    _1base2FactOpener();
                }
                else if (BunkerContain.proxyBunkerLevel != 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                }
                else if (Strategy.gamePlan == GamePlan.BANSHEE_CYCLONE) {
                    _2FactExpandOpener();
                }
                else if (Strategy.EXPAND_SLOWLY) {
                    pfExpand2BaseMassGasOpener();
                }
                else {
                    pfExpandOpener();
                }
                break;
            case ZERG:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                } else if (BunkerContain.proxyBunkerLevel != 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                } else if (Strategy.NUM_BASES_TO_OC > 1) {
                    if (Strategy.MASS_MINE_OPENER) {
                        _2FactExpandWidowMineOpener();
                    } else {
                        _2FactExpandHellionOpener();
                    }
                } else if (Strategy.EXPAND_SLOWLY) {
                    pfExpand2BaseMassGasOpener();
                } else {
                    pfExpandFast3rdOpener();
                }
                break;

            case RANDOM:
                _1base2FactOpener();
                break;
        }
    }

    private static void pfExpandFast3rdOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        build3rdCC();
    }

    private static void pfExpandOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _1FactExpandOpener() {
        LocationConstants.STARPORTS.add(LocationConstants.FACTORIES.remove(1));
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _2FactExpandOpener() {
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        if (!LocationConstants.FACTORIES.isEmpty()) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        }
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _2FactExpandHellionOpener() {
        WorkerManager.numScvsPerGas = 2;
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
        if (!LocationConstants.FACTORIES.isEmpty()) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        }
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_HELLION));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_HELLION));
        build3rdCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _2FactExpandWidowMineOpener() {
        WorkerManager.numScvsPerGas = 2;
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        if (!LocationConstants.FACTORIES.isEmpty()) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        }
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        build3rdCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.DRILL_CLAWS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }


    private static void pfExpand2BaseMassGasOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
    }

    private static void _1base1Fact2StarportOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        BuildManager.purchaseMacroCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _1base2FactOpener() {
        WorkerManager.numScvsPerGas = 3;
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
    }

    private static void _1base2Fact2StarportOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        BuildManager.purchaseMacroCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
    }

    private static void _111ExpandOpener() {
        WorkerManager.numScvsPerGas = 2;
        LocationConstants.STARPORTS.add(LocationConstants.FACTORIES.remove(1));
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_VIKING_FIGHTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
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
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.WALL_3x3));
        LocationConstants._3x3Structures.remove(LocationConstants.WALL_3x3);
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
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
                int rotation = (clockwiseFirst) ? i : -i;
                Point2d rotatedPos = Position.rotate(bunkerPos, natCCPos, rotation);
                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, rotatedPos)) {
                    return rotatedPos;
                }
                else if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(rotatedPos, natCCPos, 1))) {
                    return Position.towards(rotatedPos, natCCPos, 1);
                }
            }
            for (int i=10; i<90; i+=10) {
                int rotation = (!clockwiseFirst) ? i : -i;
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

    public static void build3rdCC() {
        if (Strategy.BUILD_EXPANDS_IN_MAIN) {
            Point2d thirdCcPos = LocationConstants.MACRO_OCS.stream()
                    .min(Comparator.comparing(p -> p.distance(LocationConstants.baseLocations.get(2))))
                    .get();
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, thirdCcPos));
            LocationConstants.MACRO_OCS.remove(thirdCcPos);
        }
        else {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(
                    Units.TERRAN_COMMAND_CENTER, LocationConstants.baseLocations.remove(2)));
        }
    }
}
