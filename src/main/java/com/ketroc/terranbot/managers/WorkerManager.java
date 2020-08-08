package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.Gas;
import com.ketroc.terranbot.models.StructureScv;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.*;
import java.util.stream.Collectors;


public class WorkerManager {
    public static int scvsPerGas = 3;

    public static void onStep() {
        Strategy.setMaxScvs();
        repairLogic();
        fix3ScvsOn1MineralPatch();
        fixOverSaturation();
        toggleWorkersInGas();
        if ((LocationConstants.opponentRace == Race.TERRAN) ||
                (LocationConstants.opponentRace == Race.ZERG && GameCache.ccList.size() >= 3) ||
                (LocationConstants.opponentRace == Race.PROTOSS && GameCache.ccList.size() >= 3)) {
            buildRefineryLogic();
        }
    }

    private static void repairLogic() {  //TODO: don't repair wall if ranged units on other side
        //loop through units.  look for unmaxed health.  decide numscvs to repair
        List<Unit> unitsToRepair = new ArrayList<>(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_PLANETARY_FORTRESS, new ArrayList<>()));
        unitsToRepair.addAll(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_MISSILE_TURRET, Collections.emptyList()));
        unitsToRepair.addAll(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_BUNKER, Collections.emptyList()));
        if (LocationConstants.opponentRace != Race.PROTOSS) { //libs on top of PF vs toss so unreachable by scvs to repair
            unitsToRepair.addAll(GameCache.liberatorList);
        }
        unitsToRepair.addAll(GameCache.siegeTankList);
        unitsToRepair.addAll(GameCache.wallStructures);
        unitsToRepair.addAll(GameCache.burningStructures);
        for (Unit unit : unitsToRepair) {
            int structureHealth = UnitUtils.getHealthPercentage(unit);
            if (structureHealth < 100) {
                int numScvsToAdd = UnitUtils.getIdealScvsToRepair(unit) - UnitUtils.numRepairingScvs(unit);
                if (numScvsToAdd > 0) {
                    List<Unit> availableScvs;
                    if (numScvsToAdd > 9999) {
                        availableScvs = getAllScvUnits(unit.getPosition().toPoint2d()); //TODO: FIX: this will include already repairing scvs, and constructing scvs
                    }
                    else {
                        //only choose scvs inside the wall within 20 distance
                        if (GameCache.wallStructures.contains(unit)) {
                            availableScvs = UnitUtils.unitInPoolToUnitList(Bot.OBS.getUnits(Alliance.SELF, u -> {
                                return u.unit().getType() == Units.TERRAN_SCV &&
                                        Math.round(u.unit().getPosition().getZ()) == Math.round(unit.getPosition().getZ()) && //inside the wall (round cuz z-coordinate isn't ever exactly the same at same level)
                                        u.unit().getPosition().distance(unit.getPosition()) < 30 &&
                                        (u.unit().getOrders().isEmpty() || (u.unit().getOrders().size() == 1 && u.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && isMiningMinerals(u)));
                            }));
                        }
//                        if (GameState.burningStructures.contains(unit) || GameState.wallStructures.contains(unit)) {
//                            //only send if safe
//                            //TODO: make threat to ground gridmap to check against (replace above if statement for wall structures)
//                        }

                        else {
                            availableScvs = getAvailableScvUnits(unit.getPosition().toPoint2d());
                        }
                        availableScvs = availableScvs.subList(0, Math.max(0, Math.min(availableScvs.size()-1, numScvsToAdd)));
                    }
                    if (!availableScvs.isEmpty()) {
                        System.out.println("sending " + availableScvs.size() + " scvs to repair.");
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR_SCV, unit, false);
                    }
                }
            }
        }
    }

    public static void fix3ScvsOn1MineralPatch() {
//        for (Base base : GameCache.baseList) {
//            for (Unit mineral : base.getMineralPatches()) {
//                mineral.
//            }
//        }
    }

    private static void buildRefineryLogic() {
        //only allow 1 refinery to be built at a time
//        if (!Strategy.ARCHON_MODE) {
//            int numProducingRefineries = (int) (StructureScv.scvBuildingList.stream().filter(scv -> scv.structureType == Units.TERRAN_REFINERY).count() +
//                    Bot.purchaseQueue.stream().filter(p -> p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType() == Units.TERRAN_REFINERY).count());
//            if (numProducingRefineries >= 1) {
//                return;
//            }
//        }

        //loop through bases
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete(0.60f) //&&
                    //(Strategy.ARCHON_MODE || GameCache.mineralBank * 2  > GameCache.gasBank * 3) && GameCache.mineralBank > 100
            ) {
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() == null && gas.getGeyser().getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY) {
                        if (StructureScv.scvBuildingList.stream()
                                .noneMatch(scv -> scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getLocation()) < 1)) {
                            if (!Purchase.isStructureQueued(Units.TERRAN_REFINERY)) {
                                Bot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_REFINERY));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public static List<Unit> getAvailableScvUnits(Point2d targetPosition) {
        return UnitUtils.unitInPoolToUnitList(getAvailableScvs(targetPosition, 10));
    }

    public static List<Unit> getAllScvUnits(Point2d targetPosition) {
        return UnitUtils.unitInPoolToUnitList(getAllScvs(targetPosition, 10));
    }

    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition) {
        return getAvailableScvs(targetPosition, 20, false);
    }
    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance) {
        return getAvailableScvs(targetPosition, distance, true);
    }
    public static List<UnitInPool> getAllAvailableScvs() {
        return getAvailableScvs(ArmyManager.retreatPos, Integer.MAX_VALUE, true);
    }

    //return list of scvs that are mining minerals without holding minerals within an optional distance
    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance, boolean isDistanceEnforced) {
        List<UnitInPool> scvList = Bot.OBS.getUnits(Alliance.SELF, scv -> {
            return scv.unit().getType() == Units.TERRAN_SCV &&
                    !GameCache.buildingScvTags.contains(scv.getTag()) &&
                    (scv.unit().getOrders().isEmpty() ||
                            (scv.unit().getOrders().size() == 1 && scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && isMiningMinerals(scv))) && //size==1 is too make sure there isn't a 2nd order like a build command
                    targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance;
        });
        if (scvList.isEmpty() && !isDistanceEnforced) {
            return getAvailableScvs(targetPosition, Integer.MAX_VALUE, true);
        }
        return scvList;
    }

    //return list of all scvs within a distance
    public static List<UnitInPool> getAllScvs(Point2d targetPosition, int distance) {
        return Bot.OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && !GameCache.buildingScvTags.contains(scv.getTag()) && targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance);
    }

    public static List<UnitInPool> getRefineryScvs(Unit refinery) {
        return Bot.OBS.getUnits(Alliance.SELF, scv -> {
            return scv.unit().getType() == Units.TERRAN_SCV && //is scv
                    !scv.unit().getOrders().isEmpty() && //has orders
                    scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && //is not holding gas
                    scv.unit().getOrders().get(0).getTargetedUnitTag().get().equals(refinery.getTag()); //is mining this refinery
        });
    }


    public static boolean isMiningMinerals(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.MINERAL_NODE_TYPE);
    }

    public static boolean isMiningGas(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.REFINERY_TYPE);
    }

    private static boolean isMiningNode(UnitInPool scv, List<Units> nodeType) { //TODO: doesn't include scvs returning minerals
        if (scv.unit().getOrders().isEmpty() || scv.unit().getOrders().get(0).getAbility() != Abilities.HARVEST_GATHER) {
            return false;
        }

        Optional<Tag> scvTargetTag = scv.unit().getOrders().get(0).getTargetedUnitTag();
        if (scvTargetTag.isEmpty()) { //return false if scv has no target
            return false;
        }
        UnitInPool targetNode = Bot.OBS.getUnit(scvTargetTag.get());
        return targetNode != null && nodeType.contains(targetNode.unit().getType());
    }



    private static void fixOverSaturation() {
        List<Unit> scvsToMove = new ArrayList<>();

        //loop through bases
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete()) {
                Unit cc = base.getCc().get().unit();

                //get available scvs at this base
                List<UnitInPool> availableScvs = getAvailableScvs(base.getCcPos(), 10);

                //saturate refineries
                int numScvsMovingToGas = 0;
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() != null) {
                        Unit refinery = gas.getRefinery();
                        for (int i = refinery.getAssignedHarvesters().get(); i < Math.min(refinery.getIdealHarvesters().get(), scvsPerGas) && !availableScvs.isEmpty(); i++) {
                            Bot.ACTION.unitCommand(availableScvs.remove(0).unit(), Abilities.SMART, refinery, false);
                            numScvsMovingToGas++;
                        }
                        if (refinery.getAssignedHarvesters().get() > scvsPerGas) {
                             List<UnitInPool> availableGasScvs = Bot.OBS.getUnits(Alliance.SELF, u -> {
                                if (u.unit().getType() == Units.TERRAN_SCV && !u.unit().getOrders().isEmpty()) {
                                    UnitOrder order = u.unit().getOrders().get(0);
                                    return order.getTargetedUnitTag().isPresent() && order.getAbility() == Abilities.HARVEST_GATHER && order.getTargetedUnitTag().get().equals(refinery.getTag());
                                }
                                return false;
                             });
                             if (!availableGasScvs.isEmpty()) {
                                 scvsToMove.add(0, availableGasScvs.get(0).unit());
                             }
                        }
                    }
                }

                //add extra scvs to list
                base.setExtraScvs(cc.getAssignedHarvesters().get() - numScvsMovingToGas - cc.getIdealHarvesters().get());
                for (int i = 0; i < base.getExtraScvs() && i < availableScvs.size(); i++) {
                    scvsToMove.add(availableScvs.get(i).unit());
                }
            }
        } //end loop through bases

        // add all idle workers to top of same list
        if (Bot.OBS.getIdleWorkerCount() > 0) {
            scvsToMove.addAll(0, UnitUtils.unitInPoolToUnitList(
                    Bot.OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && scv.unit().getOrders().isEmpty()) //TODO: sometimes this captures scvs that are building a structure but have empty getOrders()
            ));
        }

        //send extra scvs to undersaturated bases
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete(0.9f)) {
                if (scvsToMove.isEmpty()) {
                    break;
                }
                if (base.getMineralPatches().isEmpty()) {
                    continue;
                }
                int scvsNeeded = base.getExtraScvs() * -1;
                if (scvsNeeded > 0) {
                    List<Unit> scvsForThisBase = scvsToMove.subList(0, Math.min(scvsNeeded, scvsToMove.size()));
                    Bot.ACTION.unitCommand(scvsForThisBase, Abilities.SMART, base.getRallyNode(), false);
                    scvsForThisBase.clear();
                }
            }
        }

        //put any leftover idle scvs to work
        scvsToMove.removeIf(scv -> !scv.getOrders().isEmpty());
        if (!scvsToMove.isEmpty()) {
            //if no minerals left on map, join the attack
            if (GameCache.defaultRallyNode == null) {
                scvsToMove.stream().filter(scv -> scv.getBuffs().contains(Buffs.AUTOMATED_REPAIR)).forEach(scv -> Bot.ACTION.unitCommand(scv, Abilities.EFFECT_REPAIR_SCV, false));
                UnitUtils.queueUpAttackOfEveryBase(scvsToMove);
            }
            //mine from newest base (or if dried up, distance-mine)
            else {
                Bot.ACTION.unitCommand(scvsToMove, Abilities.SMART, GameCache.defaultRallyNode, false);
            }
        }

//        //rally all CCs to the newest base's mineral line
//        if (!Switches.tvtFastStart && GameState.mineralNodeRally != null && !GameState.ccList.isEmpty()) {
//            Bot.ACTION.unitCommand(GameState.ccList, Abilities.RALLY_COMMAND_CENTER, GameState.mineralNodeRally, false);
//        }
    }

//    public static void changeGasIncome() {
//        if (toggleWorkersInGas()) {
//            List<UnitInPool> allAvailableScvs = getAllAvailableScvs();
//            if (!allAvailableScvs.isEmpty()) {
//                GameCache.baseList.stream()
//                        .filter(base -> base.isMyBase() && base.isComplete())
//                        .flatMap(base -> base.getGases().stream())
//                        .map(Gas::getRefinery)
//                        .filter(refinery -> refinery != null && refinery.getBuildProgress() == 1 && refinery.getAssignedHarvesters().orElse(scvsPerGas) != scvsPerGas)
//                        .forEach(refinery -> {
//                            if (refinery.getAssignedHarvesters().get() < scvsPerGas) {
//                                Unit scvToAdd = allAvailableScvs.stream()
//                                        .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), refinery)))
//                                        .get().unit();
//                                allAvailableScvs.remove(scvToAdd);
//                                Bot.ACTION.unitCommand(scvToAdd, Abilities.SMART, refinery, false);
//                            }
//                            else {
//
//                            }
//                        });
//            }
//        }
//    }

    public static boolean toggleWorkersInGas() {
        if (UnitUtils.getNumUnits(UnitUtils.REFINERY_TYPE, false) <= 1) {
            return false;
        }

        int mins = GameCache.mineralBank;
        int gas = GameCache.gasBank;

        if (scvsPerGas == 1) {
            if (gasBankRatio() < 0.5) {
                scvsPerGas = 2;
                return true;
            }
        }
        else if (scvsPerGas == 2) {
            //if late game with bank, or if >3:1 mins:gas, then max gas income
            if (mins > 3250 || (mins > 370 && gasBankRatio() < 0.25)) {
                scvsPerGas = 3;
                return true;
            }
            //go to 1 in gas
            if (gas > 600 && gasBankRatio() > 0.7) {
                scvsPerGas = 1;
                return true;
            }
        }
        else if (scvsPerGas == 3) {
            if (mins < 2750 && gas > 75*GameCache.starportList.size() && gasBankRatio() > 0.4) {
                scvsPerGas = 2;
                return true;
            }
        }
        return false;
    }

    private static float gasBankRatio() {
        return Math.max(GameCache.gasBank, 1f) / (Math.max(GameCache.gasBank, 1f) + Math.max(GameCache.mineralBank, 1f));
    }

}
