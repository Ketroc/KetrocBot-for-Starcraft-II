package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;


public class WorkerManager {
    public static int scvsPerGas = 3;

    public static void onStep() {
        Strategy.setMaxScvs();
        repairLogic();
        //fix3ScvsOn1MineralPatch();
        fixOverSaturation();
        toggleWorkersInGas();
        buildRefineryLogic();
        defendWorkerHarass(); //TODO: this method break scvrush micro

    }

    private static void defendWorkerHarass() {
        //only for first 3min
        if (Time.nowFrames() > Time.toFrames("3:00")) {
            return;
        }

        //target scout workers
        List<Unit> enemyScoutWorkers = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType);
        List<UnitInPool> myScvs = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, LocationConstants.baseLocations.get(0), 50f);
        for (Unit enemyWorker : enemyScoutWorkers) {
            Point2d enemyWorkerPos = enemyWorker.getPosition().toPoint2d();
            if (UnitUtils.getDistance(enemyWorker, LocationConstants.baseLocations.get(0)) < 40) {
                Optional<Unit> attackingScv = myScvs.stream()
                        .map(UnitInPool::unit)
                        .filter(scv -> UnitUtils.isAttacking(scv, enemyWorker))
                        .findFirst();
                if (attackingScv.isPresent()) {
                    if (attackingScv.get().getHealth().orElse(0f) <= 10f) {
                        Bot.ACTION.unitCommand(attackingScv.get(), Abilities.STOP, false);
                        Ignored.remove(attackingScv.get().getTag());
                        sendScvToAttack(enemyWorker);
                    }
                }
                else {
                    sendScvToAttack(enemyWorker);
                }
            }
        }

//        //abandon attacking scout workers
//        for (UnitInPool scv : myScvs) {
//            if (!scv.unit().getOrders().isEmpty()) {
//                Tag targetTag = scv.unit().getOrders().get(0).getTargetedUnitTag().orElse(null);
//                if (targetTag != null) {
//                    UnitInPool targetUnit = Bot.OBS.getUnit(targetTag);
//                    if (targetUnit.unit().getType() == UnitUtils.enemyWorkerType) {
//                        Point2d targetPos = targetUnit.unit().getPosition().toPoint2d();
//                        if (!LocationConstants.pointInMainBase[Position.getMapCoord(targetPos.getX())][Position.getMapCoord(targetPos.getY())] &&
//                                !LocationConstants.pointInNat[Position.getMapCoord(targetPos.getX())][Position.getMapCoord(targetPos.getY())]) {
//                            Bot.ACTION.unitCommand(scv.unit(), Abilities.STOP, false);
//                            IgnoredUnit.remove(scv.getTag());
//                        }
//                    }
//                }
//            }
//        }
    }

    private static void sendScvToAttack(Unit enemy) {
        List<UnitInPool> availableScvs = getAvailableScvs(enemy.getPosition().toPoint2d()).stream()
                .filter(scv -> scv.unit().getHealth().orElse(1f) > 39f)
                .collect(Collectors.toList());
        if (!availableScvs.isEmpty()) {
            Ignored.add(new IgnoredScvDefender(availableScvs.get(0).getTag(), Bot.OBS.getUnit(enemy.getTag())));
            Bot.ACTION.unitCommand(availableScvs.get(0).unit(), Abilities.ATTACK, enemy, false);
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
                            availableScvs = UnitUtils.toUnitList(Bot.OBS.getUnits(Alliance.SELF, u -> {
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
        for (Base base : GameCache.baseList) {
            List<Unit> harvestingScvs = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, base.getCcPos(), 10).stream()
                    .map(UnitInPool::unit)
                    .filter(scv -> !scv.getOrders().isEmpty() &&
                            scv.getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER &&
                            scv.getOrders().get(0).getTargetedUnitTag().isPresent())
                    .collect(Collectors.toList());

            Set<Tag> uniquePatchTags = new HashSet<>();
            Tag overloadedPatch = null;
            Unit thirdScv = null;
            for (Unit scv: harvestingScvs) {
                //if can't add, then duplicate
                Tag targetPatchTag = scv.getOrders().get(0).getTargetedUnitTag().get();
                if (uniquePatchTags.add(targetPatchTag)) {
                    overloadedPatch = targetPatchTag;
                    thirdScv = scv;
                }
            }
            if (overloadedPatch == null) {
                return;
            }

            Unit availablePatch = null;
            for (Unit mineral : base.getMineralPatches()) {
                if (!uniquePatchTags.contains(mineral.getTag())) {
                    availablePatch = mineral;
                    Bot.ACTION.unitCommand(thirdScv, Abilities.HARVEST_GATHER, availablePatch, false);
                    break;
                }
            }
            if (availablePatch == null) {
                return;
            }


            if (KetrocBot.isDebugOn) {
                Point2d thirdScvPos = thirdScv.getPosition().toPoint2d();
                Point2d overloadedPatchPos = Bot.OBS.getUnit(overloadedPatch).unit().getPosition().toPoint2d();
                Point2d availablePatchPos = availablePatch.getPosition().toPoint2d();
                float z = Bot.OBS.terrainHeight(thirdScvPos) + 0.8f;
                Bot.DEBUG.debugBoxOut(Point.of(thirdScvPos.getX()-0.3f,thirdScvPos.getY()-0.3f, z), Point.of(thirdScvPos.getX()+0.3f,thirdScvPos.getY()+0.3f, z), Color.YELLOW);
                Bot.DEBUG.debugBoxOut(Point.of(overloadedPatchPos.getX()-0.3f,overloadedPatchPos.getY()-0.3f, z), Point.of(overloadedPatchPos.getX()+0.3f,overloadedPatchPos.getY()+0.3f, z), Color.YELLOW);
                Bot.DEBUG.debugBoxOut(Point.of(availablePatchPos.getX()-0.3f,availablePatchPos.getY()-0.3f, z), Point.of(availablePatchPos.getX()+0.3f,availablePatchPos.getY()+0.3f, z), Color.YELLOW);
            }
        }
    }

    private static void buildRefineryLogic() {
        //don't build new refineries yet
        if ((LocationConstants.opponentRace == Race.ZERG && GameCache.ccList.size() < 3) ||
                (LocationConstants.opponentRace == Race.PROTOSS && GameCache.ccList.size() < 3)) {
            return;
        }

        //loop through bases
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete(0.60f) //&&
                    //GameCache.mineralBank * 2  > GameCache.gasBank * 3 && GameCache.mineralBank > 100
                    //GameCache.mineralBank * 2  > GameCache.gasBank * 3 && GameCache.mineralBank > 100
            ) {
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() == null && gas.getGeyser().getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY) {
                        if (StructureScv.scvBuildingList.stream()
                                .noneMatch(scv -> scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getLocation()) < 1)) {
                            if (!Purchase.isStructureQueued(Units.TERRAN_REFINERY)) {
                                KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_REFINERY));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public static List<Unit> getAvailableScvUnits(Point2d targetPosition) {
        return UnitUtils.toUnitList(getAvailableScvs(targetPosition, 10));
    }

    public static List<Unit> getAllScvUnits(Point2d targetPosition) {
        return UnitUtils.toUnitList(getAllScvs(targetPosition, 10));
    }

    public static UnitInPool getOneScv(Point2d targetPosition) {
        List<UnitInPool> oneScvList = getAvailableScvs(targetPosition, 20, false, true);
        return (!oneScvList.isEmpty()) ? oneScvList.get(0) : null;
    }
    public static UnitInPool getOneScv(Point2d targetPosition, int distance) {
        List<UnitInPool> oneScvList = getAvailableScvs(targetPosition, distance, true, true);
        return (!oneScvList.isEmpty()) ? oneScvList.get(0) : null;
    }
    public static UnitInPool getOneScv() {
        List<UnitInPool> oneScvList = getAvailableScvs(ArmyManager.retreatPos, Integer.MAX_VALUE, true, true);
        return (!oneScvList.isEmpty()) ? oneScvList.get(0) : null;
    }
    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition) {
        return getAvailableScvs(targetPosition, 20, false, true);
    }
    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance) {
        return getAvailableScvs(targetPosition, distance, true, true);
    }
    public static List<UnitInPool> getAllAvailableScvs() {
        return getAvailableScvs(ArmyManager.retreatPos, Integer.MAX_VALUE, true, true);
    }

    public static List<UnitInPool> getAvailableMineralScvs(Point2d targetPosition) {
        return getAvailableScvs(targetPosition, 20, false, false);
    }

    public static List<UnitInPool> getAvailableMineralScvs(Point2d targetPosition, int distance) {
        return getAvailableScvs(targetPosition, distance, true, false);
    }

    public static List<UnitInPool> getAvailableMineralScvs(Point2d targetPosition, int distance, boolean isDistanceEnforced) {
        return getAvailableScvs(targetPosition, distance, isDistanceEnforced, false);
    }

    public static List<UnitInPool> getAllAvailableMineralScvs() {
        return getAvailableScvs(ArmyManager.retreatPos, Integer.MAX_VALUE, true, false);
    }

    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance, boolean isDistanceEnforced) {
        return getAvailableScvs(targetPosition, distance, isDistanceEnforced, true);
    }

    //return list of scvs that are mining minerals without holding minerals within an optional distance
    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance, boolean isDistanceEnforced, boolean includeGasScvs) {
        List<UnitInPool> scvList = Bot.OBS.getUnits(Alliance.SELF, scv -> {
            return scv.unit().getType() == Units.TERRAN_SCV &&
                    (scv.unit().getOrders().isEmpty() || isMiningMinerals(scv) || (includeGasScvs && isMiningGas(scv))) &&
                    UnitUtils.getDistance(scv.unit(), targetPosition) < distance &&
                    !Ignored.contains(scv.getTag());
        });

//        List<UnitInPool> scvList = GameCache.availableScvs.stream()
//                .filter(scv -> Bot.QUERY.pathingDistance(scv.unit(), targetPosition) < distance)
//                .collect(Collectors.toList());

        if (scvList.isEmpty() && !isDistanceEnforced) {
            return getAvailableScvs(targetPosition, Integer.MAX_VALUE, true, includeGasScvs);
        }
        return scvList;
    }

    public static UnitInPool getClosestAvailableScv(Point2d targetPosition) {
        List<UnitInPool> scvList = getAvailableScvs(targetPosition, Integer.MAX_VALUE, true, true);
        return scvList.stream()
                .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), targetPosition)))
                .orElse(null);
    }

    //return list of all scvs within a distance
    public static List<UnitInPool> getAllScvs(Point2d targetPosition, int distance) {
        return Bot.OBS.getUnits(Alliance.SELF, scv ->
                scv.unit().getType() == Units.TERRAN_SCV &&
                !Ignored.contains(scv.getTag()) &&
                targetPosition.distance(scv.unit().getPosition().toPoint2d()) < distance);
    }


    public static boolean isMiningMinerals(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.MINERAL_NODE_TYPE);
    }

    public static boolean isMiningGas(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.REFINERY_TYPE);
    }

    private static boolean isMiningNode(UnitInPool scv, Set<Units> nodeType) { //TODO: doesn't include scvs returning minerals
        if (scv.unit().getOrders().size() != 1 || scv.unit().getOrders().get(0).getAbility() != Abilities.HARVEST_GATHER) {
            return false;
        }

        //if returning resources
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
                List<UnitInPool> availableScvs = getAvailableMineralScvs(base.getCcPos(), 10);

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
            List<Unit> idleScvs = UnitUtils.toUnitList(
                    Bot.OBS.getUnits(Alliance.SELF, scv ->
                            scv.unit().getType() == Units.TERRAN_SCV &&
                                    scv.unit().getOrders().isEmpty() &&
                                    !Ignored.contains(scv.getTag())));
            scvsToMove.addAll(0, idleScvs);
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
        //skip logic until there are at least 2 refineries
        int numRefineries = UnitUtils.getNumFriendlyUnits(UnitUtils.REFINERY_TYPE, false);
        if (numRefineries <= 1) {
            return false;
        }

        int mins = GameCache.mineralBank;
        int gas = GameCache.gasBank;
        if (scvsPerGas == 1) {
            if (gasBankRatio() < 0.6) {
                scvsPerGas = 2;
                return true;
            }
        }
        else if (scvsPerGas == 2) {
            //if late game with bank, or if >3:1 mins:gas, then max gas income
            if (Time.nowFrames() > Time.toFrames("3:00") && (mins > 3000 || (mins > 300 && gasBankRatio() < 0.3))) {
                scvsPerGas = 3;
                return true;
            }
            //go to 1 in gas
            if (gas > 700 && gasBankRatio() > 0.75) {
                scvsPerGas = 1;
                return true;
            }
        }
        else if (scvsPerGas == 3) {
            if (mins < 2750 && gas > 80*GameCache.starportList.size() && gasBankRatio() > 0.5) {
                scvsPerGas = 2;
                return true;
            }
        }
        return false;
    }

    private static float gasBankRatio() {
        return Math.max(GameCache.gasBank, 1f) / (Math.max(GameCache.gasBank, 1f) + Math.max(GameCache.mineralBank, 1f));
    }

    private static List<Unit> getDeepestMineralScvs(int numScvs) {
        List<UnitInPool> scvs = new ArrayList<>();
        int scvsNeeded = numScvs;
        for (Base base : GameCache.baseList) {
            if (base.isMyBase()) {
                List<UnitInPool> baseScvs = getAvailableMineralScvs(base.getCcPos(), 10, true);
                if (baseScvs.size() >= scvsNeeded) {
                    scvs.addAll(baseScvs.subList(0, scvsNeeded));
                    break;
                } else {
                    scvs.addAll(baseScvs);
                    scvsNeeded -= baseScvs.size();
                }
            }
        }
        return scvs.stream().map(UnitInPool::unit).collect(Collectors.toList());
    }

    //Up a new pf base to a minimum of 8 scvs
    public static void sendScvsToNewPf(Unit pf) {
        Point2d pfPos = pf.getPosition().toPoint2d();
        int scvsNeeded = 8 - UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, pfPos, 10).size();
        if (scvsNeeded <= 0) {
            return;
        }
        Unit targetNode = getMiningNodeAtBase(pfPos);
        if (targetNode == null) {
            return;
        }

        List<Unit> scvs = getDeepestMineralScvs(scvsNeeded);
        Bot.ACTION.unitCommand(scvs, Abilities.SMART, targetNode, false);
    }

    private static Unit getMiningNodeAtBase(Point2d basePos) {
        List<UnitInPool> mineralPatches = UnitUtils.getUnitsNearbyOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_NODE_TYPE, basePos, 10);
        if (!mineralPatches.isEmpty()) {
            return mineralPatches.get(0).unit();
        }
        else {
            List<UnitInPool> refineries = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.REFINERY_TYPE, basePos, 10);
            if (!refineries.isEmpty()) {
                return refineries.get(0).unit();
            }
        }
        return null;
    }
}
