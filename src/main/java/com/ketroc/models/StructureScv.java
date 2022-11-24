package com.ketroc.models;


import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
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
import java.util.Optional;

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
            UnitUtils.returnAndStopScv(scv);
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

    //reduce bank for structure costs of structures which haven't gotten the "BUILD" order yet in realtime mode
//    public static void updateBank() {
//        if (Launcher.isRealTime) {
//            scvBuildingList.stream()
//                    .filter(sScv -> sScv.getStructureUnit() == null &&
//                            sScv.scv != null &&
//                            sScv.scv.unit().getOrders().stream()
//                                    .anyMatch(order -> !order.getAbility().toString().contains("BUILD")))
//                    .forEach(sScv -> {
//                        System.out.println("frame#" + Time.nowFrames() + ": reducing available bank for " + sScv.structureType);
//                        Cost.updateBank(sScv.structureType);
//                        System.out.println("GameCache.mineralBank = " + GameCache.mineralBank);
//                    });
//        }
//    }

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
        for (int i = 0; i<scvBuildingList.size(); i++) {
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
            UnitInPool structure = structureScv.getStructureUnit();

            //just requeue if both structure and scv don't exist
            if (!structureScv.scv.isAlive() && (structure == null || !structure.isAlive())) {
                requeueCancelledStructure(structureScv);
                remove(structureScv);
                i--;
                continue;
            }

            //replace dead scv with repairScv (if available) for proxy bunker structures
            if (!structureScv.scv.isAlive() &&
                    BunkerContain.proxyBunkerLevel > 0 &&
                    Time.nowFrames() < Time.toFrames("5:00") &&
                    structureScv.structurePos.distance(PosConstants.myRampPos) > 50) {
                BunkerContain.repairScvList.stream()
                        .filter(u -> !StructureScv.contains(u.unit()))
                        .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), structureScv.structurePos)))
                        .ifPresent(u -> structureScv.setScv(u));
            }

            //replace dead scv with new scv
            if (!structureScv.scv.isAlive()) {
                //don't add another scv if the structure is under enemy threat (exception for wall/bunkers/turrets)
                if (structureScv.structureType != Units.TERRAN_BUNKER &&
                        structureScv.structureType != Units.TERRAN_MISSILE_TURRET &&
                        !UnitUtils.isWallingStructure(structureScv.structurePos) &&
                        InfluenceMaps.getThreatToStructure(structureScv.structureType, structureScv.structurePos) > 1) {
                    continue;
                }
                UnitInPool availableScv = WorkerManager.getScvEmptyHands(structureScv.structurePos);
                if (availableScv != null) {
                    structureScv.setScv(availableScv);
                }
            }

            //if scv doesn't have the build command
            if (!doesScvHaveBuildOrder(structureScv)) {
                //if structure never started or was destroyed, then repurchase
                if (structure == null || !structure.isAlive()) {

                    //any unit within 5 that is a snapshot, or a non-cloaked/non-burrowed unit
                    if (structureScv.structureType == Units.TERRAN_COMMAND_CENTER) {
                        if (UnitUtils.getDistance(structureScv.scv.unit(), structureScv.structurePos) < 9 &&
                                !ExpansionClearing.isVisiblyBlockedByUnit(structureScv.structurePos)) { //creep or burrowed/cloaked
                            ExpansionClearing.add(structureScv.structurePos);
                        }
                    }

                    //just debug testing logs TODO: remove
                    System.out.println("Frame#" + Time.nowFrames());
                    System.out.println("structureScv.buildAbility = " + structureScv.buildAbility);
                    System.out.println("scv's current ability = " + ActionIssued.getCurOrder(structureScv.scv).stream().map(actionIssued -> actionIssued.ability).findFirst().orElse(null));
                    System.out.println("ActionIssued.lastActionIssued.get(structureScv.scv) != null = " + (ActionIssued.lastActionIssued.get(structureScv.scv) != null));
                    System.out.println("ActionIssued.getCurOrder(structureScv.scv).stream().noneMatch(order -> order.ability == structureScv.buildAbility) = " + ActionIssued.getCurOrder(structureScv.scv).stream().noneMatch(order -> order.ability == structureScv.buildAbility));
                    System.out.println("structureScv.scv.unit().getOrders().stream().noneMatch(order -> order.getAbility() == structureScv.buildAbility) = " + structureScv.scv.unit().getOrders().stream().noneMatch(order -> order.getAbility() == structureScv.buildAbility));

                    requeueCancelledStructure(structureScv);
                    remove(structureScv);
                    i--;
                }
                //if structure started but not complete
                else if (structure.unit().getBuildProgress() < 1.0f) {

                    //remove if there is a duplicate entry in scvBuildList
                    if (isDuplicateStructureScv(structureScv)) {
                        remove(structureScv);
                        i--;
                    }

                    //send scv to structure
                    else {
                        ActionHelper.unitCommand(structureScv.scv.unit(), Abilities.SMART, structure.unit(), false);
                    }
                }

                //if structure completed
                else if (structure.unit().getBuildProgress() == 1.0f) {
                    if (UnitUtils.REFINERY_TYPE.contains(structureScv.structureType)) {
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

    private static boolean doesScvHaveBuildOrder(StructureScv structureScv) {
        return ActionIssued.getCurOrder(structureScv.scv).stream().anyMatch(order -> order.ability == structureScv.buildAbility) ||
                structureScv.scv.unit().getOrders().stream().anyMatch(order -> order.getAbility() == structureScv.buildAbility); // scv may have their order queued if it just finished building another structure
    }

    //makes structure position available again, then requeues structure purchase (sometimes)
    public static void requeueCancelledStructure(StructureScv structureScv) {
        Print.print("structure requeued:" + structureScv.structureType);
        int index;
        switch (structureScv.structureType) {
            //don't queue rebuild on these structure types
            case TERRAN_COMMAND_CENTER:
            case TERRAN_REFINERY: case TERRAN_REFINERY_RICH:
                break;
            case TERRAN_BUNKER:
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType, structureScv.structurePos));
                break;
            case TERRAN_SUPPLY_DEPOT:
                if (UnitUtils.isWallingStructure(structureScv.structurePos)) {
                    PosConstants.extraDepots.add(0, structureScv.structurePos);
                }
                else {
                    PosConstants.extraDepots.add(structureScv.structurePos);
                }
                break;
            case TERRAN_FACTORY: case TERRAN_STARPORT:
                index = Math.max(1, PosConstants._3x3AddonPosList.size());
                PosConstants._3x3AddonPosList.add(index, structureScv.structurePos);
                break;
            case TERRAN_BARRACKS:
                if (PosConstants.proxyBarracksPos == null || structureScv.structurePos.distance(PosConstants.proxyBarracksPos) > 10) {
                    PosConstants._3x3Structures.add(structureScv.structurePos); //FIXME: decide if rax are using 3x3 or 3x3+addon list
                }
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType));
                break;
            case TERRAN_ARMORY: case TERRAN_ENGINEERING_BAY: case TERRAN_GHOST_ACADEMY: case TERRAN_FUSION_CORE:
                index = Math.max(1, PosConstants._3x3Structures.size());
                PosConstants._3x3Structures.add(index, structureScv.structurePos);
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
    public static boolean contains(Unit scv) {
        return scvBuildingList.stream()
                .anyMatch(structureScv -> structureScv.scv.getTag().equals(scv.getTag()));
    }

    //checks if a structure is being built at this position
    public static boolean contains(Point2d pos) {
        return scvBuildingList.stream()
                .anyMatch(structureScv -> structureScv.structurePos.distance(pos) < 0.5f);
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
            UnitUtils.returnAndStopScv(structureScv.scv);
        }
        if (structureScv.structureType == Units.TERRAN_COMMAND_CENTER && structureScv.getStructureUnit() == null) {
            GameCache.baseList.stream()
                    .filter(base -> base.getCcPos().distance(structureScv.structurePos) < 1)
                    .forEach(base -> base.setCc(null));
        }
        scvBuildingList.remove(structureScv);
    }


}
