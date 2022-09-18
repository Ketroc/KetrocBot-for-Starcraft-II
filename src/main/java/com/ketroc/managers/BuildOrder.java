package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.gamestate.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.purchases.*;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;

public class BuildOrder {
    public static UnitInPool proxyScv;

    public static void onGameStart() {
        switch (PosConstants.opponentRace) { //TODO: fix so that bunker contain can be used vs any race with code 1 or 2
            case TERRAN:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                } else if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
                    ghostMarauderOpener();
                }
//                else if (Strategy.gamePlan == GamePlan.TANK_VIKING) {
//                    _1_1_1_Opener();
//                }
                else if (Strategy.gamePlan == GamePlan.BUNKER_CONTAIN_WEAK) {
                    tvtBunkerContainWeak();
                }
                else if (Strategy.gamePlan == GamePlan.BUNKER_CONTAIN_STRONG) {
                    tvtBunkerContainStrong();
                }
                else if (Strategy.gamePlan == GamePlan.RAVEN_CYCLONE) {
                    _1base1Fact2StarportOpener();
                }
                else if (Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING) {
                    _111ExpandOpener();
                }
                else if (Strategy.gamePlan == GamePlan.TANK_VIKING) {
                    Switches.fastDepotBarracksOpener = true;
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    //KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SCV, GameCache.baseList.get(0).getCc()));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                    if (Strategy.MAX_MARINES > 0) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
                    }

                    //finish reaper wall first
                    if (!Strategy.NO_RAMP_WALL) {
                        if (PosConstants.reaperBlock3x3s.size() >= 2) {
                            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                            if (PosConstants.reaperBlock3x3s.size() == 3) {
                                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.reaperBlock3x3s.get(2)));
                            }
                        }
                        for (int i = 0; i < PosConstants.reaperBlockDepots.size() - 2; i++) {
                            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                        }
                    }
                    if (Strategy.NUM_BASES_TO_OC < 2 &&
                            (Strategy.NO_RAMP_WALL || PosConstants.reaperBlock3x3s.size() < 2)) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    }

                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
                    if (Purchase.numStructuresQueuedOfType(Units.TERRAN_SUPPLY_DEPOT) < 3) {
                        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    }
//                    KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_CLOAK));
//                    KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_BANSHEE));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));

                    //KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                    //KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
//                    build3rdCC();
                }
                else { //mass raven, mass banshee, etc
                    pfExpandOpener();
                }
                break;
            case PROTOSS:
                if (Strategy.MARINE_ALLIN) {
                    marineAllInBuild();
                }
                else if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
                    //ghostMarauderOpener();
                    ccFirstGhosts();
                }
                else if (Strategy.gamePlan == GamePlan.MECH_ALL_IN) {
                    mechAllIn();
                }
                else if (Strategy.gamePlan == GamePlan.ONE_BASE_BANSHEE_CYCLONE) {
                    _1base2FactOpener();
                }
                else if (Strategy.gamePlan == GamePlan.BUNKER_CONTAIN_WEAK) {
                    tvpBunkerContainWeak();
                }
                else if (Strategy.gamePlan == GamePlan.BUNKER_CONTAIN_STRONG) {
                    tvpBunkerContainStrong();
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
                }
                else if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
                    ghostHellbatOpener();
                }
                else if (Strategy.gamePlan == GamePlan.BC_RUSH) {
                    ccFirst2BaseBCs();
                }
                else if (BunkerContain.proxyBunkerLevel != 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.proxyBarracksPos));
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos));
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
        KetrocBot.purchaseQueue.add(new BuildOrderComplete());
    }

    private static void tvtBunkerContainWeak() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.proxyBarracksPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos2));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
    }

    private static void tvtBunkerContainStrong() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.proxyBarracksPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos2));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void tvpBunkerContainStrong() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.proxyBarracksPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
    }

    private static void ccFirst2BaseBCs() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.WALL_3x3));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));

        Point2d hiddenFusionCorePos = getHiddenFusionCorePos();
        Point2d factoryPos = PosConstants._3x3AddonPosList.stream()
                .filter(p -> UnitUtils.isInMyMain(p))
                .min(Comparator.comparing(p -> p.distance(GameCache.baseList.get(3).getCcPos())))
                .orElse(PosConstants._3x3AddonPosList.get(4));
        PosConstants._3x3AddonPosList.remove(factoryPos);

        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, factoryPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(1).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FUSION_CORE, hiddenFusionCorePos));
    }

    private static Point2d getHiddenFusionCorePos() {
        Point2d hiddenPos = Position.towards(
                Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), 8),
                GameCache.baseList.get(1).getResourceMidPoint(),
                6);
        Point2d closest3x3Pos = PosConstants._3x3Structures.stream()
                .min(Comparator.comparing(p -> p.distance(hiddenPos)))
                .get();
        Point2d closestStarportPos = PosConstants._3x3AddonPosList.stream()
                .min(Comparator.comparing(p -> p.distance(hiddenPos)))
                .get();
        if (closest3x3Pos.distance(hiddenPos) < closestStarportPos.distance(hiddenPos)) {
            PosConstants._3x3Structures.remove(closest3x3Pos);
            return closest3x3Pos;
        }
        else {
            PosConstants._3x3AddonPosList.remove(closestStarportPos);
            return closestStarportPos;
        }
    }

    private static void tvpBunkerContainWeak() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.proxyBarracksPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.proxyBunkerPos));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
    }

    private static void pfExpandFast3rdOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _1FactExpandOpener() {
        //LocationConstants.STARPORTS.add(LocationConstants.FACTORIES.remove(1));
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    private static void _2FactExpandOpener() {
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }

    public static void mechAllIn() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_REACTOR_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY));
    }

    private static void ghostMarauderOpener() {
        Switches.fastDepotBarracksOpener = true;
        WorkerManager.numScvsPerGas = 3;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_MARINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_MARINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_GHOST_ACADEMY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        build3rdCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.PUNISHER_GRENADES));
    }

    private static void ghostHellbatOpener() {
        Switches.fastDepotBarracksOpener = true;
        WorkerManager.numScvsPerGas = 3;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_MARINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_MARINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_GHOST_ACADEMY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        build3rdCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_REACTOR_FACTORY));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.DRILL_CLAWS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        build3rdCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
    }


    private static void pfExpand2BaseMassGasOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_SIEGE_TANK));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_WIDOWMINE));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_CYCLONE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
    }

    private static void ccFirstGhosts() {
        WorkerManager.numScvsPerGas = 3;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_MARINE));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_GHOST_ACADEMY));
        KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseUnit(Units.TERRAN_GHOST));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
    }

    private static void _1base2Fact2StarportOpener() {
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        BuildManager.purchaseMacroCC();
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
    }

    private static void _111ExpandOpener() {
        WorkerManager.numScvsPerGas = 2;
        //LocationConstants.STARPORTS.add(LocationConstants.FACTORIES.remove(1));
        Switches.fastDepotBarracksOpener = true;
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
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
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, PosConstants.WALL_3x3));
        PosConstants._3x3Structures.remove(PosConstants.WALL_3x3);
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
        Point2d natCCPos = PosConstants.baseLocations.get(PosConstants.baseLocations.size()-2);
        Point2d bunkerPos = Position.towards(natCCPos, PosConstants.baseLocations.get(1), 11);

        if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, bunkerPos)) {
            return bunkerPos;
        }
        else if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(bunkerPos, natCCPos, 1))) {
            return Position.towards(bunkerPos, natCCPos, 1);
        }
        else {
            Point2d enemyMainCCPos = PosConstants.baseLocations.get(PosConstants.baseLocations.size()-1);
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
            KetrocBot.purchaseQueue.add(new PurchaseStructure(
                    Units.TERRAN_COMMAND_CENTER, PosConstants.MACRO_OCS.remove(0))
            );
        }
        else {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(
                    Units.TERRAN_COMMAND_CENTER, PosConstants.baseLocations.get(2))
            );
        }
    }
}
