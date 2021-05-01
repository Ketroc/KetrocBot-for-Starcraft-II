package com.ketroc.models;


import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.ExpansionClearing;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StructureScv {
    public static final List<StructureScv> scvBuildingList = new ArrayList<>();

    public Point2d structurePos;
    public boolean isGas;
    public UnitInPool gasGeyser;
    public Abilities buildAbility;
    public Units structureType;
    private UnitInPool scv;
    private UnitInPool structureUnit;
    public long scvAddedFrame;

    // *********************************
    // ********* CONSTRUCTORS **********
    // *********************************

    public StructureScv(UnitInPool scv, Abilities buildAbility, Point2d structurePos) {
        this.structurePos = structurePos;
        this.buildAbility = buildAbility;
        this.structureType = Bot.abilityToUnitType.get(buildAbility);
        PlacementMap.makeUnavailable(structureType, structurePos);
        scvAddedFrame = Time.nowFrames();
        setScv(scv);
    }

    public StructureScv(UnitInPool scv, Abilities buildAbility, UnitInPool gasGeyser) {
        this.structurePos = gasGeyser.unit().getPosition().toPoint2d();
        this.buildAbility = buildAbility;
        this.structureType = Bot.abilityToUnitType.get(buildAbility);
        this.isGas = true;
        this.gasGeyser = gasGeyser;
        scvAddedFrame = Time.nowFrames();
        setScv(scv);
    }

    // **************************************
    // ******** GETTERS AND SETTERS *********
    // **************************************

    public UnitInPool getScv() {
        return scv;
    }

    public void setScv(UnitInPool scv) {
        if (this.scv != null) {
            Ignored.remove(this.scv.getTag());
        }
        this.scv = scv;
        Base.releaseScv(scv.unit());
        scvAddedFrame = Time.nowFrames();
        Ignored.add(new IgnoredUnit(scv.getTag()));
        if (structureType == Units.TERRAN_COMMAND_CENTER) {
            GameCache.baseList.stream()
                    .filter(base -> base.getCcPos().distance(structurePos) < 1)
                    .findFirst()
                    .ifPresent(base -> ArmyManager.sendBioProtection(base.getResourceMidPoint()));
        }
    }

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
            ActionHelper.unitCommand(getStructureUnit().unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
        }
        PlacementMap.makeAvailable(structureType, structurePos);

        //send scv to mineral patch
        if (scv.isAlive()) {
            Ignored.remove(scv.getTag());
            ActionHelper.unitCommand(scv.unit(), Abilities.STOP, false);
        }
    }

    public boolean isStructurePosSafe() {
        return InfluenceMaps.getGroundThreatToStructure(structureType, structurePos) > 1;
    }

    @Override
    public String toString() {
        StringBuffer strBuff = new StringBuffer();
        strBuff.append("structurePos: ").append(structurePos)
                .append("\nstructureType: ").append(structureType)
                .append("\nscv.position: ").append(scv.unit().getPosition().toPoint2d())
                .append("\nscvAddedFrame: ").append(scvAddedFrame);
        return strBuff.toString();
    }

    // *********************************
    // ******** STATIC METHODS *********
    // *********************************

    public static boolean onStructureCompleted(Unit structure) {
        for (int i = 0; i< scvBuildingList.size(); i++) {
            StructureScv structureScv = scvBuildingList.get(i);

            //hack to handle rich refineries
            Units structureType = (Units)structure.getType();
            if (structureType == Units.TERRAN_REFINERY_RICH) {
                structureType = Units.TERRAN_REFINERY;
            }

            if (structureScv.structureType == structureType && structureScv.structurePos.distance(structure.getPosition().toPoint2d()) < 1) {
                if (structureScv.structureType == Units.TERRAN_REFINERY) {
                    GameCache.baseList.stream()
                            .flatMap(base -> base.getGases().stream())
                            .filter(gas -> gas.getNodePos().distance(structureScv.structurePos) < 1)
                            .findFirst()
                            .ifPresent(gas -> {
                                Base.releaseScv(structureScv.scv.unit());
                                gas.getScvs().add(structureScv.scv);
                            });
                }

                remove(structureScv);
                return true;
            }
        }
        return false;
    }

    //cancel structure that's already started
    //free up and stop scv
    //remove StructureScv from scvBuildingList
    public static boolean cancelProduction(Units type, Point2d pos) {
        for (int i = 0; i< scvBuildingList.size(); i++) {
            StructureScv scv = scvBuildingList.get(i);
            if (scv.structureType == type && scv.structurePos.distance(pos) < 1) {
                //cancel structure
                scv.cancelProduction();

                //remove StructureScv object from list
                remove(scv);
                return true;
            }
        }
        return false;
    }

    //cancel structure that's already started
    //free up and stop scv
    //remove StructureScv from scvBuildingList
    public static boolean cancelProduction(Units type) {
        for (int i = 0; i< scvBuildingList.size(); i++) {
            StructureScv scv = scvBuildingList.get(i);
            if (scv.structureType == type) {
                //cancel structure
                scv.cancelProduction();

                //remove StructureScv object from list
                remove(scv);
                return true;
            }
        }
        return false;
    }

    public static void checkScvsActivelyBuilding() {
        for (int i = 0; i<scvBuildingList.size(); i++) {
            StructureScv structureScv = scvBuildingList.get(i);

            //if assigned scv is dead add another
            if (!structureScv.scv.isAlive()) {
                if (BunkerContain.proxyBunkerLevel > 0 &&
                        Time.nowFrames() < Time.toFrames("5:00") &&
                        structureScv.structurePos.distance(LocationConstants.pointOnMyRamp) > 50) {
                    BunkerContain.repairScvList.stream()
                            .filter(u -> !StructureScv.isScvProducing(u.unit()))
                            .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), structureScv.structurePos)))
                            .ifPresent(u -> structureScv.setScv(u));
                }
                if (!structureScv.scv.isAlive()) {
                    //don't add another scv if the structure is under enemy threat (exception for wall/bunkers/turrets)
                    if (structureScv.structureType != Units.TERRAN_BUNKER &&
                            structureScv.structureType != Units.TERRAN_MISSILE_TURRET &&
                            !UnitUtils.isWallStructurePos(structureScv.structurePos) &&
                            InfluenceMaps.getThreatToStructure(structureScv.structureType, structureScv.structurePos) > 1) {
                        continue;
                    }
                    List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(structureScv.structurePos);
                    if (!availableScvs.isEmpty()) {
                        structureScv.setScv(availableScvs.get(0));
                    }
                }
            }

            //if scv doesn't have the build command
            if (ActionIssued.getCurOrder(structureScv.scv.unit()).isEmpty() ||
                    (ActionIssued.getCurOrder(structureScv.scv.unit()).stream().noneMatch(order -> order.ability == structureScv.buildAbility) &&
                            structureScv.scv.unit().getOrders().stream().noneMatch(order -> order.getAbility() == structureScv.buildAbility))) { //scv can have the order queued if it just finished building another structure
                UnitInPool structure = structureScv.getStructureUnit();

                //if structure never started/destroyed, repurchase
                if (structure == null || !structure.isAlive()) {

                    //any unit within 5 that is a snapshot, or a non-cloaked/non-burrowed unit
                    if (structureScv.structureType == Units.TERRAN_COMMAND_CENTER) {
                        if (UnitUtils.getDistance(structureScv.scv.unit(), structureScv.structurePos) < 10 &&
                                !ExpansionClearing.isVisiblyBlockedByUnit(structureScv.structurePos)) { //creep or burrowed/cloaked
                            ExpansionClearing.add(structureScv.structurePos);
                        }
                    }
                    System.out.println("ActionIssued.getCurOrder(structureScv.scv.unit()).isEmpty() = " + ActionIssued.getCurOrder(structureScv.scv.unit()).isEmpty());
                    System.out.println("ActionIssued.getCurOrder(structureScv.scv.unit()).stream().noneMatch(order -> order.ability == structureScv.buildAbility) = " + ActionIssued.getCurOrder(structureScv.scv.unit()).stream().noneMatch(order -> order.ability == structureScv.buildAbility));
                    System.out.println("structureScv.scv.unit().getOrders().stream().noneMatch(order -> order.getAbility() == structureScv.buildAbility) = " + structureScv.scv.unit().getOrders().stream().noneMatch(order -> order.getAbility() == structureScv.buildAbility));
                    requeueCancelledStructure(structureScv);
                    remove(structureScv);
                    i--;
                }
                //if structure started but not complete
                else if (structure.unit().getBuildProgress() < 1.0f) {

                    //remove if there is a duplicate entry in scvBuildList
                    if (isDuplicateStructureScv(structureScv)) {
                        scvBuildingList.remove(i--);
                    }

                    //send another scv
                    else {
                        ActionHelper.unitCommand(structureScv.scv.unit(), Abilities.SMART, structure.unit(), false);
                    }
                }

                //if structure completed
                else if (structure.unit().getBuildProgress() == 1.0f) {
                    if (structureScv.structureType == Units.TERRAN_REFINERY) {
                        GameCache.baseList.stream()
                                .flatMap(base -> base.getGases().stream())
                                .filter(gas -> gas.getNodePos().distance(structureScv.structurePos) < 1)
                                .findFirst()
                                .ifPresent(gas -> gas.getScvs().add(structureScv.scv));
                    }
                    remove(structureScv);
                    i--;
                }
            }
        }
    }

    //makes structure position available again, then requeues structure purchase (sometimes)
    private static void requeueCancelledStructure(StructureScv structureScv) {
        Print.print("structure requeued");
        switch (structureScv.structureType) {
            //don't queue rebuild on these structure types
            case TERRAN_COMMAND_CENTER:
            case TERRAN_REFINERY: case TERRAN_REFINERY_RICH:
                break;
            case TERRAN_BUNKER:
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType, structureScv.structurePos));
                break;
            case TERRAN_SUPPLY_DEPOT:
                LocationConstants.extraDepots.add(structureScv.structurePos);
                break;
            case TERRAN_FACTORY:
                LocationConstants.FACTORIES.add(structureScv.structurePos);
                break;
            case TERRAN_STARPORT:
                LocationConstants.STARPORTS.add(structureScv.structurePos);
                break;
            case TERRAN_BARRACKS:
                if (LocationConstants.proxyBarracksPos == null || structureScv.structurePos.distance(LocationConstants.proxyBarracksPos) > 10) {
                    LocationConstants._3x3Structures.add(structureScv.structurePos);
                }
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType));
                break;
            case TERRAN_ARMORY: case TERRAN_ENGINEERING_BAY: case TERRAN_GHOST_ACADEMY:
                LocationConstants._3x3Structures.add(structureScv.structurePos);
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType));
                break;
            default:
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType, structureScv.structurePos));
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

    public static int numInProductionOfType(Units type) {
        return (int)scvBuildingList.stream()
                .filter(scv -> scv.structureType == type)
                .count();
    }

    //checks if an scv is within the scvBuildingList
    public static boolean isScvProducing(Unit scv) {
        return scvBuildingList.stream()
                .anyMatch(structureScv -> structureScv.scv.getTag().equals(scv.getTag()));
    }


    //check if another scv is already doing the assigned structure build
    private static boolean isDuplicateStructureScv(StructureScv structureScv) {
        for (StructureScv otherScv : scvBuildingList) {
            if (!structureScv.equals(otherScv) && otherScv.buildAbility == structureScv.buildAbility && UnitUtils.getDistance(otherScv.scv.unit(), structureScv.getStructureUnit().unit()) < 3) {
                return true;
            }
        }
        return false;
    }

    public static StructureScv findByScvTag(Tag scvTag) {
        return scvBuildingList.stream()
                .filter(structureScv -> structureScv.scv.getTag().equals(scvTag))
                .findFirst()
                .orElse(null);
    }


    public static void add(StructureScv structureScv) {
        scvBuildingList.add(structureScv);
    }

    public static void remove(StructureScv structureScv) {
        if (structureScv.scv != null) {
            Ignored.remove(structureScv.scv.getTag());
        }
        scvBuildingList.remove(structureScv);
    }
}
