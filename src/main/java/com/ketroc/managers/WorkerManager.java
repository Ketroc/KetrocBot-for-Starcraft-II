package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.micro.ScvAttackTarget;
import com.ketroc.micro.TankToPosition;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.models.*;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.CannonRushDefense;
import com.ketroc.utils.*;

import java.util.*;
import java.util.stream.Collectors;


public class WorkerManager {
    public static int numScvsPerGas = 3;

    public static void onStep() {
        repairLogic();
//        if (Time.nowFrames()/Strategy.STEP_SIZE % 2 == 0) {
            fixOverSaturation();
//        }
//        else {
//            putIdleScvsToWork();
//        }
        toggleWorkersInGas();
        buildRefineryLogic();
        defendWorkerHarass(); //TODO: this method break scvrush micro
        preventMulesFromDyingWithMineralsInHand();
    }

    private static void putIdleScvsToWork() {
        if (Bot.OBS.getIdleWorkerCount() == 0) {
            return;
        }
        List<UnitInPool> idleScvs = UnitUtils.getIdleScvs();
        idleScvs.removeIf(scv -> Base.isMining(scv));

        idleScvs.removeIf(scv -> Base.assignScvToAMineralPatch(scv));
        if (!idleScvs.isEmpty()) {
            Chat.chatWithoutSpam("Using extra scvs to attack", 120);
            idleScvs.stream()
                    .forEach(scv -> Bot.ACTION.toggleAutocast(scv.getTag(), Abilities.EFFECT_REPAIR_SCV));
            ActionHelper.giveScvCommand(UnitUtils.toUnitList(idleScvs), Abilities.ATTACK, ArmyManager.groundAttackersMidPoint, false);
        }
    }

    //any mule in one of my bases that can't complete another mining round, will a-move + autorepair instead
    private static void preventMulesFromDyingWithMineralsInHand() {
        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MULE).stream()
                .filter(mule -> UnitUtils.getOrder(mule) == Abilities.HARVEST_GATHER &&
                        mule.getBuffDurationRemain().orElse(0) < 144 &&
                        UnitUtils.getDistance(mule,
                                UnitUtils.getClosestUnitOfType(Alliance.SELF, UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN,
                                        mule.getPosition().toPoint2d())) < 3)
                .forEach(mule -> {
                    ActionHelper.unitCommand(mule, Abilities.ATTACK, ArmyManager.attackGroundPos, false);
                    Bot.ACTION.toggleAutocast(mule.getTag(), Abilities.EFFECT_REPAIR_MULE);
                });
    }

    private static void defendWorkerHarass() {
        //only for first 3min
        if (Strategy.WALL_OFF_IMMEDIATELY || Time.nowFrames() > Time.toFrames("3:00") || CannonRushDefense.cannonRushStep != 0) {
            return;
        }

        //target scout workers
        UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType).forEach(enemyWorker -> {
            if (UnitUtils.isInMyMainOrNat(enemyWorker)) {
                ScvAttackTarget.add(enemyWorker);
            }
        });
    }

    private static void repairLogic() {
        if (Bot.OBS.getMinerals() < 15) {
            return;
        }

        Set<Unit> unitsToRepair = getSetOfUnitsToRepair();

        //send appropriate amount of scvs to each unit
        for (Unit unit : unitsToRepair) {
            int numScvsToAdd = UnitUtils.numIdealScvsToRepair(unit) - UnitUtils.numRepairingScvs(unit);
            if (numScvsToAdd <= 0) { //skip if no additional scvs required
                continue;
            }
            List<Unit> scvsForRepair = getScvsForRepairing(unit, numScvsToAdd);
            if (!scvsForRepair.isEmpty()) {
                Print.print("sending " + scvsForRepair.size() + " scvs to repair: " + unit.getType() + " at: " + unit.getPosition().toPoint2d());
                scvsForRepair.forEach(scv -> Base.releaseScv(scv));
                //line up scvs behind PF before giving repair command
                if (unit.getType() == Units.TERRAN_PLANETARY_FORTRESS || UnitUtils.getOrder(unit) == Abilities.MORPH_PLANETARY_FORTRESS) {
                    Base pfBase = Base.getBase(unit);
                    if (pfBase == null) { //ignore offensive PFs
                        continue;
                    }
                    Point2d behindPFPos = Position.towards(pfBase.getCcPos(), pfBase.getResourceMidPoint(), 5.4f);
                    for (Unit scv : scvsForRepair) {
                        if (pfBase != null && scvNotBehindPF(scv, pfBase)) {
                            ActionHelper.giveScvCommand(scv, Abilities.MOVE, behindPFPos, false);
                            ActionHelper.giveScvCommand(scv, Abilities.EFFECT_REPAIR_SCV, unit, true);
                        }
                        else {
                            ActionHelper.giveScvCommand(scv, Abilities.EFFECT_REPAIR_SCV, unit, false);
                        }
                    }
                }
                else {
                    ActionHelper.giveScvCommand(scvsForRepair, Abilities.EFFECT_REPAIR_SCV, unit, false);
                }
            }
        }
    }

    private static Set<Unit> getSetOfUnitsToRepair() {
        Set<Unit> unitsToRepair = new HashSet<>();

        //add PFs
        unitsToRepair.addAll(
            GameCache.baseList.stream()
                    .filter(base -> base.isMyBase() &&
                            (base.getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS ||
                                    (UnitUtils.getOrder(base.getCc().unit()) == Abilities.MORPH_PLANETARY_FORTRESS &&
                                            Time.nowFrames() - base.lastMorphFrame > 600))) //complete PFs or 10sec from morphed
                    .map(base -> base.getCc().unit())
                    .collect(Collectors.toSet()));

        //add missile turrets
        unitsToRepair.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MISSILE_TURRET));

        //add liberators if TvZ/TvT
        if (LocationConstants.opponentRace != Race.PROTOSS) { //libs on top of PF vs toss so unreachable by scvs to repair
            unitsToRepair.addAll(GameCache.liberatorList);
        }

        //add defensive tanks
        unitsToRepair.addAll(
                UnitMicroList.getUnitSubList(TankToPosition.class).stream()
                .map(tank -> tank.unit.unit())
                .collect(Collectors.toSet()));

        //add wall structures
        if (InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, LocationConstants.insideMainWall) < 2) {
            unitsToRepair.addAll(GameCache.wallStructures);
        }

        //add burning structures
        unitsToRepair.addAll(
                GameCache.burningStructures.stream()
                    .filter(structure -> InfluenceMaps.getGroundThreatToStructure(structure) == 0)
                    .collect(Collectors.toSet()));

        //add bunker at natural
        Unit natBunker = UnitUtils.getCompletedNatBunker();
        if (natBunker != null) {
            unitsToRepair.add(natBunker);
        }
        return unitsToRepair;
    }

    private static boolean scvNotBehindPF(Unit unit, Base pfBase) {
        return UnitUtils.getDistance(unit, pfBase.getCcPos()) + 1.5 < UnitUtils.getDistance(unit, pfBase.getResourceMidPoint());
    }

    private static List<Unit> getScvsForRepairing(Unit unitToRepair, int numScvsToAdd) {
        List<Unit> availableScvs;
        //only choose scvs inside the wall within 20 distance
        if (GameCache.wallStructures.contains(unitToRepair) && !isRangedEnemyNearby()) {
            availableScvs = UnitUtils.toUnitList(Bot.OBS.getUnits(Alliance.SELF, u ->
                    u.unit().getType() == Units.TERRAN_SCV &&
                            Math.abs(u.unit().getPosition().getZ() - unitToRepair.getPosition().getZ()) < 1 && //same elevation as wall
                            UnitUtils.getDistance(u.unit(), unitToRepair) < 30 &&
                            (ActionIssued.getCurOrder(u.unit()).isEmpty() || isMiningMinerals(u))));
        }
//                        if (GameState.burningStructures.contains(unit) || GameState.wallStructures.contains(unit)) {
//                            //only send if safe
//                            //TODO: make threat to ground gridmap to check against (replace above if statement for wall structures)
//                        }
        else if (unitToRepair.getType() == Units.TERRAN_PLANETARY_FORTRESS) {
            availableScvs = UnitUtils.toUnitList(getAvailableScvs(unitToRepair.getPosition().toPoint2d(), 10));
        }
        else {
            availableScvs = UnitUtils.toUnitList(getAvailableScvs(unitToRepair.getPosition().toPoint2d()));
        }

        //sort by closest scvs then sublist
        availableScvs = availableScvs.stream()
                .sorted(Comparator.comparing(scv -> UnitUtils.getDistance(scv, unitToRepair)))
                .limit(Math.max(0, Math.min(availableScvs.size()-1, numScvsToAdd)))
                .collect(Collectors.toList());
        return availableScvs;
    }

    private static boolean isRangedEnemyNearby() {
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, LocationConstants.insideMainWall) > 0;
    }

    private static void buildRefineryLogic() {
        //don't build new refineries yet
//        if ((LocationConstants.opponentRace == Race.ZERG && GameCache.ccList.size() < 3) ||
//                (LocationConstants.opponentRace == Race.PROTOSS && GameCache.ccList.size() < 2) ||
//                (LocationConstants.opponentRace == Race.TERRAN && GameCache.ccList.size() < 2)) {
//            return;
//        }
        //don't make 3rd+ refinery until factory and PF are started
        if (Time.nowFrames() < Time.toFrames("5:00") &&
                (UnitUtils.getNumFriendlyUnits(Units.TERRAN_FACTORY, true) == 0 || !pfAtNatural())) {
            return;
        }

        //loop through bases
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete(0.60f)) {
                for (Gas gas : base.getGases()) {
                    if (gas.getRefinery() == null && gas.getNode().getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY) {
                        if (StructureScv.scvBuildingList.stream()
                                .noneMatch(scv -> scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getNodePos()) < 1)) {
                            if (!Purchase.isStructureQueued(Units.TERRAN_REFINERY)) {
                                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean pfAtNatural() {
        Base natBase = GameCache.baseList.get(1);
        return natBase.isMyBase() &&
                (natBase.getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS ||
                        UnitUtils.getOrder(natBase.getCc().unit()) == Abilities.MORPH_PLANETARY_FORTRESS ||
                        Purchase.isMorphQueued(Abilities.MORPH_PLANETARY_FORTRESS));
    }

    public static List<Unit> getAvailableScvUnits(Point2d targetPosition) {
        return UnitUtils.toUnitList(getAvailableScvs(targetPosition, 9));
    }

    public static List<Unit> getAllScvUnits(Point2d targetPosition) {
        return UnitUtils.toUnitList(getAllScvs(targetPosition, 9));
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
                    (ActionIssued.getCurOrder(scv.unit()).isEmpty() || isMiningMinerals(scv) || (includeGasScvs && isMiningGas(scv))) &&
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

    private static boolean isMiningNode(UnitInPool scv, Set<Units> nodeType) {
        if (ActionIssued.getCurOrder(scv.unit()).isEmpty() || ActionIssued.getCurOrder(scv.unit()).get().ability != Abilities.HARVEST_GATHER) {
            return false;
        }

        Tag scvTargetTag = ActionIssued.getCurOrder(scv.unit()).get().targetTag;
        if (scvTargetTag == null) { //return false if scv has no target
            return false;
        }

        UnitInPool targetNode = Bot.OBS.getUnit(scvTargetTag);
        return targetNode != null && nodeType.contains(targetNode.unit().getType());
    }



    private static void fixOverSaturation() {
        // saturate gases
        saturateGases();

        // get all idle scvs
        List<UnitInPool> idleScvs = UnitUtils.getIdleScvs();

        // saturate minerals
        while (!idleScvs.isEmpty()) {
            MineralPatch closestUnderSaturatedMineral = Base.getClosestUnderSaturatedMineral(idleScvs.get(0).unit().getPosition().toPoint2d());
            if (closestUnderSaturatedMineral == null) {
                break;
            }
            closestUnderSaturatedMineral.getScvs().add(idleScvs.remove(0));
        }

        // oversaturate gases (go up to 3 on gas even when only 0-2 are needed)
        while (!idleScvs.isEmpty()) {
            Gas closestUnmaxedGas = Base.getClosestUnmaxedGas(idleScvs.get(0).unit().getPosition().toPoint2d());
            if (closestUnmaxedGas == null) {
                break;
            }
            closestUnmaxedGas.getScvs().add(idleScvs.remove(0));
        }

        // distance mine
        if (!idleScvs.isEmpty()) {
            idleScvs.forEach(scv -> Base.distanceMineScv(scv));
        }

        //free up distance miners and oversaturated gas miners
        freeUpExtraScvs();

    }

    private static void freeUpExtraScvs() {
        int numGasScvsNeeded = Base.numGasScvsFromMaxSaturation();
        int numMineralScvsNeeded = Base.numMineralScvsFromMaxSaturation();
        int totalScvsNeeded = numGasScvsNeeded + numGasScvsNeeded;
        if (totalScvsNeeded == 0) {
            return;
        }

        //take from distance miners to cover required mineral and gas scvs
        List<MineralPatch> distanceMinedMineralPatches = Base.getDistanceMinedMineralPatches();
        for (int i = distanceMinedMineralPatches.size() - 1; i > 0; i--) {
            MineralPatch minPatch = distanceMinedMineralPatches.get(i);
            while (!minPatch.getScvs().isEmpty()) {
                ActionHelper.unitCommand(minPatch.getScvs().remove(0).unit(), Abilities.STOP, false);
                totalScvsNeeded--;
                numMineralScvsNeeded--;
                if (totalScvsNeeded == 0) {
                    return;
                }
            }
        }

        if (numMineralScvsNeeded <= 0) {
            return;
        }

        //take from gas miners to cover required mineral scvs
        for (Gas gas : Base.getMyGases()) {
            if (gas.getRefinery().getType() != Units.TERRAN_REFINERY_RICH && gas.getScvs().size() > numScvsPerGas) {
                UnitInPool gasScv = gas.getScvs().stream()
                        .filter(scv -> scv.getLastSeenGameLoop() == Time.nowFrames()) //not in geyser
                        .filter(scv -> !UnitUtils.isCarryingResources(scv.unit()))
                        .filter(scv -> UnitUtils.getDistance(scv.unit(), gas.getNodePos()) > 3)
                        .findAny()
                        .orElse(null);
                if (gasScv != null) {
                    ActionHelper.unitCommand(gasScv.unit(), Abilities.STOP, false);
                    gas.getScvs().remove(gasScv);
                    numMineralScvsNeeded--;
                    if (numGasScvsNeeded == 0) {
                        break;
                    }
                }
            }
        }
    }

    private static void saturateGases() {
        List<Gas> myGases = Base.getMyGases();
        for (Gas gas : myGases) {
            int numScvs = (gas.getRefinery().getType() == Units.TERRAN_REFINERY_RICH) ? 3 : numScvsPerGas;
            while (gas.getScvs().size() < numScvs) {
                UnitInPool closestMineralScv = Base.releaseClosestMineralScv(gas.getByNode());
                if (closestMineralScv == null) {
                    return;
                }
                gas.getScvs().add(closestMineralScv);
            }
        }
    }

    private static boolean mainBaseUnderAttack() {
//        int totalEnemyCostInMain = GameCache.allEnemiesList.stream()
//                .filter(enemy -> UnitUtils.canAttackGround(enemy.unit()) &&
//                        !UnitUtils.WORKER_TYPE.contains(enemy.unit().getType()) &&
//                        InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, enemy.unit().getPosition().toPoint2d()))
//                .mapToInt(enemy -> {
//                    Cost enemyCost = UnitUtils.getCost(enemy.unit());
//                    return enemyCost.minerals + enemyCost.gas;
//                })
//                .sum();

        return true;
    }

    public static void toggleWorkersInGas() {
        //skip logic until there are at least 2 refineries
        int numRefineries = UnitUtils.getNumFriendlyUnits(UnitUtils.REFINERY_TYPE, false);
        if (numRefineries <= 1) {
            return;
        }

        //max gas during slow 3rd base build order
        if (Strategy.EXPAND_SLOWLY && Time.nowFrames() < Time.toFrames("5:00")) {
            numScvsPerGas = 3;
            return;
        }

        int mins = GameCache.mineralBank;
        int gas = GameCache.gasBank;
        if (numScvsPerGas == 1) {
            if (gasBankRatio() < 0.6) {
                numScvsPerGas = 2;
            }
        }
        else if (numScvsPerGas == 2) {
            //if late game with bank, or if >3:1 mins:gas, then max gas income
            if (mins > 3100 || (mins > 300 && gasBankRatio() < 0.3)) {
                numScvsPerGas = 3;
            }
            //go to 1 in gas
            else if (gas > 700 && gasBankRatio() > 0.75) {
                numScvsPerGas = 1;
            }
        }
        else if (numScvsPerGas == 3) {
            if (mins < 2750 && gas > 80*GameCache.starportList.size() && gasBankRatio() > 0.5) {
                numScvsPerGas = 2;
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
                List<UnitInPool> baseScvs = getAvailableScvs(base.getCcPos(), 9, true, true);
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

    //Up a new pf base to a minimum of 10 scvs (12 for nat)
    public static void sendScvsToNewPf(Unit pf) { //TODO: fix this to create MineralPatch scvs
        Point2d pfPos = pf.getPosition().toPoint2d();

        //transfer a lot to nat PF for early rushes
        if (pfPos.distance(LocationConstants.baseLocations.get(1)) < 1 && Base.numMyBases() == 2) {
            List<UnitInPool> mainBaseScvs = WorkerManager.getAllScvs(LocationConstants.baseLocations.get(0), 9);
            if (mainBaseScvs.size() > 11) {
                mainBaseScvs = mainBaseScvs.subList(0, 11);
            }
            mainBaseScvs.forEach(scv -> {
                    if (UnitUtils.isCarryingResources(scv.unit())) {
                        ActionHelper.unitCommand(scv.unit(), Abilities.HARVEST_RETURN, false);
                        ActionHelper.unitCommand(scv.unit(), Abilities.SMART, GameCache.baseList.get(1).getMineralPatchUnits().get(0), true);
                    }
                    else {
                        ActionHelper.unitCommand(scv.unit(), Abilities.SMART, GameCache.baseList.get(1).getMineralPatchUnits().get(0), false);
                    }
            });
            return;
        }

        //normal transfer of 6 scvs for other bases
        int scvsNeeded = 8 - UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, pfPos, 9).size();
        if (scvsNeeded <= 0) {
            return;
        }
        Unit targetNode = getMiningNodeAtBase(pfPos);
        if (targetNode == null) {
            return;
        }

        List<Unit> scvs = getDeepestMineralScvs(scvsNeeded);
        if (!scvs.isEmpty()) {
            ActionHelper.unitCommand(scvs, Abilities.SMART, targetNode, false);
        }
    }

    private static Unit getMiningNodeAtBase(Point2d basePos) {
        List<UnitInPool> mineralPatches = UnitUtils.getUnitsNearbyOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_NODE_TYPE, basePos, 9);
        if (!mineralPatches.isEmpty()) {
            return mineralPatches.get(0).unit();
        }
        else {
            List<UnitInPool> refineries = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.REFINERY_TYPE, basePos, 9);
            if (!refineries.isEmpty()) {
                return refineries.get(0).unit();
            }
        }
        return null;
    }
}
