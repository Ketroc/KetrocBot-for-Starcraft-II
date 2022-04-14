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
import com.ketroc.geometry.Position;
import com.ketroc.micro.ScvAttackTarget;
import com.ketroc.micro.TankToPosition;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.models.Base;
import com.ketroc.models.Gas;
import com.ketroc.models.MineralPatch;
import com.ketroc.models.StructureScv;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.CannonRushDefense;
import com.ketroc.strategies.defenses.WorkerRushDefense;
import com.ketroc.strategies.defenses.WorkerRushDefense3;
import com.ketroc.utils.*;
import io.vertx.codegen.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class WorkerManager {
    public static int numScvsPerGas = 3;
    public static List<UnitInPool> idleScvs;

    public static void onStepStart() {
        repairLogic();
        fixOverSaturation();
        setNumScvsPerGas();
        buildRefineryLogic();
        //defendWorkerHarass();
        preventMulesFromDyingWithMineralsInHand();
    }

//    private static void putIdleScvsToWork() {
//        if (Bot.OBS.getIdleWorkerCount() == 0) {
//            return;
//        }
//        List<UnitInPool> idleScvs = UnitUtils.getIdleScvs();
//        idleScvs.removeIf(Base::isMining);
//
//        idleScvs.removeIf(Base::assignScvToAMineralPatch);
//        if (!idleScvs.isEmpty()) {
//            Chat.chatWithoutSpam("Using extra scvs to attack", 120);
//            idleScvs.forEach(scv -> Bot.ACTION.toggleAutocast(scv.getTag(), Abilities.EFFECT_REPAIR_SCV));
//            ActionHelper.giveScvCommand(UnitUtils.toUnitList(idleScvs), Abilities.ATTACK, ArmyManager.groundAttackersMidPoint, false);
//        }
//    }

    //any mule in one of my bases that can't complete another mining round, will a-move + autorepair instead
    private static void preventMulesFromDyingWithMineralsInHand() {
        UnitUtils.myUnitsOfType(Units.TERRAN_MULE).stream()
                .filter(mule -> UnitUtils.getOrder(mule) == Abilities.HARVEST_GATHER &&
                        mule.getBuffDurationRemain().orElse(0) < 144 &&
                        UnitUtils.getDistance(mule,
                                UnitUtils.getClosestUnitOfType(Alliance.SELF, UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN,
                                        mule.getPosition().toPoint2d())) < 3) //FIXME: throws exception when OC dies but mule is alive (not a big deal??)
                .forEach(mule -> {
                    ActionHelper.unitCommand(mule, Abilities.ATTACK, ArmyManager.attackGroundPos, false);
                    Bot.ACTION.toggleAutocast(mule.getTag(), Abilities.EFFECT_REPAIR_MULE);
                });
    }

    private static void defendWorkerHarass() {
        if (Strategy.WALL_OFF_IMMEDIATELY ||
                CannonRushDefense.cannonRushStep != 0 ||
                WorkerRushDefense.defenseStep > 0 ||
                WorkerRushDefense3.isWorkerRushed) {
            return;
        }

        //cancel all SCV chasers if it's an attack
        if (UnitUtils.getVisibleEnemySupplyInMyMainorNat() > 2 ||
                GameCache.allVisibleEnemiesList.stream().anyMatch(enemy ->
                        !UnitUtils.WORKER_TYPE.contains(enemy.unit().getType()) &&
                        UnitUtils.canAttack(enemy.unit()))) {
            UnitMicroList.getUnitSubList(ScvAttackTarget.class).forEach(ScvAttackTarget::remove);
        }
        else {
            //target scout workers
            UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType).stream()
                    .filter(UnitUtils::isInMyMainOrNat)
                    .forEach(ScvAttackTarget::add);
        }
    }

    private static void repairLogic() {
        if (Bot.OBS.getMinerals() < 15 &&
                Bot.OBS.getScore().getDetails().getCollectionRateMinerals() < 250) {
            return;
        }

        Set<Unit> unitsToRepair = getSetOfUnitsToRepair();

        //send appropriate amount of scvs to each unit
        for (Unit unitToRepair : unitsToRepair) {
            int numScvsToAdd = UnitUtils.numIdealScvsToRepair(unitToRepair) - UnitUtils.numRepairingScvs(unitToRepair);
            if (numScvsToAdd <= 0) { //skip if no additional scvs required
                continue;
            }

            //line up scvs behind PF before giving repair command
            if (unitToRepair.getType() == Units.TERRAN_PLANETARY_FORTRESS || UnitUtils.getOrder(unitToRepair) == Abilities.MORPH_PLANETARY_FORTRESS) {
                Base pfBase = Base.getBase(unitToRepair);
                if (pfBase == null) { //ignore offensive PFs
                    continue;
                }
                Point2d behindPFPos = Position.towards(pfBase.getCcPos(), pfBase.getResourceMidPoint(), 5.4f);
                for (int i=0; i<numScvsToAdd; i++) {
                    UnitInPool repairScv = WorkerManager.getScv(
                            unitToRepair.getPosition().toPoint2d(),
                            scv -> UnitUtils.getDistance(unitToRepair, scv.unit()) < 9
                    );
                    if (repairScv == null) {
                        break;
                    }
                    if (pfBase != null && scvNotBehindPF(repairScv.unit(), pfBase)) {
                        ActionHelper.unitCommand(repairScv.unit(), Abilities.MOVE, behindPFPos, false);
                        ActionHelper.unitCommand(repairScv.unit(), Abilities.EFFECT_REPAIR_SCV, unitToRepair, true);
                    } else {
                        ActionHelper.unitCommand(repairScv.unit(), Abilities.EFFECT_REPAIR_SCV, unitToRepair, false);
                    }
                }
            } else {
                for (int i=0; i<numScvsToAdd; i++) {
                    UnitInPool repairScv;
                    if (GameCache.wallStructures.contains(unitToRepair)) {
                        repairScv = WorkerManager.getScv(
                                unitToRepair.getPosition().toPoint2d(),
                                scv -> UnitUtils.isInMyMain(scv.unit())
                        );
                    } else {
                        repairScv = WorkerManager.getScvEmptyHands(unitToRepair.getPosition().toPoint2d());
                    }
                    if (repairScv == null) {
                        return;
                    }
                    ActionHelper.unitCommand(repairScv.unit(), Abilities.EFFECT_REPAIR_SCV, unitToRepair, false);
                }
            }
        }
    }

    private static Set<Unit> getSetOfUnitsToRepair() {
        Set<Unit> unitsToRepair = new HashSet<>();

        //add base PFs
        unitsToRepair.addAll(
            GameCache.baseList.stream()
                    .filter(base -> base.isMyBase() &&
                            (base.getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS ||
                                    (UnitUtils.getOrder(base.getCc().unit()) == Abilities.MORPH_PLANETARY_FORTRESS &&
                                            Time.nowFrames() - base.lastMorphFrame > 600))) //complete PFs or 10sec from morphed
                    .map(base -> base.getCc().unit())
                    .collect(Collectors.toSet()));

        //add missile turrets
        unitsToRepair.addAll(UnitUtils.myUnitsOfType(Units.TERRAN_MISSILE_TURRET));

        //add liberators if TvZ/TvT
        if (PosConstants.opponentRace != Race.PROTOSS) { //libs on top of PF vs toss so unreachable by scvs to repair
            unitsToRepair.addAll(GameCache.liberatorList);
        }

        //add defensive tanks
        unitsToRepair.addAll(
                UnitMicroList.getUnitSubList(TankToPosition.class).stream()
                .map(tank -> tank.unit.unit())
                .collect(Collectors.toSet()));

        //add wall structures
        if (InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, PosConstants.insideMainWall) < 2) {
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

    private static boolean isRangedEnemyNearby() {
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, PosConstants.insideMainWall) > 0;
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
                (UnitUtils.numMyUnits(Units.TERRAN_FACTORY, true) == 0 || !pfAtNatural())) {
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

    @Nullable
    public static UnitInPool getScvEmptyHands(Point2d targetPos) {
        return getScvEmptyHands(targetPos, scv -> true);
    }

    @Nullable
    public static UnitInPool getScvEmptyHands(Point2d targetPos, Predicate<UnitInPool> scvFilter) {
        UnitInPool resultScv = getScv(targetPos, scvFilter.and(scv -> !UnitUtils.isCarryingResources(scv.unit())));
        return resultScv != null ? resultScv : getScv(targetPos, scvFilter);
    }

    @Nullable
    public static UnitInPool getScv(Point2d targetPos) {
        return getScv(targetPos, scv -> true);
    }

    @Nullable
    public static UnitInPool getScv(Point2d targetPos, Predicate<UnitInPool> scvFilter) {
        //find closest idle scv
        UnitInPool scv = idleScvs.stream()
                .filter(scvFilter)
                .min(Comparator.comparing(idleScv -> UnitUtils.getDistance(idleScv.unit(), targetPos)))
                .orElse(null);
        if (scv != null) {
            Base.releaseScv(scv.unit());
            return scv;
        }

        //find closest distance-mining scv
        scv = GameCache.baseList.stream()
                .filter(base -> base.hasDistanceMiningScv(scvFilter))
                .min(Comparator.comparing(base -> base.getCcPos().distance(targetPos)))
                .flatMap(base -> base.getDistanceMiningScv(targetPos))
                .orElse(null);
        if (scv != null) {
            Base.releaseScv(scv.unit());
            return scv;
        }

        //find closest mineral-oversaturated scv
        scv = GameCache.baseList.stream()
                .filter(base -> base.hasOverSaturatedMineral(scvFilter))
                .min(Comparator.comparing(base -> base.getCcPos().distance(targetPos)))
                .flatMap(base -> base.getScvFromOversaturatedMineral(scvFilter))
                .orElse(null);
        if (scv != null) {
            Base.releaseScv(scv.unit());
            return scv;
        }


        // - turned off to prevent issues with scvs entering gas or gas-in-hand scvs from producing
        // - oversaturated gas scvs will still get freed up if needed for mining elsewhere
        // - FIXME: should we also turn off distance-mining scvs and min-oversaturated scvs for the same reason?
        //find closest gas-oversaturated scv
        scv = GameCache.baseList.stream()
                .filter(base -> base.hasOverSaturatedGas(scvFilter))
                .min(Comparator.comparing(base -> base.getCcPos().distance(targetPos)))
                .flatMap(base -> base.getScvFromOversaturatedGas(scvFilter))
                .orElse(null);
        if (scv != null) {
            Base.releaseScv(scv.unit());
            return scv;
        }

        //find closest mineral-mining scv
        scv = GameCache.baseList.stream()
                .filter(base -> base.numMineralScvs(scvFilter) > 0)
                .min(Comparator.comparing(base -> base.getCcPos().distance(targetPos)))
                .flatMap(base -> base.getScvFromSmallestPatch(scvFilter))
                .orElse(null);
        if (scv != null) {
            Base.releaseScv(scv.unit());
            return scv;
        }

        //find closest gas-mining scv
        scv = GameCache.baseList.stream()
                .filter(base -> base.numAvailableGasScvs(scvFilter) > 0)
                .min(Comparator.comparing(base -> base.getCcPos().distance(targetPos)))
                .flatMap(base -> base.getScvFromGas(scvFilter))
                .orElse(null);
        if (scv != null) {
            Base.releaseScv(scv.unit());
            return scv;
        }

        return null;
    }

    public static boolean isMiningMinerals(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.MINERAL_NODE_TYPE);
    }

    public static boolean isMiningGas(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.REFINERY_TYPE);
    }

    private static boolean isMiningNode(UnitInPool scv, Set<Units> nodeType) {
        if (ActionIssued.getCurOrder(scv).isEmpty() || ActionIssued.getCurOrder(scv.unit()).get().ability != Abilities.HARVEST_GATHER) {
            return false;
        }

        Tag scvTargetTag = ActionIssued.getCurOrder(scv).get().targetTag;
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
        idleScvs = UnitUtils.getIdleScvs();

        sendScvsToMine(idleScvs);

        //free up needed scvs from distance miners, oversatured mineral miners, and oversaturated gas miners
        freeUpExtraScvs();

    }

    public static void sendScvsToMine(UnitInPool idleScv) {
        sendScvsToMine(new ArrayList<>(List.of(idleScv)));
    }

    public static void sendScvsToMine(List<UnitInPool> idleScvs) {
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

        // oversaturate small minerals
        while (!idleScvs.isEmpty()) {
            MineralPatch closestSaturatedSmallMineral = Base.getClosestSaturatedSmallMineral(idleScvs.get(0).unit().getPosition().toPoint2d());
            if (closestSaturatedSmallMineral == null) {
                break;
            }
            closestSaturatedSmallMineral.getScvs().add(idleScvs.remove(0));
        }


        // distance mine
        if (!idleScvs.isEmpty()) {
            idleScvs.forEach(Base::distanceMineScv);
        }

        //TODO: distance mine to unsafe locations if numScvs > maxScvs (make them fearless??)
    }

    //make oversaturation scvs available to handle normal saturation everywhere
    private static void freeUpExtraScvs() {
        int numGasScvsNeeded = Base.numGasScvsFromDesiredSaturation();
        int numMineralScvsNeeded = Base.numMineralScvsFromSoftSaturation();
        int totalScvsNeeded = numMineralScvsNeeded + numGasScvsNeeded;
        if (totalScvsNeeded == 0) {
            return;
        }

        //take from distance miners to cover required mineral and gas scvs
        List<MineralPatch> distanceMinedMineralPatches = Base.getDistanceMinedMineralPatches();
        for (int i = distanceMinedMineralPatches.size() - 1; i > 0; i--) {
            MineralPatch minPatch = distanceMinedMineralPatches.get(i);
            while (!minPatch.getScvs().isEmpty()) {
                UnitUtils.returnAndStopScv(minPatch.getAndReleaseScv());
                totalScvsNeeded--;
                numMineralScvsNeeded--;
                if (totalScvsNeeded == 0) {
                    return;
                }
            }
        }

        //take from oversaturated minerals to cover required mineral and gas scvs
        List<MineralPatch> oversaturatedMineralPatches = Base.getOversaturatedMineralPatches();
        for (MineralPatch minPatch : oversaturatedMineralPatches) {
            while (minPatch.getScvs().size() > 2) {
                UnitUtils.returnAndStopScv(minPatch.getAndReleaseScv());
                totalScvsNeeded--;
                numMineralScvsNeeded--;
                if (totalScvsNeeded == 0) {
                    return;
                }
            }
        }

        //take from gas miners to cover required mineral scvs
        for (Gas gas : Base.getMyGases()) {
            if (gas.getRefinery().getType() != Units.TERRAN_REFINERY_RICH && gas.getScvs().size() > numScvsPerGas) {
                UnitInPool gasScv = gas.getAndReleaseScv();
                if (gasScv != null) {
                    UnitUtils.returnAndStopScv(gasScv);
                    numMineralScvsNeeded--;
                    if (numMineralScvsNeeded == 0) {
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
                UnitInPool newScv = getScv(gas.getByNodePos(), scv -> !Base.isMiningGas(scv));
                if (newScv == null) {
                    return;
                }
                gas.getScvs().add(newScv);
            }
        }
    }

    public static void setNumScvsPerGas() {
        //no gas income while defending worker rush
        if (WorkerRushDefense3.isWorkerRushed) {
            numScvsPerGas = 0;
            return;
        }

        //max gas with Tank_Viking and BunkerContain TvT
        if ((Strategy.gamePlan == GamePlan.TANK_VIKING ||
                Strategy.gamePlan == GamePlan.MECH_ALL_IN ||
                BunkerContain.proxyBunkerLevel == 2) &&
                Time.nowFrames() < Time.toFrames("4:00")) {
            numScvsPerGas = 3;
            return;
        }

        //max gas during slow 3rd base build order
        if (Strategy.EXPAND_SLOWLY && Time.nowFrames() < Time.toFrames("5:00")) {
            numScvsPerGas = 3;
            return;
        }

//        //max gas during double OC opening
//        if (Strategy.NUM_BASES_TO_OC > 1 && Time.nowFrames() < Time.toFrames("3:00")) {
//            numScvsPerGas = 3;
//            return;
//        }

        int minerals = GameCache.mineralBank;
        int gas = GameCache.gasBank;
        float mineralRate = Bot.OBS.getScore().getDetails().getCollectionRateMinerals();
        float gasRate = Bot.OBS.getScore().getDetails().getCollectionRateVespene();

        switch (numScvsPerGas) {
            case 0:
                if (mineralRate > 0 && minerals > 50 && gasBankRatio() < 0.4) {
                    numScvsPerGas = 1;
                }
                break;
            case 1:
                if (gasBankRatio() < 0.6) {
                    numScvsPerGas = 2;
                }
                else if (minerals < 500 && mineralRate == 0) {
                    numScvsPerGas = 0;
                }
                break;
            case 2:
                //if late game with bank, or if >3:1 mins:gas, then max gas income
                if (minerals > 3100 || (minerals > 300 && !Purchase.isStructureQueued(Units.TERRAN_COMMAND_CENTER) && gasBankRatio() < 0.3)) {
                    numScvsPerGas = 3;
                }
                //go to 1 in gas
                else if (gas > 700 && gasBankRatio() > 0.75) {
                    numScvsPerGas = 1;
                }
                break;
            case 3:
                if (minerals > 3100) {
                    break;
                }
                if (gas > minerals + 3000 || (
                        minerals < 2750 &&
                        gas > 100 * (GameCache.starportList.size() + GameCache.factoryList.size()) &&
                        gasBankRatio() > 0.5 &&
                        StructureScv.numInProductionOfType(Units.TERRAN_COMMAND_CENTER) < 2
                )) {
                    numScvsPerGas = 2;
                }
                break;
        }
    }

    private static float gasBankRatio() {
        return Math.max(GameCache.gasBank, 1f) / (Math.max(GameCache.gasBank, 1f) + Math.max(GameCache.mineralBank, 1f));
    }

}
