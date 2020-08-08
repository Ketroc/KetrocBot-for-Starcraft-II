package com.ketroc.terranbot.models;


import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.purchases.PurchaseStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StructureScv {
    public static final List<StructureScv> scvBuildingList = new ArrayList<>();

    public Point2d structurePos;
    public boolean isGas;
    public UnitInPool gasGeyser;
    public Abilities buildAbility;
    public Units structureType;
    public UnitInPool scv;
    private UnitInPool structureUnit;
    public int numScvsFailed;

    // *********************************
    // ********* CONSTRUCTORS **********
    // *********************************

    public StructureScv(UnitInPool scv, Abilities buildAbility, Point2d structurePos) {
        this.scv = scv;
        this.buildAbility = buildAbility;
        this.structureType = Bot.abilityToUnitType.get(buildAbility);
        this.structurePos = structurePos;
    }

    public StructureScv(UnitInPool scv, Abilities buildAbility, UnitInPool gasGeyser) {
        this.scv = scv;
        this.buildAbility = buildAbility;
        this.structureType = Bot.abilityToUnitType.get(buildAbility);
        this.isGas = true;
        this.gasGeyser = gasGeyser;
        this.structurePos = gasGeyser.unit().getPosition().toPoint2d();
    }

    // **************************************
    // ******** GETTERS AND SETTERS *********
    // **************************************

    public UnitInPool getStructureUnit() {
        if (structureUnit == null) {
            List<UnitInPool> structure = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, structureType, structurePos, 1);
            if (!structure.isEmpty()) {
                structureUnit = structure.get(0);
            }
        }
        return structureUnit;
    }

    public void setStructureUnit(UnitInPool structureUnit) {
        this.structureUnit = structureUnit;
    }

    // **************************
    // ******** METHODS *********
    // **************************

    public void cancelProduction() {
        //cancel structure
        if (getStructureUnit() != null && getStructureUnit().isAlive()) {
            Bot.ACTION.unitCommand(getStructureUnit().unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
        }

        //send scv to mineral patch
        if (scv.isAlive()) {
            Unit mineralPatch = UnitUtils.getSafestMineralPatch();
            if (mineralPatch == null) {
                Bot.ACTION.unitCommand(scv.unit(), Abilities.STOP, false);
            } else {
                Bot.ACTION.unitCommand(scv.unit(), Abilities.SMART, mineralPatch, false);
            }
        }
    }

    // *********************************
    // ******** STATIC METHODS *********
    // *********************************

    public static boolean removeScvFromList(Unit structure) {
        for (int i = 0; i< scvBuildingList.size(); i++) {
            StructureScv scv = scvBuildingList.get(i);
            if (scv.structureType == structure.getType() && scv.structurePos.distance(structure.getPosition().toPoint2d()) < 1) {
                scvBuildingList.remove(i--);
                return true;
            }
        }
        return false;
    }

    //cancel structure that's already started
    //send scv to mineral patch
    //remove StructureScv from scvBuildingList
    public static boolean cancelProduction(Units type, Point2d pos) {
        for (int i = 0; i< scvBuildingList.size(); i++) {
            StructureScv scv = scvBuildingList.get(i);
            if (scv.structureType == type && scv.structurePos.distance(pos) < 1) {
                //cancel structure
                scv.cancelProduction();

                //remove StructureScv object from list
                scvBuildingList.remove(i);
                return true;
            }
        }
        return false;
    }

    public static void checkScvsActivelyBuilding() {
        for (int i = 0; i< scvBuildingList.size(); i++) {
            StructureScv scv = scvBuildingList.get(i);

            //if assigned scv is dead or doesn't have the build order
            if (!scv.scv.isAlive() || scv.scv.unit().getOrders().isEmpty() || !scv.scv.unit().getOrders().stream().anyMatch(order -> order.getAbility() == scv.buildAbility)) {
                UnitInPool structure = scv.getStructureUnit();

                //if structure never started/destroyed, repurchase
                if (structure == null || !structure.isAlive()) {

                    //if cc location is blocked by burrowed unit or creep, set baseIndex to this base
                    if (LocationConstants.opponentRace == Race.ZERG &&
                            scv.scv.isAlive() &&
                            scv.structureType == Units.TERRAN_COMMAND_CENTER &&
                            UnitUtils.getDistance(scv.scv.unit(), scv.structurePos) < 5) {
                        int blockedBaseIndex = LocationConstants.baseLocations.indexOf(scv.structurePos);
                        if (blockedBaseIndex > 0) {
                            LocationConstants.baseAttackIndex = blockedBaseIndex;
                            System.out.println("blocked base.  set baseIndex to " + blockedBaseIndex);
                        }
                    }

                    requeueCancelledStructure(scv);
                    scvBuildingList.remove(i--);
                }
                //if structure started but not complete
                else if (structure.unit().getBuildProgress() < 1.0f) {

                    //remove if there is a duplicate entry in scvBuildList
                    if (isDuplicateStructureScv(scv)) {
                        scvBuildingList.remove(i--);
                    }

                    //send another scv
                    else {
                        List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(scv.structurePos);
                        if (!availableScvs.isEmpty()) {
                            scv.numScvsFailed++;
                            if (scv.numScvsFailed < 3 || structure.unit().getBuildProgress() > 0.8f) {
                                Bot.ACTION.unitCommand(availableScvs.get(0).unit(), Abilities.SMART, structure.unit(), false);
                                scv.scv = availableScvs.get(0);
                            }
                            //if scvs keep dying, cancel structure and add it back to the purchase queue
                            else {
                                Bot.ACTION.unitCommand(structure.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                                requeueCancelledStructure(scv);
                                scvBuildingList.remove(i--);
                            }
                        }
                    }
                }

                //if structure completed
                else if (structure.unit().getBuildProgress() == 1.0f) {
                    scvBuildingList.remove(i--);
                }
            }
        }
        GameCache.buildingScvTags = scvBuildingList.stream().map(s -> s.scv.getTag()).collect(Collectors.toList());
    }

    private static void requeueCancelledStructure(StructureScv scv) {
        switch (scv.structureType) {
            //don't specify same position for these structures
            case TERRAN_COMMAND_CENTER:
                //Bot.purchaseQueue.addFirst(new PurchaseStructure(scv.structureType));
                break;
            //fix for refinery type
            case TERRAN_REFINERY: case TERRAN_REFINERY_RICH: case TERRAN_REFINERY_RICH_410:
                //Bot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_REFINERY));
                break;
            case TERRAN_SUPPLY_DEPOT:
                LocationConstants.extraDepots.add(scv.structurePos);
//                Bot.purchaseQueue.addFirst(new PurchaseStructure(scv.structureType));
                break;
            case TERRAN_STARPORT:
                LocationConstants.STARPORTS.add(scv.structurePos);
//                Bot.purchaseQueue.addFirst(new PurchaseStructure(scv.structureType));
                break;
            case TERRAN_ARMORY: case TERRAN_ENGINEERING_BAY: case TERRAN_BARRACKS: case TERRAN_GHOST_ACADEMY:
                LocationConstants._3x3Structures.add(scv.structurePos);
                Bot.purchaseQueue.addFirst(new PurchaseStructure(scv.structureType));
                break;
            default:
                Bot.purchaseQueue.addFirst(new PurchaseStructure(scv.structureType, scv.structurePos));
                break;
        }
    }

    public static boolean isAlreadyInProductionAt(Units type, Point2d pos) {
        return scvBuildingList.stream()
                .anyMatch(scv -> scv.structureType == type && scv.structurePos == pos);
    }

    public static boolean isAlreadyInProduction(Units type) {
        return scvBuildingList.stream()
                .anyMatch(scv -> scv.structureType == type);
    }

    //checks if an scv is within the scvBuildingList
    public static boolean isScvProducing(Unit scv) {
        return scvBuildingList.stream()
                .anyMatch(structureScv -> structureScv.scv.getTag().equals(scv.getTag()));
    }


    //check if another scv is already doing the assigned structure build
    private static boolean isDuplicateStructureScv(StructureScv scv) {
        for (StructureScv otherScv : scvBuildingList) {
            if (!scv.equals(otherScv) && otherScv.buildAbility == scv.buildAbility && UnitUtils.getDistance(otherScv.scv.unit(), scv.getStructureUnit().unit()) < 3) {
                return true;
            }
        }
        return false;
    }
}
