package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.Gas;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;

import java.util.*;
import java.util.stream.Collectors;


public class WorkerManager {

    public static void onStep() {
        repairLogic();
        if (Bot.OBS.getGameLoop() % 120 == 0) { //~every 6sec
            fixOverSaturation();
            buildRefineryLogic();
        }

    }

    private static void repairLogic() {
        //loop through units.  look for unmaxed health.  decide numscvs to repair
        List<UnitInPool> structures = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_PLANETARY_FORTRESS, new ArrayList<UnitInPool>());
        structures.addAll(GameState.allFriendliesMap.getOrDefault(Units.TERRAN_MISSILE_TURRET, Collections.emptyList()));
        structures.addAll(GameState.allFriendliesMap.getOrDefault(Units.TERRAN_BUNKER, Collections.emptyList()));
        for (UnitInPool unitInPool : structures) {
            Unit structure = unitInPool.unit();
            int ccHealth = UnitUtils.getHealthPercentage(structure);
            if (ccHealth < 100) {
                int numScvsToAdd = UnitUtils.getIdealScvsToRepair(structure) - UnitUtils.numRepairingScvs(structure);
                if (numScvsToAdd > 0) {
                    List<Unit> availableScvs;
                    if (numScvsToAdd > 9999) {
                        availableScvs = getAllScvUnits(structure.getPosition().toPoint2d());
                    }
                    else {
                        availableScvs = getAvailableScvUnits(structure.getPosition().toPoint2d());
                        availableScvs = availableScvs.subList(0, Math.max(0, Math.min(availableScvs.size()-1, numScvsToAdd)));
                    }
                    if (!availableScvs.isEmpty()) {
                        System.out.println("sending " + availableScvs.size() + " scvs to repair.");
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR_SCV, structure, false);
                    }
                }
            }
        }
    }

    private static void buildRefineryLogic() { //TODO: handle 2nd scv going to the same geyser as previous scv who hadn't gotten there yet
        for (Purchase p : Bot.purchaseQueue) { //ignore if already in queue
            if (p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType() == Units.TERRAN_REFINERY) {
                return;
            }
        }

        //loop through bases
        int scvCount = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_SCV, Collections.emptyList()).size(); //TODO: won't include scvs inside refineries
        for (Base base : GameState.baseList) {
            if (base.getCc() != null &&
                    (scvCount > 85 || (base.getCc().getAssignedHarvesters().get() > Strategy.NUM_SCVS_BEFORE_GAS || GameState.mineralBank > 2000))) {
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() == null) {
                        Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                        return;
                    }
                }
            }
        }
    }

    private static int getIdealRefineryCount(int totalScvs) {
        if (totalScvs <= 40) {
            return 4;
        }
        if (totalScvs <= 50) {
            return 6;
        }
        if (totalScvs <= 60) {
            return 8;
        }
        return Integer.MAX_VALUE;
    }

    public static List<Unit> getAvailableScvUnits(Point2d targetPosition) {
        return UnitUtils.unitInPoolToUnitList(getAvailableScvs(targetPosition, 20));
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

    //return list of scvs that are mining minerals without holding minerals within an optional distance
    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance, boolean isDistanceEnforced) {
        List<UnitInPool> scvList = Bot.OBS.getUnits(Alliance.SELF, scv -> {
//            return scv.unit().getType() == Units.TERRAN_SCV &&
//                    !scv.unit().getOrders().isEmpty() &&
//                    (scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && isMiningMinerals(scv)) && //is mining minerals
//                    (targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance);
            return scv.unit().getType() == Units.TERRAN_SCV &&
                    (scv.unit().getOrders().isEmpty() ||
                            (scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && isMiningMinerals(scv))) && //is mining minerals
                    targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance;
        });
        if (scvList.isEmpty() && !isDistanceEnforced) {
            return getAvailableScvs(targetPosition, Integer.MAX_VALUE, true);
        }
        return scvList;
    }

    //return list of all scvs within a distance
    public static List<UnitInPool> getAllScvs(Point2d targetPosition, int distance) {
        return Bot.OBS.getUnits(Alliance.SELF, scv -> {
            return scv.unit().getType() == Units.TERRAN_SCV && targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance;
        });
    }

    public static List<UnitInPool> getRefineryScvs(Unit refinery) {
        return Bot.OBS.getUnits(Alliance.SELF, scv -> {
            return scv.unit().getType() == Units.TERRAN_SCV && //is scv
                    !scv.unit().getOrders().isEmpty() && //has orders
                    scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && //is not holding gas
                    scv.unit().getOrders().get(0).getTargetedUnitTag().equals(refinery.getTag()); //is mining this refinery
        });
    }


    public static boolean isMiningMinerals(UnitInPool scv) {
        return isMiningNode(scv, Base.MINERAL_NODE_TYPE);
    }

    public static boolean isMiningGas(UnitInPool scv) {
        return isMiningNode(scv, Arrays.asList(Units.TERRAN_REFINERY));
    }

    private static boolean isMiningNode(UnitInPool scv, List<Units> nodeType) { //TODO: doesn't include scvs returning minerals
        if (scv.unit().getOrders().isEmpty() || scv.unit().getOrders().get(0).getAbility() != Abilities.HARVEST_GATHER) {
            return false;
        }

        Optional<Tag> scvTargetTag = scv.unit().getOrders().get(0).getTargetedUnitTag();
        if (!scvTargetTag.isPresent()) { //return false if scv has no target
            return false;
        }
        UnitInPool targetNode = Bot.OBS.getUnit(scvTargetTag.get());
        return (targetNode == null) ? false : nodeType.contains((Units)targetNode.unit().getType());
    }



    private static void fixOverSaturation() { //TODO: fix >4 on gas (make setting newestMineralNode smarter to not have scvs always transferring
        List<Unit> scvsToMove = new ArrayList<>();

        //loop through bases
        for (Base base : GameState.baseList) {
            Unit cc = base.getCc();

            //get available scvs at this base
            List<UnitInPool> availableScvs = getAvailableScvs(base.getCcPos(), 10);

            //saturate refineries
            int numScvsMovingToGas = 0;
            for (Gas gas : base.getGases()) {
                if (gas.getRefinery() != null) {
                    Unit refinery = gas.getRefinery().unit();
                    for (int i=refinery.getAssignedHarvesters().get(); i < refinery.getIdealHarvesters().get() && !availableScvs.isEmpty(); i++) {
                        Bot.ACTION.unitCommand(availableScvs.remove(0).unit(), Abilities.SMART, refinery, false);
                        numScvsMovingToGas++;
                    }
                }
            }

            //add extra scvs to list
            base.setExtraScvs(cc.getAssignedHarvesters().get() - numScvsMovingToGas - cc.getIdealHarvesters().get());
            for (int i = 0; i < base.getExtraScvs() && i < availableScvs.size(); i++) {
                scvsToMove.add(availableScvs.get(i).unit());
            }
        } //end loop through bases

        // add all idle workers to same list
        if (Bot.OBS.getIdleWorkerCount() > 0) {
            scvsToMove.addAll(UnitUtils.unitInPoolToUnitList(
                    Bot.OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && scv.unit().getActive().get() == false)
            ));
        }

        //send extra scvs to undersaturated bases
        for (Base base : GameState.baseList) {
            int scvsNeeded = base.getExtraScvs() * -1;
            while (scvsNeeded > 0 && scvsToMove.size() > 0) {
                Bot.ACTION.unitCommand(scvsToMove.remove(0), Abilities.SMART, base.getMineralPatches().get(0).unit(), false);
                scvsNeeded--;
            }
            if (scvsNeeded > 0) { //set cc rallies here if saturation is still needed
                GameState.mineralNodeRally = base.getMineralPatches().get(0);
            }
        }

        //rally all CCs to newest base's mineral line
        if (!Switches.TvtFastStart) {
            for (Unit cc : GameState.ccList) {
                Bot.ACTION.unitCommand(cc, Abilities.RALLY_COMMAND_CENTER, GameState.mineralNodeRally.unit(), false);

            }
        }
    }

    private static boolean isWorkerLineFull(Unit cc) {
        int scvCount = cc.getAssignedHarvesters().orElse(0);
        int scvCountIdeal = cc.getIdealHarvesters().orElse(0);
        return (scvCount >= scvCountIdeal);
    }

    public static List<Tag> unitInPoolToTagList(List<UnitInPool> unitInPoolList) {
        return unitInPoolList.stream().map(UnitInPool::getTag).collect(Collectors.toList());
    }


    private static void buildScv(Unit cc) { //TODO: check when to stop making scvs
        if (cc.getType() == Units.TERRAN_ORBITAL_COMMAND || cc.getType() == Units.TERRAN_PLANETARY_FORTRESS ||
                Bot.purchaseQueue.isEmpty() || //if cc isn't waiting for an OC or PF upgrade, then make scv
                !(Bot.purchaseQueue.getFirst() instanceof PurchaseStructureMorph) ||
                !((PurchaseStructureMorph) Bot.purchaseQueue.getFirst()).getStructure().getTag().equals(cc.getTag()) ||
                GameState.barracksList.isEmpty()) { //TODO: barracks is a hardcode for OC... fix to handle PF too
            System.out.println("sending action @" + Bot.OBS.getGameLoop() + Abilities.TRAIN_SCV);
            Bot.ACTION.unitCommand(cc, Abilities.TRAIN_SCV, true);
            Cost.updateBank(Units.TERRAN_SCV);
        }
        else//TODO: delete
            System.out.println(((PurchaseStructureMorph) Bot.purchaseQueue.getFirst()).getStructure().getTag().equals(cc.getTag()));
    }

}
