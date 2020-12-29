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
import com.ketroc.terranbot.utils.InfluenceMaps;
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
        preventMulesFromDyingWithMineralsInHand();
    }

    //any mule in one of my bases that can't complete another mining round, will a-move + autorepair instead
    private static void preventMulesFromDyingWithMineralsInHand() {
        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MULE).stream()
                .filter(mule -> UnitUtils.getOrder(mule) == Abilities.HARVEST_GATHER &&
                        mule.getBuffDurationRemain().orElse(0) < 144 &&
                        UnitUtils.getDistance(mule,
                                UnitUtils.getClosestUnitOfType(Alliance.SELF, UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN, mule.getPosition().toPoint2d())
                        ) < 3)
                .forEach(mule -> {
                    Bot.ACTION.unitCommand(mule, Abilities.ATTACK, ArmyManager.attackGroundPos, false);
                    if (!mule.getBuffs().contains(Buffs.AUTOMATED_REPAIR)) {
                        Bot.ACTION.toggleAutocast(mule.getTag(), Abilities.EFFECT_REPAIR_MULE);
                    }
                });
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
        List<Unit> unitsToRepair = new ArrayList<>(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_PLANETARY_FORTRESS));
        unitsToRepair.addAll(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_MISSILE_TURRET, Collections.emptyList()));
        unitsToRepair.addAll(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_BUNKER, Collections.emptyList()));
        if (LocationConstants.opponentRace != Race.PROTOSS) { //libs on top of PF vs toss so unreachable by scvs to repair
            unitsToRepair.addAll(GameCache.liberatorList);
        }
        unitsToRepair.addAll(GameCache.siegeTankList); //TODO: include siege tanks in motion??
        unitsToRepair.addAll(GameCache.wallStructures);
        unitsToRepair.addAll(GameCache.burningStructures);
        for (Unit unit : unitsToRepair) {
            int numScvsToAdd = UnitUtils.getIdealScvsToRepair(unit) - UnitUtils.numRepairingScvs(unit);
            if (numScvsToAdd <= 0) { //skip if no additional scvs required
                break;
            }
            List<Unit> scvsForRepair = getScvsForRepairing(unit, numScvsToAdd);
            if (!scvsForRepair.isEmpty()) {
                System.out.println("sending " + scvsForRepair.size() + " scvs to repair.");
                //line up scvs behind PF before giving repair command
                if (UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_PLANETARY_FORTRESS).contains(unit)) {
                    Base pfBase = Base.getBase(unit);
                    if (pfBase != null && scvNotBehindPF(unit, pfBase)) {
                        Bot.ACTION.unitCommand(scvsForRepair, Abilities.MOVE, pfBase.getResourceMidPoint(), false)
                                .unitCommand(scvsForRepair, Abilities.EFFECT_REPAIR_SCV, unit, true);
                    }
                    else {
                        Bot.ACTION.unitCommand(scvsForRepair, Abilities.EFFECT_REPAIR_SCV, unit, false);
                    }
                }
                else {
                    Bot.ACTION.unitCommand(scvsForRepair, Abilities.EFFECT_REPAIR_SCV, unit, false);
                }
            }
        }
    }

    private static boolean scvNotBehindPF(Unit unit, Base pfBase) {
        return UnitUtils.getDistance(unit, pfBase.getCcPos()) + 1 < UnitUtils.getDistance(unit, pfBase.getResourceMidPoint());
    }

    private static List<Unit> getScvsForRepairing(Unit unit, int numScvsToAdd) {
        List<Unit> availableScvs;
        if (numScvsToAdd > 9999) {
            availableScvs = getAllScvUnits(unit.getPosition().toPoint2d()).stream()
                    .filter(scv -> UnitUtils.isScvRepairing(scv))
                    .collect(Collectors.toList());
        }
        else {
            //only choose scvs inside the wall within 20 distance
            if (GameCache.wallStructures.contains(unit) && !isRangedEnemyNearby()) {
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
        return availableScvs;
    }

    private static boolean isRangedEnemyNearby() {
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, LocationConstants.insideMainWall) > 0;
    }

    public static void fix3ScvsOn1MineralPatch() {
        for (Base base : GameCache.baseList) {
            List<Unit> harvestingScvs = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, base.getCcPos(), 10).stream()
                    .map(UnitInPool::unit)
                    .filter(scv -> UnitUtils.getOrder(scv) == Abilities.HARVEST_GATHER &&
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
//        if ((LocationConstants.opponentRace == Race.ZERG && GameCache.ccList.size() < 3) ||
//                (LocationConstants.opponentRace == Race.PROTOSS && GameCache.ccList.size() < 2) ||
//                (LocationConstants.opponentRace == Race.TERRAN && GameCache.ccList.size() < 2)) {
//            return;
//        }
        if (Time.nowFrames() < Time.toFrames("5:00") && UnitUtils.getNumFriendlyUnits(Units.TERRAN_FACTORY, false) == 0) {
            return;
        }

        //loop through bases
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete(0.60f)) {
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() == null && gas.getGeyser().getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY) {
                        if (StructureScv.scvBuildingList.stream()
                                .noneMatch(scv -> scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getPosition()) < 1)) {
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
                Unit cc = base.getCc().unit();

                //get available scvs at this base
                List<UnitInPool> baseMineralScvs = getAvailableMineralScvs(base.getCcPos(), 10);

                //saturate refineries
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() != null) {
                        Unit refinery = gas.getRefinery();
                        for (int i = refinery.getAssignedHarvesters().get(); i < Math.min(refinery.getIdealHarvesters().get(), scvsPerGas) && !baseMineralScvs.isEmpty(); i++) {
                            UnitInPool closestUnit = UnitUtils.getClosestUnit(baseMineralScvs, refinery);
                            Bot.ACTION.unitCommand(closestUnit.unit(), Abilities.SMART, refinery, false);
                            baseMineralScvs.remove(closestUnit);
                            base.scvsAddedThisFrame--;
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
                int numExtraScvs = base.numScvsNeeded() * -1;
                if (numExtraScvs > 0) {
                    List<UnitInPool> extraScvsList = baseMineralScvs.subList(0, Math.min(baseMineralScvs.size(), numExtraScvs));
                    scvsToMove.addAll(UnitUtils.toUnitList(extraScvsList));
                }
            }
        }

        //add all idle workers to top of same list
        if (Bot.OBS.getIdleWorkerCount() > 0) {
            List<Unit> idleScvs = UnitUtils.toUnitList(
                    Bot.OBS.getUnits(Alliance.SELF, scv ->
                            scv.unit().getType() == Units.TERRAN_SCV &&
                                    scv.unit().getOrders().isEmpty() &&
                                    !Ignored.contains(scv.getTag())));
            scvsToMove.addAll(0, idleScvs);
        }

        //send extra scvs to closest undersaturated base
        for (Unit scv : scvsToMove) {
            Base closestBase = GameCache.baseList.stream()
                    .filter(base -> base.isMyBase() && base.numScvsNeeded() > 0)
                    .min(Comparator.comparing(base -> UnitUtils.getDistance(scv, base.getRallyNode())))
                    .orElse(null);
            if (closestBase == null) { //all minerals saturated at all bases
                break;
            }
            closestBase.addMineralScv(scv);
        }

        //TODO: cap gas income since minerals are saturated?? (will this cause too much scv travelling in subsequent frames?)

        //put any leftover idle scvs to work
        scvsToMove.removeIf(scv -> !scv.getOrders().isEmpty());
        if (!scvsToMove.isEmpty()) {
            //if no minerals left on map, join the attack as auto-repairers
            if (GameCache.defaultRallyNode == null) {
                scvsToMove.stream()
                        .filter(scv -> !scv.getBuffs().contains(Buffs.AUTOMATED_REPAIR))
                        .forEach(scv -> Bot.ACTION.toggleAutocast(scv.getTag(), Abilities.EFFECT_REPAIR_SCV));
                //UnitUtils.queueUpAttackOfEveryBase(scvsToMove);
                Bot.ACTION.unitCommand(scvsToMove, Abilities.ATTACK, ArmyManager.groundAttackersMidPoint, false);
            }
            //mine from newest base (or if dried up, distance-mine)
            else {
                Bot.ACTION.unitCommand(scvsToMove, Abilities.SMART, GameCache.defaultRallyNode, false);
            }
        }
    }

    public static void toggleWorkersInGas() {
        //skip logic until there are at least 2 refineries
        int numRefineries = UnitUtils.getNumFriendlyUnits(UnitUtils.REFINERY_TYPE, false);
        if (numRefineries <= 1) {
            return;
        }

        //max gas during slow 3rd base build order
        if (Strategy.EXPAND_SLOWLY && Time.nowFrames() < Time.toFrames("5:00")) {
            scvsPerGas = 3;
            return;
        }

        int mins = GameCache.mineralBank;
        int gas = GameCache.gasBank;
        if (scvsPerGas == 1) {
            if (gasBankRatio() < 0.6) {
                scvsPerGas = 2;
            }
        }
        else if (scvsPerGas == 2) {
            //if late game with bank, or if >3:1 mins:gas, then max gas income
            if (Time.nowFrames() > Time.toFrames("3:00") && (mins > 3100 || (mins > 300 && gasBankRatio() < 0.3))) {
                scvsPerGas = 3;
            }
            //go to 1 in gas
            else if (gas > 700 && gasBankRatio() > 0.75) {
                scvsPerGas = 1;
            }
        }
        else if (scvsPerGas == 3) {
            if (mins < 2750 && gas > 80*GameCache.starportList.size() && gasBankRatio() > 0.5) {
                scvsPerGas = 2;
            }
        };
    }

    private static float gasBankRatio() {
        return Math.max(GameCache.gasBank, 1f) / (Math.max(GameCache.gasBank, 1f) + Math.max(GameCache.mineralBank, 1f));
    }

    private static List<Unit> getDeepestMineralScvs(int numScvs) {
        List<UnitInPool> scvs = new ArrayList<>();
        int scvsNeeded = numScvs;
        for (Base base : GameCache.baseList) {
            if (base.isMyBase()) {
                List<UnitInPool> baseScvs = getAvailableScvs(base.getCcPos(), 10, true, true);
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

    //Up a new pf base to a minimum of 10 scvs
    public static void sendScvsToNewPf(Unit pf) {
        Point2d pfPos = pf.getPosition().toPoint2d();

        //transfer a lot to nat PF for early rushes
        if (pfPos.distance(LocationConstants.baseLocations.get(1)) < 1 && Base.numMyBases() == 2) {
            List<UnitInPool> mainBaseScvs = WorkerManager.getAllScvs(LocationConstants.baseLocations.get(0), 10);
            if (mainBaseScvs.size() > 15) {
                mainBaseScvs = mainBaseScvs.subList(0, 15);
            }
            mainBaseScvs.forEach(scv -> {
                    if (UnitUtils.isCarryingResources(scv.unit())) {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.HARVEST_RETURN, false)
                                .unitCommand(scv.unit(), Abilities.SMART, GameCache.baseList.get(1).getMineralPatches().get(0), true);
                    }
                    else {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.SMART, GameCache.baseList.get(1).getMineralPatches().get(0), false);
                    }
            });
            return;
        }

        //normal transfer of 6 scvs for other bases
        int scvsNeeded = 8 - UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, pfPos, 10).size();
        if (scvsNeeded <= 0) {
            return;
        }
        Unit targetNode = getMiningNodeAtBase(pfPos);
        if (targetNode == null) {
            return;
        }

        List<Unit> scvs = getDeepestMineralScvs(scvsNeeded);
        if (!scvs.isEmpty()) {
            Bot.ACTION.unitCommand(scvs, Abilities.SMART, targetNode, false);
        }
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
