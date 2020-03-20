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

    public static void onGameStart() {
        System.out.println("sending action @" + Bot.OBS.getGameLoop() + Abilities.TRAIN_SCV);
        Bot.ACTION.unitCommand(GameState.baseList.get(0).getCc(), Abilities.TRAIN_SCV, false);
    }

    public static void onStep() {
        repairLogic();
        if (Bot.OBS.getGameLoop() % 200 == 0) { //~every 10sec
            fixOverSaturation();
            buildRefineryLogic();
        }

    }

    private static void repairLogic() {
        //loop through units.  look for unmaxed health.  decide numscvs to repair
        for (Base base : GameState.baseList) {
            Unit cc = base.getCc();
            int ccHealth = UnitUtils.getHealthPercentage(cc);
            if (ccHealth < 100) {
                int numScvsToAdd = UnitUtils.getIdealScvsToRepair(cc) - UnitUtils.numRepairingScvs(cc);
                if (numScvsToAdd > 0) {
                    List<Unit> availableScvs = getAvailableScvUnits(cc.getPosition().toPoint2d());
                    availableScvs = availableScvs.subList(0, Math.max(0, Math.min(availableScvs.size()-1, numScvsToAdd)));
                    if (!availableScvs.isEmpty()) {
                        System.out.println("sending " + availableScvs.size() + " scvs to repair.");
                        Unit mineralNode;
                        if (base.getMineralPatches().isEmpty()) {
                            mineralNode = GameState.mineralNodeRally.unit();
                        }
                        else {
                            mineralNode = base.getMineralPatches().get(0).unit();
                        }
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR_SCV, cc, false)
                                .unitCommand(availableScvs, Abilities.SMART, mineralNode, true);
                    }
                }
            }
        }
    }

    private static void buildRefineryLogic() { //handle 2nd scv going to the same geyser as previous scv who hadn't gotten there yet
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
        List<UnitInPool> result = getAvailableScvs(targetPosition, 20);
        return unitInPoolToUnitList(getAvailableScvs(targetPosition, 20));
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
            return scv.unit().getType() == Units.TERRAN_SCV &&
                    !scv.unit().getOrders().isEmpty() &&
                    (scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && isMiningMinerals(scv)) && //is mining minerals
                    (targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance);
        });
        if (scvList.isEmpty() && !isDistanceEnforced) {
            return getAvailableScvs(targetPosition, Integer.MAX_VALUE, true);
        }
        return scvList;
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
        UnitInPool oldNode = GameState.mineralNodeRally;
        GameState.mineralNodeRally = null; //reset rally node

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

            int numScvsOverSaturation = cc.getAssignedHarvesters().get() - numScvsMovingToGas - cc.getIdealHarvesters().get();
            if (numScvsOverSaturation < 0) { //set this base as rally point
               if (GameState.mineralNodeRally == null && !base.getMineralPatches().isEmpty()) {
                   GameState.mineralNodeRally = base.getMineralPatches().get(0);
               }
            }
            else if (numScvsOverSaturation > 0) {
                //add extra mineral scvs
                for (int i = 0; i < numScvsOverSaturation && i < availableScvs.size(); i++) {
                    scvsToMove.add(availableScvs.get(i).unit());
                }
            }
        } //end loop through bases

        //set rally to newest base if still null
        if (GameState.mineralNodeRally == null) {
            GameState.mineralNodeRally = oldNode;
        }

        // add all idle workers
        if (Bot.OBS.getIdleWorkerCount() > 0) {
            scvsToMove.addAll(unitInPoolToUnitList(
                    Bot.OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && scv.unit().getActive().get() == false)
            ));
        }

        //send scvs to newest base's mineral line
        if (!scvsToMove.isEmpty()) {
            Bot.ACTION.unitCommand(scvsToMove, Abilities.SMART, GameState.mineralNodeRally.unit(), false);
        }

        //rally all CCs to newest base's mineral line
        for (Unit cc : GameState.ccList) {
            Bot.ACTION.unitCommand(cc, Abilities.RALLY_COMMAND_CENTER, GameState.mineralNodeRally.unit(), false);
        }
    }

    private static boolean isWorkerLineFull(Unit cc) {
        int scvCount = cc.getAssignedHarvesters().orElse(0);
        int scvCountIdeal = cc.getIdealHarvesters().orElse(0);
        return (scvCount >= scvCountIdeal);
    }

    public static List<Unit> unitInPoolToUnitList(List<UnitInPool> unitInPoolList) {
        return unitInPoolList.stream().map(UnitInPool::unit).collect(Collectors.toList());
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
