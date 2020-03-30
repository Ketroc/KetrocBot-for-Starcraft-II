package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BuildManager {
    public static int vikingsRequired = 0;
    public static final List<Abilities> BUILD_ACTIONS = Arrays.asList(
            Abilities.BUILD_REFINERY, Abilities.BUILD_COMMAND_CENTER, Abilities.BUILD_STARPORT, Abilities.BUILD_SUPPLY_DEPOT,
            Abilities.BUILD_ARMORY, Abilities.BUILD_BARRACKS, Abilities.BUILD_BUNKER, Abilities.BUILD_ENGINEERING_BAY,
            Abilities.BUILD_FACTORY, Abilities.BUILD_FUSION_CORE, Abilities.BUILD_GHOST_ACADEMY, Abilities.BUILD_MISSILE_TURRET,
            Abilities.BUILD_SENSOR_TOWER
    );

    public static void onStep() {
        //build depot logic
        if (GameState.mineralBank > 100 && checkIfDepotNeeded() && !LocationConstants.extraDepots.isEmpty()) {
            Bot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        }

        //cancel structure logic
        for (Unit structure : GameState.inProductionList) {
            if (structure.getBuildProgress() < 1) {
                if (UnitUtils.getHealthPercentage(structure) < 8) {
                    Bot.ACTION.unitCommand(structure, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                }
            }
        }

        //keep CCs active (make scvs, morph ccs, call mules)
        for (Unit cc : GameState.ccList) {
            if (!cc.getActive().get()) {
                switch ((Units)cc.getType()) {
                    case TERRAN_COMMAND_CENTER:
                        if (ccToBeOC(cc.getPosition())) {
                            if (UnitUtils.hasTechToBuild(Units.TERRAN_ORBITAL_COMMAND) && UnitUtils.canAfford(Units.TERRAN_ORBITAL_COMMAND)) {
                                if (!isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                    Bot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                }
                                break; //don't queue scv
                            }
                        }
                        else { //if base that will become a PF TODO: use same logic as OC
                            if (UnitUtils.hasTechToBuild(Units.TERRAN_PLANETARY_FORTRESS)) { //&& UnitUtils.canAfford(Units.TERRAN_PLANETARY_FORTRESS)) {
                                if (!isMorphQueued(Abilities.MORPH_PLANETARY_FORTRESS)) { //if affordable and not already in queue
                                    Bot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, cc));
                                }
                                break; //don't queue scv
                            }
                        }
                    case TERRAN_ORBITAL_COMMAND:
                        if (cc.getEnergy().get() >= 50) {
                            Bot.ACTION.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, GameState.mineralNodeRally, false);
                        }

                    case TERRAN_PLANETARY_FORTRESS:
                        //build scv
                        if (Bot.OBS.getFoodWorkers() < Strategy.getMaxScvs()) {
                            Bot.ACTION.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }

                        break;
                }
            }
        }

        //build marines
        if (!GameState.barracksList.isEmpty() && !GameState.barracksList.get(0).unit().getActive().get()) { //if barracks is idle
            int marineCount = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_MARINE, Collections.emptyList()).size();
            for (UnitInPool bunker : GameState.allFriendliesMap.getOrDefault(Units.TERRAN_BUNKER, Collections.emptyList())) {
                marineCount += bunker.unit().getCargoSpaceTaken().get(); //count marines in bunkers
            }
            if (marineCount < 4 && UnitUtils.canAfford(Units.TERRAN_MARINE)) {
                Bot.ACTION.unitCommand(GameState.barracksList.get(0).unit(), Abilities.TRAIN_MARINE, false);
                Cost.updateBank(Units.TERRAN_MARINE);
            }
        }

//        //build siege tanks
//        if (!GameState.factoryList.isEmpty()) {
//            Unit factory = GameState.factoryList.get(0).unit();
//            if (!factory.getActive().get() && factory.getAddOnTag().isPresent()) {
//                //1 tank per expansion base
//                if (GameState.siegeTankList.size() < GameState.baseList.size()-1 && UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
//                    Bot.ACTION.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
//                    Cost.updateBank(Units.TERRAN_SIEGE_TANK);
//                }
//            }
//        }

        //build starport units
        for (UnitInPool starport : GameState.starportList) {
            if (!starport.unit().getActive().get() && starport.unit().getAddOnTag().isPresent()) {
                Abilities unitToProduce = ArmyManager.decideStarportUnit();
                Units unitType = Bot.abilityToUnitType.get(unitToProduce);
                if (UnitUtils.canAfford(unitType)) {
                    Bot.ACTION.unitCommand(starport.unit(), unitToProduce, false);
                    Cost.updateBank(unitType);
                }
            }
        }

        //build command center logic
        if (GameState.baseList.size() < LocationConstants.myExpansionLocations.size() - Strategy.NUM_DONT_EXPAND && GameState.mineralBank > 500 && !isStructureQueued(Units.TERRAN_COMMAND_CENTER)) {
            //if on 6 bases, try to build a macro OC
            if (GameState.baseList.size() >= 7 && !LocationConstants.MACRO_OCS.isEmpty()) {
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, LocationConstants.MACRO_OCS.remove(0)));
            }
            else {
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
            }
        }

        //build starport logic
        if (GameState.gasBank > 400 && GameState.mineralBank > 150 && UnitUtils.hasTechToBuild(Units.TERRAN_STARPORT) && !isAlreadyBuilding(Abilities.BUILD_STARPORT) && !LocationConstants.STARPORTS.isEmpty()) {
            Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        }


    }

    public static boolean ccToBeOC(Point ccPos) {
        for (Point point : LocationConstants.myExpansionLocations) {
            if (ccPos.distance(point) < 1) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkIfDepotNeeded() {
        if (isStructureQueued(Units.TERRAN_SUPPLY_DEPOT)) { //if depot already in queue
            return false;
        }
        int curSupply = Bot.OBS.getFoodUsed();
        int supplyCap = Bot.OBS.getFoodCap();
        if (supplyCap == 200) { // max supply available
            return false;
        }
        // TODO: decide based on current production rate rather than hardcoded 10
        if (supplyCap - curSupply + supplyInProduction() >= 10) { //if not nearing supply block
            return false;
        }

        return true;
    }

    private static int supplyInProduction() { //TODO: switch to use gamestate list of production
        int depotSupply = GameState.productionMap.getOrDefault(Abilities.BUILD_SUPPLY_DEPOT, 0) * 8;
        //int ccSupply = com.ketroc.terranbot.GameState.productionList.getOrDefault(Abilities.BUILD_COMMAND_CENTER, 0) * 14;
        return depotSupply; // + ccSupply;
    }

    //check if structure is in production or in the queue
    public static boolean isAlreadyBuilding(Abilities ability) {
        //check production
        if (GameState.productionMap.getOrDefault(ability, 0) > 0) {
            return true;
        }
        //check queue
        if (isStructureQueued(Bot.abilityToUnitType.get(ability))) {
            return true;
        }
        return false;
    }

    public static boolean isStructureQueued(Units structureType) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType().equals(structureType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMorphQueued(Abilities morphType) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructureMorph && ((PurchaseStructureMorph) p).getMorphOrAddOn() == morphType) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUpgradeQueued(Upgrades upgrade) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseUpgrade && ((PurchaseUpgrade) p).getUpgrade() == upgrade) {
                return true;
            }
        }
        return false;
    }

    public static List<Point2d> calculateTurretPositions(Point2d ccPos) {//pick position away from enemy main base like a knight move (3x1)
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.myExpansionLocations.get(LocationConstants.myExpansionLocations.size()-1).getX();
        float yEnemy = LocationConstants.myExpansionLocations.get(LocationConstants.myExpansionLocations.size()-1).getY();
        float xDistance = xEnemy - xCC;
        float yDistance = yEnemy - yCC;
        float xMove = 1;
        float yMove = 1;
        float xTurret1;
        float yTurret1;
        float xTurret2;
        float yTurret2;

        if (Math.abs(xDistance) > Math.abs(yDistance)) { //move 3x1
            yMove = 4f;
        }
        else { //move 1x3
            xMove = 4f;
        }
        xTurret1 = xCC + xMove;
        xTurret2 = xCC - xMove;

        if (xDistance*yDistance > 0) {
            yTurret1 = yCC - yMove;
            yTurret2 = yCC + yMove;
        }
        else {
            yTurret1 = yCC + yMove;
            yTurret2 = yCC - yMove;
        }
        return List.of(Point2d.of(xTurret1, yTurret1), Point2d.of(xTurret2, yTurret2));
    }

    public static Point2d getMidPoint(Point2d p1, Point2d p2) {
        return Point2d.of((p1.getX() + p2.getX()) / 2, (p1.getY() + p2.getY()) / 2);
    }
}
